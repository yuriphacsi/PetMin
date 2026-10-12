package com.yuri.petbite.device

import com.yuri.petbite.data.FeedEvent
import com.yuri.petbite.data.MealSchedule
import com.yuri.petbite.data.PetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// API v3: el firmware compatible está incluido en firmware/PetBiteESP32.
data class DeviceStatus(
    val levelPercent: Int?, val lastGrams: Double?, val checkedAt: Long,
    val ready: Boolean, val busy: Boolean, val clockSynced: Boolean,
    val deviceId: String, val message: String, val simulation: Boolean,
    val remainingGrams: Double? = null, val nextSequence: Long = 1, val retentionDays: Int = 30
)
class DeviceRejected(message: String) : Exception(message)
class FeederApi(private val pollDelayMs: Long = 700, private val maxPolls: Int = 60) {
    private suspend fun request(config: PetSettings, path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(Validation.address(config.address) + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 7000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (config.token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${config.token}")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = json.optString("error", "El dispensador respondió HTTP $code.")
                // Solo un rechazo explícito del contrato es seguro de clasificar como no ejecutado.
                if (json.optBoolean("rejected", false)) throw DeviceRejected(message)
                error(message)
            }
            json
        } finally { connection.disconnect() }
    }
    suspend fun status(config: PetSettings): DeviceStatus {
        val json = request(config, "/api/status")
        check(json.getInt("apiVersion") == 3 && json.getBoolean("online")) { "Carga el firmware PetMin v3 incluido en el proyecto." }
        val level = if (json.isNull("foodLevelPercent")) null else json.getInt("foodLevelPercent")
        check(level == null || level in 0..100) { "Nivel de alimento inválido." }
        val last = if (json.isNull("lastDispensedGrams")) null else json.getDouble("lastDispensedGrams")
        check(last == null || (last.isFinite() && last >= 0)) { "Lectura de peso inválida." }
        return DeviceStatus(level, last, System.currentTimeMillis(), json.getBoolean("ready"), json.getBoolean("busy"),
            json.getBoolean("clockSynced"), json.getString("deviceId"), json.optString("message"), json.optBoolean("simulation", false),
            if (json.isNull("remainingGrams")) null else json.getDouble("remainingGrams").also { require(it.isFinite() && it >= 0) },
            json.getLong("nextSequence").also { require(it > 0) }, json.getInt("retentionDays"))
    }
    suspend fun dispense(config: PetSettings, id: String, grams: Int, sequence: Long = 1): Double {
        require(Validation.portion(grams))
        // Se envía POST una sola vez. Las consultas posteriores nunca abren la compuerta.
        var response = request(config, "/api/dispense", "POST", JSONObject().put("requestId", id).put("grams", grams).put("sequence", sequence))
        repeat(maxPolls) {
            check(response.getString("requestId") == id) { "La respuesta no corresponde a la orden enviada." }
            when (response.getString("status")) {
                "completed" -> {
                    val delivered = response.getDouble("dispensedGrams")
                    check(delivered.isFinite() && delivered >= 0) { "Peso inválido." }
                    return delivered
                }
                "failed", "interrupted" -> error(response.optString("detail", "La entrega no se completó; revisa el dispensador."))
                "accepted", "dispensing" -> Unit
                else -> error("Estado de entrega desconocido.")
            }
            delay(pollDelayMs)
            response = request(config, "/api/jobs/$id")
        }
        error("La orden sigue sin confirmación. Actualiza el historial antes de repetir.")
    }
    suspend fun events(config: PetSettings): List<FeedEvent> {
        val all = mutableListOf<FeedEvent>()
        var offset = 0
        do {
        val response = request(config, if (offset == 0) "/api/events" else "/api/events?offset=$offset")
        val array = response.getJSONArray("events")
        require(array.length() <= 64) { "Respuesta de historial demasiado grande." }
        all += (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val grams = item.getInt("grams")
            val timestamp = item.getLong("timestamp")
            require(Validation.portion(grams) && timestamp > 0)
            val delivered = if (item.isNull("dispensedGrams")) null else item.getDouble("dispensedGrams")
            require(delivered == null || (delivered.isFinite() && delivered >= 0))
            val status = when (item.getString("status")) {
                "completed" -> "Completado"
                "accepted", "dispensing" -> "En proceso"
                "failed" -> "Error"
                "interrupted" -> "Sin confirmar"
                else -> error("Estado de historial desconocido.")
            }
            FeedEvent(item.getString("requestId"), timestamp, grams, delivered, item.optBoolean("simulation", false),
                if (item.getString("source") == "schedule") "Horario ESP32" else "Manual", status, item.optString("detail"))
        }
        if (response.isNull("nextOffset")) break
        val next = response.getInt("nextOffset")
        require(next > offset && next <= 512) { "Paginacion invalida." }
        offset = next
        } while (true)
        return all.distinctBy { it.id }
    }
    suspend fun retention(config: PetSettings) {
        check(request(config, "/api/preferences", "PUT", JSONObject().put("retentionDays", config.retentionDays)).getBoolean("saved"))
    }
    suspend fun schedules(config: PetSettings, values: List<MealSchedule>) {
        val array = JSONArray()
        values.forEach { array.put(JSONObject().put("id", it.id).put("hour", it.hour).put("minute", it.minute)
            .put("grams", it.grams).put("enabled", it.enabled)) }
        // Los horarios del dispensador usan hora de Perú, independientemente de la zona del teléfono.
        val response = request(config, "/api/schedules", "PUT", JSONObject()
            .put("timezone", "America/Lima").put("schedules", array))
        check(response.getBoolean("saved") && response.getInt("count") == values.size) { "El ESP32 no confirmó los horarios." }
    }
    suspend fun stop(config: PetSettings) {
        check(request(config, "/api/stop", "POST", JSONObject()).getBoolean("stopped")) { "El dispositivo no confirmó la detención." }
    }
}
