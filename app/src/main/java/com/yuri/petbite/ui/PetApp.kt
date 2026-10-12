@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yuri.petbite.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuri.petbite.PetViewModel
import com.yuri.petbite.data.*
import com.yuri.petbite.device.DeviceStatus
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(Tab("Inicio", Icons.Outlined.Home), Tab("Porción", Icons.Outlined.Pets),
    Tab("Horarios", Icons.Outlined.Schedule), Tab("Gaming", Icons.Outlined.SportsEsports), Tab("Historial", Icons.Outlined.History), Tab("Ajustes", Icons.Outlined.Settings))
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private fun timeLabel(hour: Int, minute: Int) = LocalTime.of(hour, minute).format(timeFormat)
private fun weight(value: Double) = if (value == value.toInt().toDouble()) "${value.toInt()}" else String.format(Locale.getDefault(), "%.1f", value)
private fun eventTime(event: FeedEvent) = Instant.ofEpochMilli(event.timestamp).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))

@Composable fun PetApp(vm: PetViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    Scaffold(
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Pets, "Logotipo de PetMin", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp)); Text("PetMin", fontWeight = FontWeight.Bold)
        } }, actions = {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(end = 16.dp)) {
                Text("ESP32", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }) },
        bottomBar = { NavigationBar { tabs.forEachIndexed { index, item ->
            NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.icon, null) },
                label = { Text(item.label, maxLines = 1, fontSize = 10.sp) })
        } } },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(settings, schedules, events, device, busy, { tab = 1 }, { tab = 2 }, vm::refresh, vm::stop)
                1 -> PortionScreen(settings, device, busy, vm::feed, vm::stop)
                2 -> ScheduleScreen(vm, settings, schedules, busy)
                3 -> GamingScreen()
                4 -> HistoryScreen(events)
                5 -> SettingsScreen(settings, busy, vm::saveSettings, vm::refresh, vm::setRetentionDays)
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Info(text: String, warning: Boolean = false) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (warning) Icons.Outlined.Info else Icons.Outlined.CheckCircle, null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
@Composable private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun HomeScreen(config: PetSettings, schedules: List<MealSchedule>, events: List<FeedEvent>, device: DeviceStatus?, busy: Boolean,
    feed: () -> Unit, schedule: () -> Unit, refresh: () -> Unit, stop: () -> Unit) {
    val today = LocalDate.now()
    val todayEvents = events.filter { it.demo == false && it.status == "Completado" &&
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == today }
    val level = device?.levelPercent
    val last = events.firstOrNull { it.demo == false && it.status == "Completado" }
    val now = LocalTime.now(ZoneId.of("America/Lima"))
    val next = schedules.filter { it.enabled }.minByOrNull {
        val minute = it.hour * 60 + it.minute - (now.hour * 60 + now.minute)
        if (minute > 0) minute else minute + 1440
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Hola, ${config.petName}", "Un cuidado, todos los días.") }
        item { Info(when {
            device == null -> "Sin conexión. Configura la dirección y clave del ESP32 en Ajustes. Se vuelve a intentar automáticamente."
            device.simulation -> "ESP32 conectado en SIMULACIÓN: no mueve el motor."
            device.busy -> "ESP32 conectado · Sirviendo alimento."
            device.ready -> "ESP32 conectado · Listo para servir."
            else -> "ESP32 conectado · ${device.message}"
        }, device?.ready != true || device.simulation) }
        if (busy || device?.busy == true) item { Button(onClick = stop, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Detener dispensador") } }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("DEPÓSITO · ${level?.let { "$it%" } ?: "sin lectura"}", style = MaterialTheme.typography.labelMedium)
                            Text(device?.remainingGrams?.let { "${weight(it)} g" } ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Outlined.Pets, "Depósito de alimento", modifier = Modifier.size(62.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { (level ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = level?.let { "Depósito al $it por ciento" } ?: "Nivel del depósito no disponible" },
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .6f))
                    Text(when {
                        device == null -> "Sin lectura · comprueba la conexión"
                        else -> "Última lectura: " + Instant.ofEpochMilli(device.checkedAt).atZone(ZoneId.systemDefault()).format(timeFormat)
                    }, style = MaterialTheme.typography.bodySmall)
                    if (level != null && level < 20) Text("Queda poco alimento. Revisa el depósito.", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Servido hoy${" · ESP32"}", "${weight(todayEvents.sumOf { it.deliveredGrams ?: 0.0 })} g", Modifier.weight(1f))
            Stat("Próximo horario", next?.let { timeLabel(it.hour, it.minute) } ?: "Sin horarios", Modifier.weight(1f))
        } }
        item { Button(onClick = feed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Outlined.Pets, null); Spacer(Modifier.width(10.dp)); Text("Dispensar ahora")
        } }
        item { OutlinedButton(onClick = refresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualizar estado")
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Última alimentación", fontWeight = FontWeight.Bold)
            TextButton(onClick = schedule) { Text("Ver horarios") }
        } }
        item { if (last == null) Info("Todavía no registraste una alimentación. Prueba tu primera porción.")
            else EventCard(last) }
        item { Info("Estado e historial se actualizan cada 5 segundos con la app visible. Los horarios enviados al ESP32 se ejecutan aunque cierres la app.", warning = true) }
    }
}

@Composable private fun PortionScreen(config: PetSettings, device: DeviceStatus?, busy: Boolean, feed: (Int, String) -> Unit, stop: () -> Unit) {
    var grams by rememberSaveable { mutableFloatStateOf(40f) }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { PageTitle("Una porción de cuidado", "Elige cuánto alimento deseas servir.") }
        item { Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Outlined.Pets, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${grams.roundToInt()} g", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text("Porción solicitada")
                Slider(value = grams, onValueChange = { grams = (it / 5).roundToInt() * 5f }, valueRange = 5f..300f, steps = 58, enabled = !busy)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("5 g"); Text("300 g") }
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 40, 60, 100).forEach { amount -> FilterChip(selected = grams.roundToInt() == amount,
                onClick = { grams = amount.toFloat() }, enabled = !busy, label = { Text("$amount g") }, modifier = Modifier.weight(1f)) }
        } }
        item { Button(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (busy) "Procesando…" else "Dispensar ${grams.roundToInt()} g")
        } }
        if (busy || device?.busy == true) item { Button(onClick = stop, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Detener dispensador") } }
        item { Info("Ajusta la porción según la alimentación de tu mascota. Los valores de los botones son ejemplos, no una recomendación de dieta.", true) }
        item { Info("La app comprueba el ESP32 antes de enviar la orden. El peso servido se registra cuando el dispositivo confirma la entrega.") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("¿Servir alimento?") },
        text = { Text("Se solicitarán ${grams.roundToInt()} g al dispensador. Comprueba que el recipiente esté colocado.") },
        confirmButton = { TextButton(onClick = { confirm = false; feed(grams.roundToInt(), "Manual") }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

@Composable private fun ScheduleScreen(vm: PetViewModel, config: PetSettings, schedules: List<MealSchedule>, busy: Boolean) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MealSchedule?>(null) }
    var deleting by remember { mutableStateOf<MealSchedule?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("A su hora", "Hasta 12 comidas diarias · Hora de Perú (Lima).") }
        item { Info(if (config.syncedSignature == vm.signature(schedules)) "Horarios enviados al ESP32. Se ejecutan sin tener abierta la app, con el reloj del dispositivo sincronizado."
            else "Cambios locales pendientes. Envía los horarios al ESP32; hasta entonces el dispositivo conserva su programación anterior.", true) }
        item { Button(onClick = { editing = null; showEditor = true }, enabled = !busy && schedules.size < 12, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Agregar horario")
        } }
        item { OutlinedButton(onClick = vm::syncSchedules, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Enviar horarios al ESP32") } }
        if (schedules.isEmpty()) item { EmptyState(Icons.Outlined.Schedule, "Su rutina comienza aquí", "Agrega la hora y la porción de su primera comida.") }
        items(schedules, key = { it.id }) { value ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(timeLabel(value.hour, value.minute), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${value.grams} g · Todos los días", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = value.enabled, onCheckedChange = { vm.toggleSchedule(value, it) }, enabled = !busy)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editing = value; showEditor = true }, enabled = !busy) { Text("Editar") }
                        IconButton(onClick = { deleting = value }, enabled = !busy) { Icon(Icons.Outlined.Delete, "Eliminar horario") }
                    }
                }
            }
        }
    }
    if (showEditor) ScheduleEditor(editing, { showEditor = false }) { hour, minute, grams ->
        // Validaciones visibles mantienen abierto el formulario cuando existe un error.
        if (schedules.any { it.id != editing?.id && it.hour == hour && it.minute == minute }) vm.message("Ya existe un horario a esa hora.")
        else { vm.saveSchedule(editing?.id, hour, minute, grams); showEditor = false }
    }
    deleting?.let { value -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("¿Eliminar este horario?") },
        text = { Text("Se quitará la comida de las ${timeLabel(value.hour, value.minute)}. " + "Después envía los cambios al ESP32.") },
        confirmButton = { TextButton(onClick = { vm.deleteSchedule(value); deleting = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}

@Composable private fun ScheduleEditor(value: MealSchedule?, dismiss: () -> Unit, save: (Int, Int, Int) -> Unit) {
    var hour by rememberSaveable { mutableIntStateOf(value?.hour ?: 8) }
    var minute by rememberSaveable { mutableIntStateOf(value?.minute ?: 0) }
    var grams by rememberSaveable { mutableStateOf((value?.grams ?: 40).toString()) }
    val context = LocalContext.current
    val valid = grams.toIntOrNull()?.let { it in 5..300 } == true
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (value == null) "Nuevo horario" else "Editar horario") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Todos los días")
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() }) {
                Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(10.dp)); Text(timeLabel(hour, minute), fontSize = 24.sp)
            }
            OutlinedTextField(value = grams, onValueChange = { grams = it.filter(Char::isDigit).take(3) }, label = { Text("Porción en gramos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = !valid,
                supportingText = { Text(if (valid) "De 5 a 300 g" else "Ingresa un valor válido entre 5 y 300 g") })
        }
    }, confirmButton = { TextButton(onClick = { save(hour, minute, grams.toInt()) }, enabled = valid) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable private fun EventCard(value: FeedEvent) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (value.status == "Completado") Icons.Outlined.CheckCircle else Icons.Outlined.Info, null,
                tint = if (value.status == "Completado") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${value.deliveredGrams?.let { weight(it) } ?: "—"} g · ${value.status}", fontWeight = FontWeight.SemiBold)
                Text("${eventTime(value)} · ${value.source}", style = MaterialTheme.typography.bodySmall)
                Text("${"ESP32"} · Solicitado: ${value.requestedGrams} g", style = MaterialTheme.typography.labelSmall)
                if (value.detail.isNotBlank()) Text(value.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
// PETMIN FUTURO: filtros por mascota y exportación de historial sin modificar eventos originales.
@Composable private fun HistoryScreen(events: List<FeedEvent>) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Historial de alimentación", "Registros reales del dispensador.") }
        val realEvents = events.filter { !it.demo }
        if (realEvents.isEmpty()) item { EmptyState(Icons.Outlined.History, "Todavía sin registros", "Las dispensaciones confirmadas aparecerán aquí.") }
        items(realEvents, key = { it.id }) { EventCard(it) }
    }
}

// PETMIN FUTURO: añadir asistente de conexión Wi-Fi y calibración guiada del HX711.
@Composable private fun SettingsScreen(config: PetSettings, busy: Boolean,
    save: (String, String, String) -> Unit, connect: () -> Unit, retention: (Int) -> Unit) {
    var name by rememberSaveable(config.petName) { mutableStateOf(config.petName) }
    var address by rememberSaveable(config.address) { mutableStateOf(config.address) }
    var token by rememberSaveable(config.token) { mutableStateOf(config.token) }
    val dirty = name != config.petName || address != config.address || token != config.token
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Configuración PetMin", "Conecta tu dispensador real.") }
        item { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Nombre para el saludo") },
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Dirección del ESP32") },
            placeholder = { Text("http://192.168.1.100") }, supportingText = { Text("El teléfono y ESP32 deben compartir la red Wi-Fi.") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), enabled = !busy, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Clave del ESP32 (API_TOKEN)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { save(name, address, token) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Guardar configuración") } }
        item { OutlinedButton(onClick = connect, enabled = !busy && !dirty, modifier = Modifier.fillMaxWidth()) { Text("Probar conexión") } }
        if (dirty) item { Info("Guarda los cambios antes de probar la conexión.", true) }
        item { Info("Configura firmware/PetBiteESP32/Secrets.h con Wi-Fi y clave; Config.h con calibración real. No existe modo demo.", true) }
        item { Text("Conservar historial", fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = config.retentionDays == 7, onClick = { retention(7) }, label = { Text("7 días") })
            FilterChip(selected = config.retentionDays == 30, onClick = { retention(30) }, label = { Text("30 días") })
        } }
        item { ProfilePanel() }
        item { CalibrationGuide() }
        item { Text("PetMin 3.0 · Android + ESP32", style = MaterialTheme.typography.bodySmall) }
    }
}
