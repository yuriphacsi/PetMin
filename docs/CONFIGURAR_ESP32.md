# Configurar ESP32, circuito y calibración

## Conexiones para ESP32 clásico
| Elemento | Conexión |
|---|---|
| Señal SG90 | GPIO13 |
| Alimentación SG90 | Fuente externa regulada 5 V, corriente suficiente según corriente de bloqueo del servo |
| Tierra servo | GND común con ESP32 y fuente |
| HX711 DT/DOUT | GPIO32 |
| HX711 SCK | GPIO33 |
| Lógica HX711 | Compatible con 3,3 V; verificar módulo |
| Celda 10 kg | E+/E− excitación, A+/A− señal según ficha; no asumir colores |
| Buzzer activo | GPIO25 mediante transistor si supera corriente admisible del GPIO |

No alimentar servo desde 3,3 V ni introducir señales de 5 V al ESP32. La celda pesa la tolva completa; descontar estructura vacía en la tara. Evitar apoyos adicionales y cables tirantes que desvíen peso. No se utiliza HC-SR04, RFID ni balanza bajo el plato.

El servo sin corriente NO garantiza cierre. Incorporar compuerta de retorno mecánico y corte físico de alimentación antes de uso autónomo. El watchdog no resuelve un corte eléctrico o atasco mecánico.

## Herramientas objetivo
Arduino IDE, ESP32 Dev Module clásico, core Espressif 3.0.7, ArduinoJson 6.21.5, HX711 de Bogdan Necula 0.7.5. Versiones heredadas, pendientes de compilar esta revisión. WiFi/WebServer/Preferences/LittleFS incluidos en core. PWM LEDC a 50 Hz, sin dependencia ESP32Servo. Elegir partición con espacio de archivos de al menos 512 KiB.
URL de placas: https://espressif.github.io/arduino-esp32/package_esp32_index.json

## Wi-Fi e instalación
1. Abrir firmware/PetBiteESP32/PetBiteESP32.ino.
2. Copiar Secrets.h.example como Secrets.h; completar SSID 2,4 GHz, contraseña y token propio ≥8 caracteres. Nunca subir Secrets.h ni incluirlo en ZIP compartidos.
3. Mantener ACTUATOR_CONFIGURED=false y HX711_CALIBRATED=false.
4. Primera instalación: FORMAT_FS_ON_FAILURE=true permite crear LittleFS si no puede montar. Cargar, verificar y volver a false. **No dejar true: un error de montaje podría borrar historial.**
5. Monitor serie 115200: anotar http://IP. Configurar en Android esa dirección y token. Misma red sin aislamiento de clientes. Puede consultarse conexión antes de calibrar; no permitirá dispensar.
6. Reservar IP en router si se desea evitar cambios. El Wi-Fi se configura por USB; la app configura IP/token.

HTTP local con token, sin cifrado del transporte. Usar red de confianza; no exponer el puerto 80 a Internet. Acceso remoto requiere diseño seguro adicional.

## Calibración HX711
1. Montar tolva vacía, actuador deshabilitado. Promediar 20 lecturas raw estables del monitor y guardar HX711_ZERO.
2. Añadir masa conocida de 500 o 1000 g y promediar otras 20 lecturas.
3. Factor = (raw_con_masa − raw_vacío) / gramos_conocidos. Guardar HX711_COUNTS_PER_GRAM. Puede ser negativo, nunca cero.
4. Activar HX711_CALIBRATED y cargar. Comprobar vacío, 100, 500, 1000 y cerca de 2000 g con patrón externo, registrando errores.
5. Si no hay estabilidad, corregir estructura/ruido/alimentación. La celda de 10 kg no garantiza precisión para porciones pequeñas. Considerar celda de menor rango que soporte estructura y alimento.

No tarar automáticamente una tolva llena al encender. Capacidad de alimento configurada 2000 g; la capacidad mecánica incluye estructura.

## Mecanismo y medición de porciones
Verificar posiciones de cierre/apertura con tolva vacía y brazo inicialmente desacoplado. Configurar ángulos usando rango de pulsos 1000–2000 μs. Habilitar ACTUATOR_CONFIGURED solo tras comprobar cierre, incluida puesta en marcha.

El firmware exige 1,5 s estable (cambios consecutivos ≤1 g), filtra con factor 0,5, estima inicial−final y cierra anticipadamente con margen inicial 2 g. Cierra por 12 s máximo, 4 s sin flujo, lectura perdida o salto >80 g por muestra. Un aumento >5 g durante entrega provoca fallo. Watchdog independiente por falta de muestras >1,5 s o tiempo máximo. Espera ≥700 ms y estabilidad al cerrar, máximo 5 s; tolerancia inicial ±5 g. Hay 30 s entre operaciones durante el mismo encendido. Son parámetros iniciales, no precisión garantizada.

Hacer 10 repeticiones por porción elegida. Medir solicitado, pérdida de tolva, peso recibido en balanza externa, error y atascos. Para porciones de 5 g, la tolerancia inicial no es adecuada como garantía de precisión: acordar una tolerancia útil y ajustar mecanismo antes de uso real. No aflojar validaciones para ocultar ruido.

Si SG90 no tiene torque o las croquetas traban la salida, cambiar geometría o actuador; no aumentar indefinidamente tiempos. Pérdida de tolva no confirma llegada al plato: alimento puede quedar en conducto. Añadir segunda balanza para confirmar recepción.

## Horarios, reloj y almacenamiento
Crear/editar/eliminar en Android y pulsar Enviar horarios al ESP32. Hasta confirmación conserva los anteriores. NTP obtiene hora Lima; después continúa sin Internet mientras siga encendido. Tras corte requiere sincronizar otra vez. Sin NTP no dispensa. Futuro: RTC con batería.

No recupera comidas pasadas. Marca persistente antes de servir: ante corte puede omitirse la comida, nunca se repite automáticamente. Editar mismo ID conserva marca diaria; borrar/recrear en mismo minuto no repite horario. Nueva orden manual es una nueva decisión explícita.

Hasta 512 registros LittleFS; si se llena rechaza nuevas entregas sin borrar registros recientes. Retención 7/30 días enviada al conectar: ESP32 limpia cada minuto inactivo con reloj válido; Android al iniciar, sincronizar o cambiar preferencia. Con app cerrada no limpia almacenamiento Android hasta abrir. Perfiles, juego, secuencias y horarios no se borran con historial.
