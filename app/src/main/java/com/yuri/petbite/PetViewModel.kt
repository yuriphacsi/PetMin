package com.yuri.petbite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.yuri.petbite.data.*
import com.yuri.petbite.device.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PetDatabase.get(application).dao()
    private val store = SettingsStore(application)
    private val api = FeederApi()
    private val _settings = MutableStateFlow(store.read())
    val settings = _settings.asStateFlow()
    val schedules = dao.schedules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = dao.events().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _device = MutableStateFlow<DeviceStatus?>(null)
    val device = _device.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var ready = false
    private var refreshing = false
    private var stopping = false

    init {
        viewModelScope.launch {
            dao.recoverInterrupted()
            cleanupHistory()
            ready = true
        }
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (settings.value.token.length >= 8 && !busy.value && !refreshing &&
                    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) refreshInternal(false)
            }
        }
    }
    // PETMIN FUTURO: migrar la limpieza a WorkManager si se requiere aun sin abrir la app.
    private suspend fun cleanupHistory() {
        val days = settings.value.retentionDays
        dao.deleteEventsBefore(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L)
    }
    fun setRetentionDays(days: Int) {
        if (days !in listOf(7, 30)) return
        persist(settings.value.copy(retentionDays = days))
        viewModelScope.launch { cleanupHistory(); refreshInternal(false); message("Retención local: $days días. El ESP32 se actualizará al conectar.") }
    }
    private fun persist(value: PetSettings) { store.save(value); _settings.value = value }
    fun message(text: String) { viewModelScope.launch { messageChannel.send(text) } }
    fun saveSettings(name: String, address: String, token: String) {
        if (busy.value) return
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 40) { message("Escribe un nombre de 1 a 40 caracteres."); return }
        val cleanAddress = try { Validation.address(address) } catch (e: IllegalArgumentException) {
            message(e.message ?: "Dirección inválida."); return
        }
        if ('\n' in token || '\r' in token) { message("La clave no debe contener saltos de línea."); return }
        if (token.trim().length < 8) { message("Escribe la clave API_TOKEN del ESP32 (mínimo 8 caracteres)."); return }
        val old = settings.value
        val connectionChanged = old.address != cleanAddress || old.token != token.trim()
        persist(old.copy(petName = cleanName, address = cleanAddress, token = token.trim(),
            syncedSignature = if (connectionChanged) "" else old.syncedSignature))
        if (connectionChanged) _device.value = null
        refresh()
        message("Configuración guardada. Prueba la conexión con el ESP32.")
    }
    fun refresh() { viewModelScope.launch { refreshInternal(true) } }
    private suspend fun refreshInternal(notify: Boolean) {
        if (refreshing) return
        refreshing = true
        val config = settings.value
        try {
            val current = api.status(config)
            if (current.retentionDays != config.retentionDays && !current.busy) api.retention(config)
            val imported = api.events(config)
            if (settings.value == config) {
                _device.value = current
                // No reimportar registros vencidos desde el historial limitado del ESP32.
                val cutoff = System.currentTimeMillis() - settings.value.retentionDays * 24L * 60L * 60L * 1000L
                dao.importEvents(imported.filter { it.timestamp >= cutoff })
                cleanupHistory()
                if (notify) message(if (current.ready) "ESP32 conectado e historial actualizado." else "ESP32 conectado: ${current.message}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (settings.value == config) _device.value = null
            if (notify) message("No se pudo actualizar: ${e.message.orEmpty()}")
        } finally { refreshing = false }
    }
    fun stop() {
        if (stopping) return
        stopping = true
        val config = settings.value
        viewModelScope.launch {
            try { api.stop(config); message("ESP32 confirmó la detención.") }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                message("No se confirmó la detención. Revisa físicamente el dispensador.")
            } finally { stopping = false; refreshInternal(false) }
        }
    }
    fun feed(grams: Int, source: String = "Manual") {
        if (busy.value || !ready) return
        if (!Validation.portion(grams)) { message("La porción debe estar entre 5 y 300 g."); return }
        val config = settings.value
        if (config.token.length < 8) { message("Primero configura la dirección y clave del ESP32 en Ajustes."); return }

        _busy.value = true
        val event = FeedEvent(UUID.randomUUID().toString(), System.currentTimeMillis(), grams, null, false, source, "En proceso")
        viewModelScope.launch {
            try {
                run {
                    val current = api.status(config)
                    _device.value = current
                    if (current.simulation) throw DeviceRejected("El ESP32 está en simulación. Carga el firmware PetMin v3 incluido.")
                    if (!current.ready) throw DeviceRejected(current.message.ifBlank { "El dispensador no está listo." })
                }
                dao.saveEvent(event.copy(demo = _device.value?.simulation == true))
                val delivered = api.dispense(config, event.id, grams, requireNotNull(_device.value).nextSequence)
                dao.saveEvent(event.copy(deliveredGrams = delivered, status = "Completado", demo = _device.value?.simulation == true))
                message("Dispensación confirmada: $delivered g.")
                refreshInternal(false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val rejected = e is DeviceRejected
                val detail = if (rejected) "Orden rechazada: ${e.message}" else
                    "No se confirmó la entrega. Actualiza el historial y revisa el recipiente antes de repetir. ${e.message.orEmpty()}"
                dao.saveEvent(event.copy(status = if (rejected) "Rechazado" else "Sin confirmar", detail = detail,
                    demo = _device.value?.simulation == true))
                refreshInternal(false)
                _device.value = null
                message(detail)
            } finally { _busy.value = false }
        }
    }
    fun saveSchedule(id: String?, hour: Int, minute: Int, grams: Int) {
        if (busy.value) return
        if (!Validation.time(hour, minute) || !Validation.portion(grams)) { message("Revisa la hora y la porción (5–300 g)."); return }
        if (schedules.value.any { it.id != id && it.hour == hour && it.minute == minute }) {
            message("Ya tienes un horario a esa hora."); return
        }
        if (id == null && schedules.value.size >= 12) { message("Puedes guardar hasta 12 horarios."); return }
        val previous = schedules.value.find { it.id == id }
        viewModelScope.launch {
            dao.saveSchedule(MealSchedule(id ?: UUID.randomUUID().toString(), hour, minute, grams,
                previous?.enabled ?: true, ""))
            persist(settings.value.copy(syncedSignature = ""))
            message("Horario guardado.")
        }
    }
    fun toggleSchedule(value: MealSchedule, enabled: Boolean) {
        if (busy.value) return
        viewModelScope.launch { dao.saveSchedule(value.copy(enabled = enabled)); persist(settings.value.copy(syncedSignature = "")) }
    }
    fun deleteSchedule(value: MealSchedule) {
        if (busy.value) return
        viewModelScope.launch { dao.deleteSchedule(value); persist(settings.value.copy(syncedSignature = "")); message("Horario eliminado.") }
    }
    fun syncSchedules() {
        if (busy.value) return
        _busy.value = true
        val snapshot = schedules.value.toList()
        val config = settings.value
        viewModelScope.launch {
            try {
                api.schedules(config, snapshot)
                persist(settings.value.copy(syncedSignature = signature(snapshot)))
                message("Horarios confirmados por el ESP32.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                persist(settings.value.copy(syncedSignature = ""))
                message("No se confirmó la sincronización. ${e.message.orEmpty()}")
            } finally { _busy.value = false }
        }
    }
    fun signature(values: List<MealSchedule>): String = "America/Lima|" + values.joinToString(";") {
        "${it.id}:${it.hour}:${it.minute}:${it.grams}:${it.enabled}"
    }
}
