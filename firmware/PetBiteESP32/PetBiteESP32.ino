#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <HX711.h>
#include <LittleFS.h>
#include <time.h>
#include <atomic>
#include <esp_arduino_version.h>
#include "Config.h"
#include "Rules.h"

WebServer server(80);
Preferences prefs;
HX711 scale;
DynamicJsonDocument history(24576);
DynamicJsonDocument timetable(12288);
constexpr int MAX_EVENTS = 16;
const char* headerKeys[] = {"Authorization"};
String deviceId;
bool storageReady = false, servoReady = false, clockValid = false;
float hopperGrams = NAN, lastGain = NAN;
long rawScale = 0;
int levelPercent = -1;
unsigned long lastScaleMs = 0, lastLevelMs = 0, lastWiFiMs = 0, lastPrintMs = 0;
String activeId;
uint64_t lastSequence = 0;
unsigned long lastFinishedMs = 0;
unsigned long stableSince = 0, lastCleanupMs = 0;
float previousSample = NAN;
bool sensorJump = false;
int retentionDays = 30;
uint64_t lastScheduleMinute = 0;
constexpr int MAX_ARCHIVE_EVENTS = 512;

float baseline = 0, progressWeight = 0;
unsigned long startedMs = 0, lastProgressMs = 0, settlingMs = 0;
int targetGrams = 0;
bool gateOpen = false;
String outcome;
std::atomic<bool> watchdogArmed(false), emergencyClosed(false);
std::atomic<unsigned long> watchdogStarted(0), scaleHeartbeat(0);

bool active() { return activeId.length() > 0; }
long long epochMs() { return static_cast<long long>(time(nullptr)) * 1000LL; }
JsonArray eventsArray() { return history.as<JsonArray>(); }
JsonArray scheduleArray() { return timetable.as<JsonArray>(); }

// PETMIN NOTA: archivo por orden, reemplazo temporal; NVS conserva además las últimas 16.
// LittleFS es persistente y limita uso de RAM a una página de 32 registros.
bool archiveEvent(JsonObject event) {
    String path = "/events/" + event["requestId"].as<String>() + ".json";
    File file = LittleFS.open("/event.tmp", "w");
    if (!file) return false;
    size_t expected = measureJson(event);
    size_t written = serializeJson(event, file); file.flush(); file.close();
    return written == expected && LittleFS.rename("/event.tmp", path);
}
int archiveCount() {
    File root = LittleFS.open("/events"); int count = 0;
    for (File f = root.openNextFile(); f; f = root.openNextFile()) { count++; f.close(); }
    return count;
}
void pruneArchive() {
    if (!clockValid || active() || !storageReady) return;
    File root = LittleFS.open("/events");
    long long cutoff = epochMs() - retentionDays * 86400000LL;
    for (File f = root.openNextFile(); f; f = root.openNextFile()) {
        String path = f.path(); StaticJsonDocument<1024> item;
        bool parsed = !deserializeJson(item, f); f.close();
        if (parsed && item["timestamp"].as<long long>() < cutoff) LittleFS.remove(path);
    }
}
bool persistHistory() {
    String value; serializeJson(history, value);
    return storageReady && prefs.putBytes("events", value.c_str(), value.length()) == value.length();
}
String readHistory() {
    if (!prefs.isKey("events")) return "[]";
    size_t size = prefs.getBytesLength("events");
    if (size < 2 || size > 12288) { storageReady = false; return "[]"; }
    char* buffer = static_cast<char*>(malloc(size + 1));
    if (!buffer) { storageReady = false; return "[]"; }
    size_t read = prefs.getBytes("events", buffer, size);
    buffer[read] = '\0'; String result(buffer); free(buffer);
    if (read != size) { storageReady = false; return "[]"; }
    return result;
}
bool persistSchedules() {
    String value; serializeJson(timetable, value);
    return storageReady && prefs.putString("schedules", value) == value.length();
}
JsonObject findEvent(const String& id) {
    for (JsonObject item : eventsArray()) if (item["requestId"].as<String>() == id) return item;
    return JsonObject();
}
void writeAngle(int degrees) {
    if (!servoReady) return;
    const uint32_t micros = SERVO_MIN_US + constrain(degrees, 0, 180) * (SERVO_MAX_US - SERVO_MIN_US) / 180;
    const uint32_t duty = micros * 65535UL / 20000UL;
#if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcWrite(SERVO_PIN, duty);
#else
    ledcWrite(0, duty);
#endif
}
void closeGate() { watchdogArmed.store(false); writeAngle(SERVO_CLOSED_DEGREES); gateOpen = false; }
void motorWatchdog(void*) {
    for (;;) {
        if (watchdogArmed.load() && (millis() - watchdogStarted.load() >= MAX_OPEN_MS || millis() - scaleHeartbeat.load() > 1500)) {
            writeAngle(SERVO_CLOSED_DEGREES);
            emergencyClosed.store(true); watchdogArmed.store(false);
        }
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}
bool scaleHealthy() { return (HX711_CALIBRATED && isfinite(hopperGrams) && millis() - lastScaleMs < 1200); }
String notReady() {
    if (!storageReady) return "No se pudo abrir la memoria persistente del ESP32.";
    if (!clockValid) return "Esperando hora por Internet (NTP); revisa el Wi-Fi.";
    if (active()) return "Dispensador ocupado.";
    if (lastFinishedMs && millis() - lastFinishedMs < 30000) return "Espera 30 segundos entre porciones.";
    if ((!ACTUATOR_CONFIGURED || !servoReady)) return "Configura los pines y angulos del servo en Config.h.";
    if ((!HX711_CALIBRATED || !isfinite(HX711_COUNTS_PER_GRAM) || fabs(HX711_COUNTS_PER_GRAM) < 0.001f)) return "Falta calibrar HX711 en Config.h.";
    if (!scaleHealthy()) return "Sin lectura de la balanza. Revisa HX711.";
    if (!stableSince || millis() - stableSince < 1500) return "Esperando peso estable de la tolva.";
    if (!petbite::weightInRange(hopperGrams, CAPACITY_GRAMS)) return "Peso de la tolva fuera de rango; revisa la calibracion.";
    return "";
}
void respond(int code, JsonVariantConst value) {
    String payload; serializeJson(value, payload);
    server.sendHeader("Cache-Control", "no-store");
    server.send(code, "application/json; charset=utf-8", payload);
}
void reject(int code, const String& error) {
    StaticJsonDocument<384> json; json["rejected"] = true; json["error"] = error; respond(code, json.as<JsonVariantConst>());
}
bool authorize() {
    if (strlen(API_TOKEN) < 8 || String(API_TOKEN) == "CAMBIA_ESTA_CLAVE") { reject(503, "Configura una clave propia de al menos 8 caracteres en Config.h."); return false; }
    if (server.header("Authorization") != "Bearer " + String(API_TOKEN)) { reject(401, "Clave del dispositivo incorrecta."); return false; }
    return true;
}
bool parseBody(JsonDocument& json) {
    if (server.arg("plain").length() > 6000 || deserializeJson(json, server.arg("plain"))) { reject(400, "JSON invalido o demasiado grande."); return false; }
    return true;
}
bool validId(const String& id) {
    if (id.length() < 8 || id.length() > 100) return false;
    for (size_t i = 0; i < id.length(); ++i) if (!isalnum(id[i]) && id[i] != '-') return false;
    return true;
}
bool beginMeal(const String& id, int grams, const String& source) {
    if (!notReady().isEmpty() || !petbite::validPortion(grams)) return false;
    if (hopperGrams < grams + STOP_MARGIN_GRAMS) return false;
    if (archiveCount() >= MAX_ARCHIVE_EVENTS) return false;
    if (eventsArray().size() >= MAX_EVENTS) { eventsArray().remove(0); history.garbageCollect(); }
    JsonObject event = eventsArray().createNestedObject();
    event["requestId"] = id; event["grams"] = grams; event["timestamp"] = epochMs();
    event["source"] = source; event["status"] = "accepted"; event["dispensedGrams"] = nullptr;
    event["detail"] = ""; event["simulation"] = false;
    // Escribir ANTES de mover: un reinicio no debe repetir una orden pendiente.
    if (!archiveEvent(event) || !persistHistory()) { storageReady = false; event["status"] = "failed"; event["detail"] = "No se pudo guardar la orden."; return false; }
    if (!scaleHealthy()) { event["status"] = "failed"; event["detail"] = "Lectura vencida antes de abrir."; if (!archiveEvent(event) || !persistHistory()) storageReady = false; return false; }
    activeId = id; targetGrams = grams; baseline = hopperGrams;
    sensorJump = false; progressWeight = 0; startedMs = lastProgressMs = millis(); settlingMs = 0; outcome = "";
    event["status"] = "dispensing";
    emergencyClosed.store(false); watchdogStarted.store(millis());
    watchdogArmed.store(true);
    writeAngle(SERVO_OPEN_DEGREES); gateOpen = true;
    return true;
}
void finishMeal(const String& state, const String& detail, float gain) {
    closeGate();
    JsonObject event = findEvent(activeId);
    event["status"] = state; event["detail"] = detail;
    if (isfinite(gain) && gain >= 0) event["dispensedGrams"] = gain; else event["dispensedGrams"] = nullptr;
    if (state == "completed") lastGain = gain;
    if (!archiveEvent(event) || !persistHistory()) {
        storageReady = false;
        // La orden aceptada persistida se recuperara como interrumpida al reiniciar.
        event["status"] = "interrupted"; event["detail"] = "Error de memoria al guardar el resultado. Revisa el recipiente.";
    }
    activeId = ""; settlingMs = 0; lastFinishedMs = millis();
}
void serviceMeal() {
    if (!active()) return;
    if (emergencyClosed.exchange(false)) { finishMeal("failed", "Cierre de proteccion por tiempo o falta de lectura.", NAN); return; }
    if (!scaleHealthy()) { finishMeal("failed", "Se perdio la lectura de la balanza; compuerta cerrada.", NAN); return; }
    if (sensorJump) { finishMeal("failed", "Cambio brusco de peso; revisar fijacion de tolva.", NAN); return; }
    float gain = petbite::hopperLoss(baseline, hopperGrams);
    if (!gateOpen && settlingMs && millis() - settlingMs > 5000) { finishMeal("failed", "Peso final inestable.", NAN); return; }
    if (gain < -5) { finishMeal("failed", "Se recargo o movio la tolva; compuerta cerrada.", NAN); return; }
    if (gateOpen) {
        if (gain > progressWeight + 0.5f) { progressWeight = gain; lastProgressMs = millis(); }
        if (millis() - lastProgressMs >= NO_FLOW_TIMEOUT_MS) { finishMeal("failed", "No se detecta flujo de alimento. Revisa deposito y compuerta.", max(0.0f, gain)); return; }
        if (petbite::mustClose(gain, targetGrams, STOP_MARGIN_GRAMS, millis() - startedMs, MAX_OPEN_MS)) {
            outcome = gain >= targetGrams - STOP_MARGIN_GRAMS ? "completed" : "failed";
            closeGate(); settlingMs = millis();
        }
    } else if (settlingMs && millis() - settlingMs >= 700 && stableSince && millis() - stableSince >= 500) {
        if (!petbite::withinTolerance(gain, targetGrams, MAX_TOLERANCE_GRAMS)) outcome = "failed";
        finishMeal(outcome, outcome == "failed" ? "Porcion fuera de tolerancia o tiempo limite; revisar mecanismo." : "", max(0.0f, gain));
    }
}
void sampleSensors() {
    if (scale.is_ready()) {
        rawScale = scale.read();
        float value = (static_cast<float>(rawScale) - HX711_ZERO) / HX711_COUNTS_PER_GRAM;
        if (isfinite(value) && rawScale != 8388607 && rawScale != -8388608) {
            if (isfinite(previousSample) && fabs(value - previousSample) > 80.0f && active()) sensorJump = true;
            if (!isfinite(previousSample) || fabs(value - previousSample) > 1.0f) stableSince = 0;
            else if (!stableSince) stableSince = millis();
            previousSample = value;
            // PETMIN NOTA: filtro corto, sin promedios bloqueantes que retrasen el cierre.
            hopperGrams = isfinite(hopperGrams) ? hopperGrams * 0.5f + value * 0.5f : value;
            lastScaleMs = millis(); scaleHeartbeat.store(lastScaleMs);
        }
    }
    levelPercent = scaleHealthy() ? constrain(static_cast<int>(roundf(100 * hopperGrams / CAPACITY_GRAMS)), 0, 100) : -1;
    digitalWrite(BUZZER_PIN, levelPercent >= 0 && levelPercent < 20 && millis() % 30000 < 200 ? HIGH : LOW);
    if (!active() && millis() - lastPrintMs >= 2000) {
        lastPrintMs = millis();
        if (lastScaleMs == 0 || millis() - lastScaleMs > 1200) Serial.println("HX711 SIN LECTURA: revisa alimentacion y pines.");
        else Serial.printf("HX711 raw=%ld; g=%.2f; nivel=%d%%\n", rawScale, hopperGrams, levelPercent);
    }
}
void serviceSchedules() {
    if (!clockValid || active() || !storageReady) return;
    time_t now = time(nullptr); struct tm local; localtime_r(&now, &local);
    uint64_t minuteKey = static_cast<uint64_t>(now) / 60;
    if (minuteKey <= lastScheduleMinute) return;
    int day = (local.tm_year + 1900) * 10000 + (local.tm_mon + 1) * 100 + local.tm_mday;
    for (JsonObject item : scheduleArray()) {
        if (!petbite::due(item["enabled"], item["hour"], item["minute"], local.tm_hour, local.tm_min, item["lastDay"] | 0, day)) continue;
        // Registrar antes de servir; editar o reenviar no borra la marca diaria.
        item["lastDay"] = day;
        // PETMIN SEGURIDAD: borrar/recrear un horario en el mismo minuto no repite comida.
        if (prefs.putULong64("lastMinute", minuteKey) != sizeof(uint64_t) || !persistSchedules()) { storageReady = false; return; }
        lastScheduleMinute = minuteKey;
        String id = "s-" + deviceId + "-" + item["id"].as<String>() + "-" + String(day);
        if (findEvent(id).isNull() && !beginMeal(id, item["grams"], "schedule") && storageReady) {
            // Registrar una comida omitida; nunca recuperarla sirviendo otra después.
            if (archiveCount() < MAX_ARCHIVE_EVENTS) {
                if (eventsArray().size() >= MAX_EVENTS) { eventsArray().remove(0); history.garbageCollect(); }
                JsonObject missed = findEvent(id); if (missed.isNull()) missed = eventsArray().createNestedObject();
                missed["requestId"] = id; missed["timestamp"] = epochMs(); missed["grams"] = item["grams"];
                missed["source"] = "schedule"; missed["status"] = "failed"; missed["dispensedGrams"] = nullptr;
                missed["simulation"] = false;
                String reason = notReady(); missed["detail"] = reason.isEmpty() ? "Alimento insuficiente o memoria llena." : reason;
                if (!archiveEvent(missed) || !persistHistory()) storageReady = false;
            }
        }
        break;
    }
}
void registerRoutes() {
    server.collectHeaders(headerKeys, 1);
    server.on("/api/status", HTTP_GET, [] {
        if (!authorize()) return;
        StaticJsonDocument<1024> json;
        json["apiVersion"] = 3; json["online"] = true; json["deviceId"] = deviceId;
        json["retentionDays"] = retentionDays; json["nextSequence"] = lastSequence + 1; json["simulation"] = false; json["busy"] = active(); json["clockSynced"] = clockValid;
        String reason = notReady(); if (reason.isEmpty() && archiveCount() >= MAX_ARCHIVE_EVENTS) reason = "Historial lleno (512); espera retencion o ajusta dias."; json["ready"] = reason.isEmpty(); json["message"] = reason;
        if (scaleHealthy()) json["remainingGrams"] = max(0.0f, hopperGrams); else json["remainingGrams"] = nullptr;
        if (levelPercent >= 0) json["foodLevelPercent"] = levelPercent; else json["foodLevelPercent"] = nullptr;
        if (isfinite(lastGain)) json["lastDispensedGrams"] = lastGain; else json["lastDispensedGrams"] = nullptr;
        respond(200, json.as<JsonVariantConst>());
    });
    server.on("/api/events", HTTP_GET, [] {
        if (!authorize()) return;
        // No recorrer flash mientras el servo necesita lecturas frecuentes.
        if (active()) {
            DynamicJsonDocument response(24576); response["events"].set(eventsArray()); response["nextOffset"] = nullptr;
            respond(200, response.as<JsonVariantConst>()); return;
        }
        int offset = server.arg("offset").toInt();
        if (offset < 0 || offset > MAX_ARCHIVE_EVENTS) { reject(400, "Pagina invalida."); return; }
        DynamicJsonDocument response(24576); JsonArray list = response.createNestedArray("events");
        File root = LittleFS.open("/events"); int index = 0; bool more = false;
        for (File f = root.openNextFile(); f; f = root.openNextFile()) {
            if (index++ < offset) { f.close(); continue; }
            if (list.size() == 32) { more = true; f.close(); break; }
            StaticJsonDocument<1024> item;
            if (deserializeJson(item, f)) { f.close(); reject(503, "Registro ilegible; revisar almacenamiento."); return; }
            f.close(); list.add(item.as<JsonObject>());
        }
        if (more) response["nextOffset"] = offset + 32; else response["nextOffset"] = nullptr;
        respond(200, response.as<JsonVariantConst>());
    });
    server.on("/api/preferences", HTTP_PUT, [] {
        if (!authorize()) return;
        if (active()) { reject(409, "Espera a que termine la porcion."); return; }
        StaticJsonDocument<128> json; if (!parseBody(json)) return;
        int days = json["retentionDays"] | 0;
        if (days != 7 && days != 30) { reject(400, "Retencion: 7 o 30 dias."); return; }
        if (prefs.putInt("retention", days) != sizeof(int)) { reject(503, "No se pudo guardar."); return; }
        retentionDays = days; pruneArchive(); json["saved"] = true; respond(200, json.as<JsonVariantConst>());
    });
    server.on("/api/dispense", HTTP_POST, [] {
        if (!authorize()) return;
        StaticJsonDocument<512> json; if (!parseBody(json)) return;
        String id = json["requestId"] | "";
        if (!validId(id) || !json["grams"].is<int>() || !petbite::validPortion(json["grams"])) { reject(400, "Identificador o porcion invalida."); return; }
        JsonObject previous = findEvent(id);
        if (!previous.isNull()) {
            if (previous["grams"].as<int>() != json["grams"].as<int>()) { reject(409, "Identificador usado con otra porcion."); return; }
            respond(200, previous); return;
        }
        String reason = notReady(); if (!reason.isEmpty()) { reject(409, reason); return; }
        uint64_t sequence = json["sequence"] | uint64_t(0);
        if (!petbite::sequenceAllowed(sequence, lastSequence)) { reject(409, "Secuencia antigua: actualiza estado antes de solicitar otra porcion."); return; }
        // PETMIN SEGURIDAD: consumir secuencia antes de mover, incluso si falla después.
        if (prefs.putULong64("sequence", sequence) != sizeof(uint64_t)) { reject(503, "Fallo de memoria."); return; }
        lastSequence = sequence;
        if (!beginMeal(id, json["grams"], "manual")) { reject(503, "No se pudo iniciar la porcion. Revisa peso y memoria."); return; }
        respond(202, findEvent(id));
    });
    server.on("/api/stop", HTTP_POST, [] {
        if (!authorize()) return;
        if (active()) finishMeal("failed", "Detenido desde la aplicacion; peso final no estabilizado.", NAN);
        else closeGate();
        StaticJsonDocument<64> json; json["stopped"] = true; respond(200, json.as<JsonVariantConst>());
    });
    server.on("/api/schedules", HTTP_PUT, [] {
        if (!authorize()) return;
        if (active()) { reject(409, "Espera a que termine la porcion antes de cambiar horarios."); return; }
        DynamicJsonDocument json(12288); if (!parseBody(json)) return;
        if (json["timezone"].as<String>() != "America/Lima" || !json["schedules"].is<JsonArray>() || json["schedules"].size() > 12) { reject(400, "Se admiten 12 horarios diarios en America/Lima."); return; }
        JsonArray incoming = json["schedules"];
        DynamicJsonDocument replacement(12288); JsonArray target = replacement.to<JsonArray>();
        for (JsonObject item : incoming) {
            if (!validId(item["id"] | "") || !item["hour"].is<int>() || !item["minute"].is<int>() || !item["grams"].is<int>() || !item["enabled"].is<bool>() ||
                !petbite::validTime(item["hour"], item["minute"]) || !petbite::validPortion(item["grams"])) { reject(400, "Horario invalido."); return; }
            for (JsonObject other : target) if (other["id"].as<String>() == item["id"].as<String>() ||
                (other["hour"].as<int>() == item["hour"].as<int>() && other["minute"].as<int>() == item["minute"].as<int>())) { reject(400, "Horarios duplicados."); return; }
            JsonObject clean = target.createNestedObject();
            clean["id"] = item["id"]; clean["hour"] = item["hour"]; clean["minute"] = item["minute"];
            clean["grams"] = item["grams"]; clean["enabled"] = item["enabled"]; clean["lastDay"] = 0;
            for (JsonObject old : scheduleArray()) if (old["id"].as<String>() == item["id"].as<String>()) clean["lastDay"] = old["lastDay"] | 0;
        }
        String saved; serializeJson(replacement, saved);
        if (!storageReady || prefs.putString("schedules", saved) != saved.length()) { reject(503, "No se pudieron guardar los horarios."); return; }
        timetable.set(replacement);
        StaticJsonDocument<128> reply; reply["saved"] = true; reply["count"] = incoming.size(); respond(200, reply.as<JsonVariantConst>());
    });
    server.onNotFound([] {
        if (!authorize()) return;
        if (server.method() == HTTP_GET && server.uri().startsWith("/api/jobs/")) {
            JsonObject item = findEvent(server.uri().substring(10));
            if (!item.isNull()) { respond(200, item); return; }
            reject(404, "Orden no encontrada en los ultimos registros. Comprueba el recipiente."); return;
        }
        reject(404, "Ruta no encontrada.");
    });
}
void setup() {
    Serial.begin(115200);
    xTaskCreate(motorWatchdog, "petbite-guard", 2048, nullptr, 3, nullptr);
    char chipId[18]; snprintf(chipId, sizeof(chipId), "%llX", static_cast<unsigned long long>(ESP.getEfuseMac())); deviceId = chipId;
    if (ACTUATOR_CONFIGURED) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
        servoReady = ledcAttach(SERVO_PIN, 50, 16);
#else
        servoReady = ledcSetup(0, 50, 16) > 0; ledcAttachPin(SERVO_PIN, 0);
#endif
        closeGate();
    }
    scale.begin(HX711_DATA_PIN, HX711_CLOCK_PIN);
    pinMode(BUZZER_PIN, OUTPUT); digitalWrite(BUZZER_PIN, LOW);
    storageReady = prefs.begin("petmin3", false);
    lastSequence = prefs.getULong64("sequence", 0);
    lastScheduleMinute = prefs.getULong64("lastMinute", 0);
    retentionDays = prefs.getInt("retention", 30); if (retentionDays != 7) retentionDays = 30;
    if (!LittleFS.begin(FORMAT_FS_ON_FAILURE)) { storageReady = false; Serial.println("LittleFS no disponible: consulta instalacion."); }
    else if (!LittleFS.exists("/events") && !LittleFS.mkdir("/events")) storageReady = false;
    if (deserializeJson(history, readHistory()) || !history.is<JsonArray>()) { history.to<JsonArray>(); storageReady = false; }
    if (deserializeJson(timetable, prefs.getString("schedules", "[]")) || !timetable.is<JsonArray>()) { timetable.to<JsonArray>(); storageReady = false; }
    for (JsonObject item : eventsArray()) {
        String status = item["status"] | "";
        if (status == "accepted" || status == "dispensing") { item["status"] = "interrupted"; item["detail"] = "Reinicio durante la entrega. Revisa el recipiente; no se repetira automaticamente."; }
        if (item["status"].as<String>() == "completed") lastGain = item["dispensedGrams"];
    }
    // Recuperar también archivos aceptados si el corte ocurrió antes de actualizar NVS.
    if (storageReady) {
        File root = LittleFS.open("/events");
        for (File f = root.openNextFile(); f; f = root.openNextFile()) {
            StaticJsonDocument<1024> item; bool ok = !deserializeJson(item, f); f.close();
            if (!ok) { storageReady = false; break; }
            String state = item["status"] | "";
            if (state == "accepted" || state == "dispensing") {
                item["status"] = "interrupted"; item["detail"] = "Reinicio: no repetir automaticamente; revisar recipiente.";
                if (!archiveEvent(item.as<JsonObject>())) { storageReady = false; break; }
            }
        }
    }
    if (!persistHistory()) storageReady = false;
    WiFi.mode(WIFI_STA); WiFi.setAutoReconnect(true); WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    configTzTime("PET5", "pool.ntp.org", "time.google.com");
    registerRoutes(); server.begin();
    Serial.println("PetMin v3 listo. Esperando Wi-Fi. Configura IP y clave en la app.");
}
void loop() {
    // Sensores y cierre tienen prioridad sobre nuevas peticiones.
    sampleSensors(); serviceMeal();
    clockValid = time(nullptr) > 1700000000;
    server.handleClient();
    serviceSchedules();
    if (!active() && millis() - lastCleanupMs > 60000) { lastCleanupMs = millis(); pruneArchive(); }
    if (millis() - lastWiFiMs >= 10000) {
        lastWiFiMs = millis();
        if (WiFi.status() != WL_CONNECTED) WiFi.reconnect();
        else Serial.println("Direccion de la app: http://" + WiFi.localIP().toString());
    }
    delay(2);
}
