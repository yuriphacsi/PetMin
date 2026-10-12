# Verificación PetMin 3.0 — 12/10/2026

Reemplaza informe de PetBite 2.0: no transfiere sus resultados a esta revisión.

| Comprobación | Resultado real |
|---|---|
| Inspección de código/ZIP | Realizada |
| Reglas C++ con g++ -std=c++17 -Wall -Wextra -Werror | Compiladas y ejecutadas con aserciones activas, exit 0 |
| Android testDebugUnitTest + assembleDebug | Intentados, bloqueo al descargar Gradle 8.9: Network is unreachable |
| Suite HTTP/JUnit actualizada | NO ejecutada por bloqueo Gradle |
| Firmware completo ESP32 | NO compilado; herramientas/core/bibliotecas no disponibles |
| UI, teléfono/emulador y OpenGL | NO ejecutados |
| Hardware, flash, red y dosificación | NO probados |
| APK | NO generado ni verificado |

Logs: pruebas/gradle.txt y pruebas/reglas.txt. Reglas nativas no compilan sketch ni verifican sensores, concurrencia o hardware.

## Reproducir
Con JDK 17, SDK 35 y red: `gradlew.bat testDebugUnitTest assembleDebug`. Ver app/build/test-results/testDebugUnitTest/. APK esperado en app/build/outputs/apk/debug/app-debug.apk. Instalar Android 8+. Si firma difiere de una instalación previa no se puede actualizar directamente; respaldar antes de desinstalar.
Firmware: Arduino IDE Verificar con herramientas indicadas en CONFIGURAR_ESP32.md.
Reglas: `g++ -std=c++17 -Wall -Wextra -Werror firmware/tests/rules_test.cpp -o rules_test` y ejecutar binario.

## Aceptación física y Android pendiente
Registrar fecha, responsable, esperado y observado, sin inventar evidencias:
- IP/token incorrectos, ESP32 apagado: error sin declarar entrega.
- Sin calibrar: conectado pero bloqueado; sin medición ficticia.
- Pesos patrón y 10 porciones por tamaño: comparar tolva y balanza externa.
- Doble toque, replay mismo ID/secuencia, replay tras >16 eventos y reinicio: no duplicar.
- Dos teléfonos: solo uno obtiene la secuencia.
- Desconectar sensor, atascar con supervisión, mover/recargar tolva: cierre y fallo.
- Paro, red perdida y corte eléctrico: no repetir automáticamente; cierre mecánico probado.
- Horario con app cerrada; reinicio sin NTP: no recuperar comidas atrasadas.
- Editar/borrar/reenviar mismo minuto: no duplicar.
- Retención 7/30, 512 registros y flash llena: conservar perfiles/logros; bloqueo seguro por capacidad.
- Perfil/foto y reinicio Android: datos accesibles, errores de imagen manejados.
- Gaming: perro/gato, giro, reposo/alegría/comida, cinco desafíos, progreso 0–100 guardado; cero llamadas IoT.
- Pantalla pequeña/rotación/teclado/segundo plano: usable y renderer pausado.

Antes de uso autónomo verificar circuito, mecanismo y todas las protecciones. Código implementado no equivale a dosificación validada.
