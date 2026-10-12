# PetMin API 3

HTTP local, Authorization: Bearer <API_TOKEN>, JSON UTF-8. Android rechaza API 2; distribuir firmware y app juntos.

| Método/ruta | Función |
|---|---|
| GET /api/status | Estado, medición y próxima secuencia |
| POST /api/dispense | Orden manual única |
| GET /api/jobs/{id} | Consultar últimas 16 órdenes sin accionar |
| POST /api/stop | Cerrar y registrar fallo, peso desconocido |
| PUT /api/schedules | Reemplazar hasta 12 horarios, incluida lista vacía |
| GET /api/events?offset=0 | Página de 32 eventos |
| PUT /api/preferences | Retención 7/30 días |

Status: apiVersion=3, online, deviceId, simulation=false, busy, clockSynced, ready, message, remainingGrams, foodLevelPercent, lastDispensedGrams, nextSequence, retentionDays. Medidas desconocidas son null. Online no implica ready.

Orden: `{"requestId":"uuid-orden-001","grams":40,"sequence":1}`. Gramos 5–300. Leer nextSequence antes de solicitar; se persiste antes de mover. Repetir ID reciente devuelve resultado previo; secuencia antigua se rechaza aun si su evento fue eliminado. Varios teléfonos con misma secuencia: uno gana; el otro debe actualizar y requerir otra acción explícita. No repetir POST tras timeout. Borrar NVS reinicia protección y exige puesta en servicio supervisada.

Evento: requestId, timestamp Unix en milisegundos, grams, dispensedGrams nullable, source manual/schedule, status accepted/dispensing/completed/failed/interrupted, detail, simulation=false. Completed exige tolerancia configurada, no certifica llegada al plato. Fallo puede incluir peso parcial. Rechazo explícito HTTP no 2xx tiene rejected=true; otros errores se consideran inciertos.

Horarios: `{"timezone":"America/Lima","schedules":[{"id":"uuid-horario-001","hour":8,"minute":0,"grams":40,"enabled":true}]}`. Respuesta saved=true,count=1. Sin horas/IDs duplicados; marca diaria conservada por ID.

Preferencias: `{"retentionDays":7}`, respuesta saved=true.
Historial: events más nextOffset (32,64,... o null al finalizar), máximo 512 archivos. Durante entrega devuelve caché de últimas órdenes para evitar acceso prolongado a flash; sincronización posterior recoge archivo completo. Room deduplica por ID. Paginación no es instantánea transaccional: cambios concurrentes se reconcilian al siguiente refresco.

Secuencia no sustituye token. Gaming no recibe cliente API ni envía órdenes.
