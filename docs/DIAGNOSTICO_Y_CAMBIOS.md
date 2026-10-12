# Diagnóstico y cambios de esta entrega

## Estado al recibir el ZIP
33 archivos: Android Kotlin/Compose/Material 3, AndroidViewModel, StateFlow, Room 2.6.1, SharedPreferences, cliente HttpURLConnection con coroutines; firmware Arduino ESP32, Preferences, ArduinoJson, HX711 y PWM LEDC. AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, BOM Compose 2024.12.01, SDK 35, mínimo 26, JDK 17.

Se leyeron AGENTE_PETMIN.md y LEEME.md. El primero solicita firmware SG90/tolva y Gaming; el segundo declara una actualización parcial. El informe de verificación antiguo pertenece a PetBite 2.0 y **no demuestra** que esta revisión PetMin compile.

Problemas detectados: medición del plato en lugar de la tolva; GPIO18/HC-SR04 incompatibles con circuito previsto; rama simulada todavía existente en firmware; código de interfaz con condiciones constantes residuales; historial ESP32 de solo 16 registros; deduplicación vinculada a esos registros; ausencia de perfiles y Gaming; documentación mezclada de versiones.

## Reutilización
Se preservaron proyecto Gradle, paquete Android, tema, navegación y pantallas existentes, entidades Room y esquema, validaciones, cliente HTTP asíncrono, stop, sincronización de horarios y recuperación de operaciones no confirmadas. No se reconstruyó la aplicación desde cero.

## Etapas realizadas
1. Respaldo ZIP original intacto y diagnóstico. Intento inicial de Gradle: descarga bloqueada por red.
2. Firmware para pérdida de peso de tolva, SG90 GPIO13, HX711 GPIO32/33, buzzer GPIO25, eliminación de simulación, API 3 y configuración de secretos local.
3. Secuencia persistente para órdenes manuales, intervalo de espera, peso estable, pérdida de lectura, cambio brusco, tolerancia, reloj y marca de minuto programado. Historial de archivos LittleFS, recuperación, páginas y retención.
4. Adaptación Android al contrato nuevo, gramos restantes y perfiles en almacenamiento separado.
5. Escena 3D original nativa, dos mascotas, habitación y animaciones; cinco desafíos persistentes aislados del ESP32.
6. Pruebas nativas de reglas, ampliación de pruebas HTTP, revisión de fuentes y documentación. Segundo intento de Gradle bloqueado también.

## Decisión 3D
Godot/Unity agregan exportación, integración y dependencias que no estaban en el ZIP. Para esta primera entrega se utiliza GLSurfaceView/OpenGL ES con geometría prediseñada original de estilo de bloques. Es renderizado tridimensional real con profundidad y cámara, no un video ni una imagen 2D. Es sencillo y no equivale a un modelo escaneado o realista. La UI educativa no depende del renderer: podrá sustituirse por un motor/modelos glTF posteriormente.

## Mapa de código
- `PetViewModel.kt`: estado IoT, acciones, mensajes y sincronización.
- `device/FeederApi.kt`: contrato HTTP y manejo de resultados inciertos.
- `device/Validation.kt`: entradas de usuario.
- `data/Database.kt`: Room, historial y horarios.
- `data/Profiles.kt`: perfiles; `data/Settings.kt`: ajustes.
- `ui/PetApp.kt`, `Profiles.kt`, `GamingScreen.kt`: pantallas.
- `gaming/PetScene.kt`: renderer y animaciones, sin referencia a IoT.
- `firmware/PetBiteESP32/`: nombre de carpeta conservado para Arduino; firmware PetMin API 3.
- `firmware/tests/rules_test.cpp`: reglas puras verificables sin hardware.

## Límites de la arquitectura
Un dispensador por configuración. Sin backend, acceso remoto, emparejamiento automático ni cifrado HTTP. Los perfiles son registros de referencia, no determinan porciones. El nombre de saludo en Ajustes es independiente del perfil y del nombre virtual. Una mascota virtual activa, progreso único que se conserva al alternar especie. Horarios requieren botón explícito de sincronización; confirmar visualmente después de restaurar/borrar ESP32 porque la marca local de sincronización no detecta un borrado externo.

Respaldo dentro del ZIP final y diferencias en `docs/CAMBIOS.patch`. No se modificaron credenciales reales. La base recibida permanece recuperable.
