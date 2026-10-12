# PetMin 3.0 — Android + ESP32 + Gaming 3D

Código actualizado el 12/10/2026. **Compilación Android y firmware completo aún no verificados; no incluye APK.** Esta entrega no certifica funcionamiento con hardware.

## Empezar
1. Abrir carpeta PetMin en Android Studio, JDK 17 y SDK 35.
2. Con acceso a Internet: `gradlew.bat testDebugUnitTest assembleDebug` (Windows) o `bash gradlew testDebugUnitTest assembleDebug` (Linux).
3. Instalar el APK que se genere en `app/build/outputs/apk/debug/app-debug.apk`. No existe dentro de este ZIP.
4. Configurar/cargar firmware siguiendo `docs/CONFIGURAR_ESP32.md`, inicialmente con actuador deshabilitado.
5. Android y ESP32 en la misma red; guardar IP y clave en Ajustes. Ambos deben utilizar API 3.

## Implementado en código
- Inicio, Porción, Horarios, Gaming, Historial y Ajustes.
- Estado real, gramos/porcentaje de tolva de 2000 g, aviso de nivel bajo y buzzer.
- Porciones manuales confirmadas, secuencias persistentes antirrepetición, paro y errores.
- Pérdida de peso con HX711, estabilidad previa, filtrado, tolerancia y protecciones de tiempo/sensor/flujo.
- Hasta 12 horarios diarios persistentes en ESP32, NTP/Lima, marcas contra duplicados y recuperación tras reinicios.
- Historial Room y LittleFS paginado, retención 7/30 días, estados interrumpidos y comidas omitidas.
- Perfiles editables: nombre, perro/gato, edad en meses, peso opcional, foto opcional y preferencias.
- Mascota 3D original geométrica: perro/gato, habitación, giro limitado, reposo, alegría y alimentación virtual.
- Cinco desafíos con puntuación persistente y totalmente separados del dispensador.
- Comentarios en español, plan para cuatro integrantes y protocolo de pruebas.

## Pendiente o limitado
- Compilar e instalar Android; compilar/cargar ESP32 y ejecutar pruebas reales de hardware, UI y OpenGL.
- Validar precisión, torque SG90, atascos, pérdida de red, cortes eléctricos, flash y horarios con app cerrada.
- Calibración y Wi-Fi del ESP32 por USB/archivos: no hay calibración remota ni aprovisionamiento Wi-Fi desde Android.
- Aviso de nivel bajo en pantalla y buzzer; no hay notificaciones móviles con app cerrada.
- Pérdida de peso de tolva estima salida; no confirma llegada al plato ni consumo. Para eso hace falta otra medición.
- Futuro: escaneo/fotogrametría, accesorios, monedas, nube y más niveles/habitaciones.

Se conserva paquete com.yuri.petbite y esquema Room 1 sin borrar datos. Campos heredados demo/lastDemoDay en Room solo conservan compatibilidad; no existe dispensación simulada. La copia original está en `respaldo/` del ZIP final. Sus informes antiguos no prueban esta revisión.

Leer diagnóstico, plan, API, calibración, verificación y recursos en docs/.
