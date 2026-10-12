#pragma once
// PETMIN HARDWARE: ESP32 clásico. Nunca guardar credenciales reales en Git.
#if __has_include("Secrets.h")
#include "Secrets.h"
#else
constexpr char WIFI_SSID[] = "";
constexpr char WIFI_PASSWORD[] = "";
constexpr char API_TOKEN[] = "";
#endif
constexpr int SERVO_PIN = 13;
constexpr int HX711_DATA_PIN = 32;
constexpr int HX711_CLOCK_PIN = 33;
constexpr int BUZZER_PIN = 25; // Buzzer activo mediante transistor si supera corriente del GPIO.
constexpr bool ACTUATOR_CONFIGURED = false;
constexpr int SERVO_MIN_US = 1000, SERVO_MAX_US = 2000;
constexpr int SERVO_CLOSED_DEGREES = 0, SERVO_OPEN_DEGREES = 65;
constexpr unsigned long MAX_OPEN_MS = 12000, NO_FLOW_TIMEOUT_MS = 4000;
constexpr float STOP_MARGIN_GRAMS = 2.0f;
constexpr float CAPACITY_GRAMS = 2000.0f;
constexpr float MAX_TOLERANCE_GRAMS = 5.0f;
// PETMIN HARDWARE: tara con tolva vacía montada; factor puede ser negativo.
constexpr bool HX711_CALIBRATED = false;
constexpr long HX711_ZERO = 0;
constexpr float HX711_COUNTS_PER_GRAM = 1.0f;

// Solo primera instalación: true permite formatear si no puede montar. Volver a false.
constexpr bool FORMAT_FS_ON_FAILURE = false;
