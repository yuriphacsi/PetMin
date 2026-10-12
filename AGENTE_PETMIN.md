# AGENTE PETMIN — Instrucciones permanentes del proyecto

## Objetivo
Completar PetMin en 3 semanas con cuatro integrantes, manteniendo una app Android Kotlin/Jetpack Compose, ESP32 real y minijuego educativo con mascota 3D. Nunca reintroducir modo demo o simulación como experiencia del usuario.

## Flujo obligatorio del agente
1. Leer LEEME.md, código, tests y documentación; presentar plan corto antes de editar.
2. Trabajar por cambios pequeños; comentar el propósito de las decisiones importantes en español y usar etiquetas `PETMIN FUTURO:` solo para extensiones reales.
3. No afirmar que compila, conecta o dispensa si no se ejecutaron las pruebas.
4. Tras cada cambio ejecutar `gradlew testDebugUnitTest` y `gradlew assembleDebug` si el entorno lo permite; informar bloqueos.
5. Nunca imprimir, subir ni registrar claves Wi-Fi ni API_TOKEN; conservar secretos fuera de repositorios.
6. Mantener sincronizados contratos entre Android y ESP32; no cambiar rutas JSON unilateralmente.
7. Usar ramas y commits separados por hardware, Android, Gaming y datos; revisar conflictos antes de fusionar.
8. Priorizar seguridad física: paro, errores, calibración, alimentación externa de servo, GND común, sin apertura si la medición no es fiable.

## Arquitectura y tareas prioritarias
### P0 — compatibilidad del hardware
El código legado usa servo MG996R en GPIO18, HX711 bajo el plato y HC-SR04. El proyecto real plantea SG90 GPIO13, HX711 GPIO32/33, celda 10kg bajo tolva de 2kg, buzzer GPIO25 y RFID opcional. Validar pines y montaje con el integrante de electrónica. Rediseñar firmware para estimar gramos dispensados como reducción de peso del depósito, con filtrado y tiempo de asentamiento. No suponer precisión sin mediciones. El SG90 puede ser insuficiente: ofrecer alternativa de motor/compuerta si falla.
### P1 — Android IoT
Pantalla de estado, Wi-Fi local, porciones, paro, horarios persistidos en ESP32, historial real, retención 7/30 días, alertas de poca comida. Conservar mensajes claros para desconexión, ausencia de calibración y órdenes no confirmadas. No repetir automáticamente una orden cuyo resultado es incierto.
### P2 — Gaming 3D acotado
Perro y gato prediseñados, una mascota activa, cámara fija con giro limitado, una habitación, cinco desafíos progresivos sobre alimentos seguros/peligrosos, puntuación y progreso persistente independiente del historial IoT. Evaluar integración Android/Godot temprano. Nunca usar acciones del juego para dispensar alimento real. Verificar contenidos educativos por especie y evitar representar consumo de tóxicos.
### P3 — futuro, fuera del alcance inicial
Escaneo de mascotas con cámara y reconstrucción 3D, RFID, múltiples habitaciones, tienda completa, nube y personalización avanzada. Dejar interfaces extensibles, no código incompleto que rompa la compilación.

## Definición de terminado
APK instalable, conexión real ESP32, porción medida con calibración documentada, historial configurable, horarios probados con app cerrada, cinco desafíos funcionales y demostración de los cuatro integrantes. Si hay limitaciones, documentarlas sin ocultarlas.

## Plan de tres semanas
Semana 1: revisar, adaptar firmware/circuito, compilar Android, seleccionar activos 3D.
Semana 2: conectar hardware real, historial y horarios, construir cinco desafíos.
Semana 3: integración, pruebas, evidencias, correcciones y presentación. Congelar funciones al día 14.
