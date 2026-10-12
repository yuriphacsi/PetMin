# Recursos, referencias y ampliaciones

## Recursos 3D
`gaming/PetScene.kt` define geometría procedural original prediseñada de perro y gato, habitación y animaciones. No descarga ni incorpora modelos ajenos. La geometría y animaciones originales incluidas pueden reutilizarse bajo CC0-1.0 (https://creativecommons.org/publicdomain/zero/1.0/); esto no cambia las licencias de Android, dependencias o código heredado. Iconos proceden de Material Icons mediante dependencia existente. Las fotos las selecciona el usuario.

Se conserva Gradle/GRADLE_LICENSE.txt. Dependencias de terceros mantienen sus respectivas licencias; no se empaquetan binarios adicionales ni activos de procedencia desconocida.

## Referencias consultadas
- Android GLSurfaceView/OpenGL ES: https://developer.android.com/develop/ui/views/graphics/opengl/environment
- Ciclo de vida del renderer: https://developer.android.com/reference/android/opengl/GLSurfaceView
- HX711 biblioteca y lectura: https://github.com/bogde/HX711
- Contenido educativo, ASPCA: https://www.aspca.org/pet-care/aspca-poison-control/people-foods-avoid-feeding-your-pets

Los desafíos contienen paráfrasis breves y no calculan raciones veterinarias. Uvas/pasas se presentan específicamente como riesgo documentado en perros; el juego promueve mantenerlas alejadas de mascotas, sin afirmar una dosis segura en gatos.

## Extensiones posteriores
- Renderer separado permite sustituir geometría por glTF o motor 3D sin modificar cliente IoT.
- Almacén `petmin_gaming` independiente permite migrar logros, monedas y niveles sin tocar Room de alimentación.
- Perfiles con UUID permiten más mascotas, selección activa y futuras asociaciones con horarios.
- Escaneo/fotogrametría requiere captura multivista, procesamiento/modelado y revisión de privacidad/licencias: no es un botón de cámara que genere automáticamente un avatar fiable.
- Nube requiere autenticación y transporte seguro. Nunca introducir llamadas a dispensador desde acciones del juego.
- RTC con batería permitiría recuperar hora sin Internet; segunda balanza permitiría contrastar recepción en plato.
