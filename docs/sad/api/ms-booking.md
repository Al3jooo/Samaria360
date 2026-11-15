# API de ms-booking (reservas)

Contrato del CU «Reservar servicio» (RF11, RF18, RF20, RF32, RF37, RF44) para el frontend (WF-10) y para las pruebas. Todas las rutas van por el API Gateway (`/api/v1/reservas/**`); el servicio escucha en el 8083 solo dentro de la red de Docker. Decisiones en `docs/trazabilidad.md` («Reservar servicio») y [ADR-007](../adr/ADR-007-reservas-y-catalogo.md).

## Convenciones

- **Autenticación:** `Authorization: Bearer <JWT de ms-auth>`. Reservar, consultar y cancelar exigen el rol **Turista**; el turista es el `sub` del token. La disponibilidad es pública. Todo Bearer que llegue, también a un endpoint público, se valida: un token con el claim `ver` menor que la versión de la cuenta recibe **401 `SESION_INVALIDADA`** ([ADR-008](../adr/ADR-008-sincronizacion-estado-cuentas.md)). Sin fila para la cuenta, se acepta un token de Turista o de Prestador (también sin `ver`), pero un token de Gestor o Administrador recibe 401 `SESION_INVALIDADA` (DEC-110, enmienda RF45 de ADR-008).
- **Fechas:** `aaaa-mm-dd`; horas: `hh:mm` (la respuesta usa `hh:mm:ss`); instantes: ISO 8601 en UTC (`2030-03-30T13:00:00Z`). Todo se interpreta en la hora de Santa Marta (`America/Bogota`).
- **Dinero:** COP, número decimal.
- **Errores:** ProblemDetail (RFC 9457) con `codigo` estable, como ms-auth y ms-catalog. `DATOS_INVALIDOS` lleva `campos`: un código por campo con el nombre JSON, nunca textos.

## Reservar

`POST /api/v1/reservas` · rol Turista · cabecera **`Idempotency-Key: <UUID>`** obligatoria

```json
{
  "idServicio": "6a0f…",          // ObjectId del servicio (catalog_db)
  "idUnidad": "6b2c…",            // solo alojamiento: la unidad del detalle de ms-catalog
  "fechaEntrada": "2030-03-30",   // alojamiento: entrada; tour: el día
  "fechaSalida": "2030-04-01",    // solo alojamiento: rango [entrada, salida), máximo 14 noches
  "horaInicio": "08:00",          // solo tour: un horario publicado
  "cantidad": 1,                  // solo alojamiento: habitaciones de la unidad
  "personas": 2,                  // huéspedes o personas del tour
  "precioTotalEsperado": 520000   // el total que ve el turista (RF37)
}
```

**Reglas.**
- **Una reserva es de una sola unidad.** Para dos tipos de habitación se hacen dos reservas.
- **Alojamiento:** cobra `precioNoche` de la unidad × noches × habitaciones. `personas` ≤ habitaciones × `capacidadMaxHuespedes` de la unidad. Check-in 15:00 y check-out 12:00; con entrada hoy, se puede reservar hasta las 15:00.
- **Tour:** cobra `precioBase` × personas. `personas` ≤ `aforoMaxGrupo`. No se reserva un horario que ya pasó.
- La confirmación es automática (RF20): los cupos se descuentan en el momento.
- Antes de abrir la transacción, materializa la oferta con el detalle que ya consultó (sin otra llamada; DEC-152). Un día en que el servicio no opera, una salida que no ofrece o una fecha posterior a la ventana de 60 días responden `FECHA_NO_DISPONIBLE`.
- **`precioTotalEsperado` se compara por valor, no por escala:** `160001` y `160001.00` son el mismo total. Solo un valor distinto responde `PRECIO_CAMBIADO`.

**Respuesta `201 Created`**, con `Location: /api/v1/reservas/{idReserva}`:

```json
{
  "idReserva": "3f1c…", "estado": "Confirmada",
  "idServicio": "6a0f…", "nombreServicio": "Hotel Casa Tayrona", "categoria": "Alojamiento",
  "idUnidad": "6b2c…", "nombreUnidad": "Suite familiar",
  "fechaEntrada": "2030-03-30", "fechaSalida": "2030-04-01", "horaInicio": null,
  "fechaInicio": "2030-03-30T20:00:00Z", "fechaFin": "2030-04-01T17:00:00Z",
  "cantidad": 1, "personas": 2, "precioUnitario": 260000, "unidadCobro": "Noche",
  "precioTotal": 520000, "moneda": "COP",
  "fechaCreacion": "2030-03-10T17:00:00Z", "fechaCancelacion": null,
  "cancelableHasta": "2030-03-29T20:00:00Z"
}
```

El nombre del servicio, el de la unidad y los precios quedan **congelados** (RF32): no cambian aunque cambie el catálogo.

**`cancelableHasta`** lo calcula ms-booking: `fechaInicio` menos el plazo de RF44 (24 h). Viaja en todas las respuestas de una reserva (reservar, `/mias`, `/{id}` y cancelar). El cliente no recalcula el plazo: lo usa para mostrar si todavía se puede cancelar, y el servidor decide igual al cancelar.

**Idempotencia.**
- Reenviar con la misma clave y el mismo pedido responde **`201` con la misma reserva** y la cabecera `Idempotent-Replayed: true`, sin descontar otra vez.
- La misma clave con otro pedido (incluida otra unidad) responde `422 IDEMPOTENCIA_CONFLICTO`.
- La clave es de cada turista. Un intento que falla (por ejemplo, `CUPO_AGOTADO`) no la guarda.
- **Regla para el frontend:** generar una clave nueva por cada intención de reservar y reutilizarla en los reintentos de esa misma intención.

## Mis reservas

`GET /api/v1/reservas/mias?desde=&hasta=&estado=&pagina=0` · rol Turista → `{elementos, pagina, tamano: 20, totalElementos, totalPaginas}`. RF12, del lado del turista.

- **`desde` y `hasta`** (opcionales): fechas de Santa Marta (`aaaa-mm-dd`), inclusivas, sobre la fecha de la **actividad**. Una reserva aparece si `fechaInicio < (hasta + 1 día) 00:00` y `fechaFin > desde 00:00`, en `America/Bogota`: una estadía del 30 de noviembre al 2 de diciembre aparece al filtrar diciembre, y una actividad que termina exactamente a las 00:00 de `desde`, no. Con uno solo, el rango queda abierto del otro lado.
- **`estado`** (opcional): uno o varios de `Confirmada`, `Cancelada` y `Completada`, separados por coma.
- **Orden:** `fechaInicio` descendente, con `idReserva` como desempate (la paginación es estable).
- **Errores (`DATOS_INVALIDOS`):** `hasta` anterior a `desde` → `hasta: RANGO_INVALIDO`; más de 366 días contando los dos extremos → `hasta: MAXIMO_DIAS`; una fecha mal escrita → `FORMATO`; un estado desconocido → `estado: NO_PERMITIDO`.

`GET /api/v1/reservas/{idReserva}` · rol Turista → la reserva. La de otro turista responde 404, igual que una que no existe.

## Reservas del Prestador

`GET /api/v1/reservas/prestador?desde=&hasta=&estado=&servicio=&pagina=0` · rol **Prestador** → `{elementos, pagina, tamano: 20, totalElementos, totalPaginas}`. CU [«Consultar historial»](../../casos-de-uso/CU-consultar-historial.md), RF12 del lado del prestador (DEC-191, DEC-192).

- **Qué reservas:** las de `id_prestador = sub`. ms-booking copia el dueño del servicio del detalle del catálogo al reservar (V1); no llama al catálogo para consultar.
- **`desde`, `hasta` y `estado`:** las mismas reglas y errores que «Mis reservas».
- **`servicio`** (opcional): el id de un servicio (ObjectId en texto). Otro formato → `servicio: FORMATO`. Un servicio de otro prestador no da error: no tiene reservas suyas.
- **Orden:** el de «Mis reservas» (índice `ix_reserva_prestador_inicio`, V6).
- **Cada elemento:** `{idReserva, estado, idServicio, nombreServicio, categoria, idUnidad, nombreUnidad, fechaEntrada, fechaSalida, horaInicio, fechaInicio, fechaFin, cantidad, personas, precioUnitario, unidadCobro, precioTotal, moneda, fechaCreacion, fechaCancelacion}`. **Sin datos del Turista** (Ley 1581; los contactos solo salen en «Asistentes», RF42) y sin `cancelableHasta`: el Prestador no cancela.
- Un Turista → **403 `ACCESO_DENEGADO`**; el Prestador en las rutas del Turista, también.

## Oferta del Prestador (RF19)

Rol **Prestador**, dueño del servicio (Tour o Alojamiento). CU [«Modificar oferta»](../../casos-de-uso/CU-modificar-oferta.md); reglas de la decisión 3 del equipo (DEC-310, **decisión del equipo, no del SRS**). Antes de cada operación ms-booking lee el detalle público del catálogo (como al reservar, D4) y materializa la ventana si falta: un servicio ajeno, inexistente o no visible → **409 `OFERTA_NO_DISPONIBLE`**; Gastronomía o Alquiler → 400 `campos.idServicio = CATEGORIA_NO_RESERVABLE`; catálogo caído y falta materializar → 503.

- **Consultar:** `GET /api/v1/reservas/prestador/servicios/{id}/inventario?desde=&hasta=` → `{idServicio, nombre, categoria, desde, hasta, diasOperacion, salidas, filas}`. Por defecto, de mañana a 14 días; `hasta` se recorta al horizonte de 60 días; `hasta` antes de `desde` → `campos.hasta = RANGO_INVALIDO`. Cada fila: `{categoria, idUnidad, fecha, horaInicio, cuposTotales, cuposDisponibles, reservados, cerrado}` (`reservados = cuposTotales − cuposDisponibles`).
- **Ajustar, abrir o agregar:** `PUT …/servicios/{id}/inventario` `{fecha, horaInicio, idUnidad, cuposTotales}` → **200** con la fila. `fecha` desde mañana en Santa Marta y dentro del horizonte (`FECHA_PASADA`, `MAXIMO_DIAS`); `horaInicio` `HH:mm` obligatoria en Tour; `idUnidad` de una unidad del alojamiento (`NO_PERMITIDO` si no es suya); `cuposTotales` de 1 a 10 000. Si la fila existe, fija los cupos totales y conserva los reservados (UPDATE condicional): por debajo de lo reservado → **409 `CUPOS_RESERVADOS`** con `reservados`; si estaba cerrada, la abre. Si no existe, la crea (una salida o una fecha nueva).
- **Cerrar:** `DELETE …/servicios/{id}/inventario?fecha=&horaInicio=&idUnidad=` → **200** con la fila (`cerrado: true`). No borra la fila: la reserva, la disponibilidad y el resumen dejan de verla. Con reservas Confirmadas → **409 `FECHA_CON_RESERVAS`**; fila inexistente → `FECHA_NO_DISPONIBLE`.
- **Plantilla:** `PUT …/servicios/{id}/plantilla` `{diasOperacion, salidas}` → **202** sin cuerpo. De 1 a 7 días (`Lunes` … `Domingo`, sin tildes) y, en Tour, de 1 a 10 salidas `HH:mm` (se ordenan y no se repiten); en Alojamiento, `salidas` se ignora. Se aplica a las fechas que todavía no están materializadas: ms-catalog fija la `oferta` del servicio cuando recibe el evento y la materialización siguiente la usa.
- Cada cambio encola **`OfertaModificada`** ([`eventos.md`](eventos.md)) en la misma transacción (outbox V8, `clave` = id del servicio); ms-catalog guarda el historial (RF41).

## Asistentes (RF42)

`GET /api/v1/reservas/prestador/asistentes?servicio=&fecha=&horaInicio=` · rol **Prestador** → `{idServicio, fecha, horaInicio, asistentes, totalPersonas}`. CU [«Exportar asistentes»](../../casos-de-uso/CU-exportar-asistentes.md) (decisión 7 del equipo, DEC-312).

- **Qué reservas:** las **Confirmadas** del Prestador del token para ese servicio: en Tour, las que empiezan en `fecha` (y en `horaInicio`, si viene); en Alojamiento, las estadías con `fechaEntrada ≤ fecha < fechaSalida`. Ordenadas por salida y nombre. Un servicio ajeno da una lista vacía.
- **Cada asistente:** `{idReserva, nombre, correo, telefono, personas, horaInicio, nombreUnidad, fechaEntrada, fechaSalida}`. El contacto lo entrega ms-auth (`POST /internal/v1/tokens` con el cliente `ms-booking` y `POST /internal/v1/contactos`, lotes de 100; `AUTH_INTERNO_URI`, `SERVICIO_BOOKING_SECRETO`); no se guarda. Una cuenta sin contacto llega con `nombre`, `correo` y `telefono` nulos.
- `servicio` y `fecha` obligatorios (`REQUERIDO`, `FORMATO`). ms-auth caído → **503 `SERVICIO_NO_DISPONIBLE`** con `servicio: ms-auth`.
- La exportación a CSV o PDF la hace el navegador (WF-40).

## Cancelar

`POST /api/v1/reservas/{idReserva}/cancelacion` · rol Turista → `200` con la reserva en estado `Cancelada`.

- **RF44:** se puede cancelar si faltan **24 horas o más** para el inicio (`cancelableHasta`, límite incluido). Si no, `409 CANCELACION_FUERA_DE_PLAZO`.
- Cancelar una reserva ya Cancelada responde `200` con ella y no devuelve cupos otra vez.
- Una reserva Completada no se cancela: `409 RESERVA_NO_CANCELABLE`. También si RF52 la completa mientras se procesa la cancelación: gana uno de los dos y los cupos no cambian.
- Los cupos se devuelven a las mismas noches o al mismo horario que se descontaron.

## Disponibilidad (pública)

`GET /api/v1/reservas/disponibilidad?servicio=&unidad=&desde=&hasta=`
- `servicio` obligatorio; `unidad` opcional (solo alojamiento);
- por defecto, desde hoy y 30 días; como máximo 60 días.

**Inventario bajo demanda** (DEC-135, DEC-152 a DEC-155; enmienda A2 de [ADR-007](../adr/ADR-007-reservas-y-catalogo.md)). Los cupos salen de la **oferta** que el servicio define en ms-catalog (`oferta` del detalle: `diasOperacion` y, en un tour, `salidas`):
- Antes de leer, ms-booking asegura que la oferta esté materializada hasta `hasta` (recortado al horizonte). Si no lo está, consulta el detalle de ms-catalog y crea las filas que falten de **toda la ventana**, de hoy a hoy + 59 en Santa Marta: una por salida de cada día de operación (tour, con `aforoMaxGrupo` cupos) o una por unidad y noche de operación (alojamiento, con `numHabitaciones`). Las filas que ya existen no cambian.
- **Un día sin filas** dentro de la ventana es un día que no opera (o una salida que no se ofrece); después de la ventana, no hay inventario. En los dos casos la respuesta es la misma: sin filas.
- Con los datos inválidos (por ejemplo, un `servicio` que no es un ObjectId) responde 400 `DATOS_INVALIDOS` sin consultar ms-catalog.
- Un servicio que ms-catalog no muestra (404) no se materializa: la respuesta trae lo que ya existía (sin filas, `categoria: null`).
- **503 `SERVICIO_NO_DISPONIBLE`** (`servicio: ms-catalog`) si hay que materializar y ms-catalog no responde. Si el rango ya está materializado, responde sin consultar ms-catalog.

```json
{
  "idServicio": "6a0f…", "desde": "2030-03-30", "hasta": "2030-03-31", "categoria": "Alojamiento",
  "unidades": [
    { "idUnidad": "6b2c…", "noches": [ { "fecha": "2030-03-30", "cuposTotales": 3, "cuposDisponibles": 1 } ] }
  ],
  "horarios": [],
  "horaCheckIn": "15:00:00", "horaCheckOut": "12:00:00", "plazoCancelacionHoras": 24
}
```

`horaCheckIn` y `horaCheckOut` son el horario de los alojamientos y `plazoCancelacionHoras`, el plazo de RF44. Salen de la configuración de ms-booking (`samaria360.reservas.hora-check-in` y `hora-check-out`) y van siempre, aunque no haya inventario en el rango: la pantalla los muestra tal cual, sin escribirlos por su cuenta.

En un tour, `unidades` va vacío y `horarios` trae `{fecha, hora, cuposTotales, cuposDisponibles}`. Sin inventario en el rango, `categoria` es `null` y las dos listas van vacías. **No trae nombres ni precios:** el cliente los toma del detalle de ms-catalog (`GET /api/v1/catalogo/servicios/{id}`).

`GET /api/v1/reservas/disponibilidad/resumen?servicios=id1,id2&desde=&hasta=` → `{"id1": "CON_CUPOS", "id2": "SIN_INVENTARIO"}`, en el orden pedido.
- Hasta 10 ids, cada uno un ObjectId; por defecto, hoy y los 7 días siguientes.
- `CON_CUPOS`: alguna unidad u horario tiene cupos en algún día del rango; `AGOTADO`: hay inventario, pero todo en 0; `SIN_INVENTARIO`: no hay filas (no opera en esas fechas, no es reservable o ms-catalog no lo muestra).
- Materializa como la disponibilidad, servicio por servicio, con un **plazo total de 2,5 s** (DEC-155). Si ms-catalog no responde o el plazo se agota con servicios sin materializar, **503 `SERVICIO_NO_DISPONIBLE`** para toda la respuesta: nunca un `SIN_INVENTARIO` falso.

## Completar reservas (RF52)

Una tarea de ms-booking pasa de `Confirmada` a `Completada` las reservas cuya `fechaFin` ya pasó, con un UPDATE condicional idempotente. Corre al arrancar y después cada 15 minutos (`samaria360.completar-reservas.cada`): una reserva queda Completada a más tardar 15 minutos después de terminar. No tiene endpoint.

## Endpoint interno de cuentas (ADR-008)

`PUT /internal/v1/cuentas/{id}` · no pasa por el API Gateway · solo un JWT de servicio de ms-auth con `aud = ms-booking`. Guarda la proyección de la cuenta (tabla `cuenta`, V4) con un *upsert* que solo avanza si la versión es mayor; responde 204 se aplique o no. Contrato completo en [`ms-auth.md`](ms-auth.md). Consultar, cancelar y la disponibilidad no la consultan: solo el validador del JWT.

## Códigos de error

| Código | HTTP | Cuándo |
|---|---|---|
| `DATOS_INVALIDOS` | 400 | `campos` con: `REQUERIDO`, `FORMATO`, `VALOR_MINIMO`, `NO_PERMITIDO`, `FECHA_PASADA`, `RANGO_INVALIDO`, `MAXIMO_NOCHES`, `MAXIMO_DIAS`, `MAXIMO_ELEMENTOS`, `EXCEDE_CAPACIDAD`, `EXCEDE_AFORO`, `CATEGORIA_NO_RESERVABLE`. `idUnidad` es `REQUERIDO` en un alojamiento y `NO_PERMITIDO` en un tour. Un cuerpo que no es JSON también es `DATOS_INVALIDOS`, sin `campos` |
| `IDEMPOTENCIA_REQUERIDA` | 400 | Falta `Idempotency-Key` o no es un UUID |
| `NO_AUTENTICADO` | 401 | Sin token o con uno inválido |
| `SESION_INVALIDADA` | 401 | El token es de antes de un cambio de estado de la cuenta (`ver` menor que la versión conocida, DEC-29): hay que iniciar sesión de nuevo |
| `ACCESO_DENEGADO` | 403 | El rol no es Turista, o no es Prestador en `/reservas/prestador` |
| `RESERVA_NO_ENCONTRADA` | 404 | No existe o es de otro turista |
| `OFERTA_NO_DISPONIBLE` | 409 | El servicio no existe, no está Publicado o el prestador está inactivo |
| `UNIDAD_NO_DISPONIBLE` | 409 | El alojamiento no tiene esa unidad: hay que volver a cargar el detalle |
| `FECHA_NO_DISPONIBLE` | 409 | No hay inventario publicado para alguna noche o para el horario: el servicio no opera ese día o no ofrece esa salida, o la fecha es posterior a la ventana de 60 días |
| `CUPO_AGOTADO` | 409 | Hay inventario, pero no alcanza (RF11); no se descuenta nada |
| `PRECIO_CAMBIADO` | 409 | Lleva `precioTotal`, `precioUnitario` y `moneda` vigentes (RF37) |
| `CANCELACION_FUERA_DE_PLAZO` | 409 | Faltan menos de 24 horas (RF44) |
| `RESERVA_NO_CANCELABLE` | 409 | La reserva está Completada (también si RF52 la completó mientras se cancelaba) |
| `CUPOS_RESERVADOS` | 409 | RF19: los cupos nuevos quedan por debajo de los reservados; lleva `reservados` (DEC-310) |
| `FECHA_CON_RESERVAS` | 409 | RF19: la fecha o salida que se quiere cerrar tiene reservas Confirmadas (DEC-310) |
| `IDEMPOTENCIA_CONFLICTO` | 422 | La misma clave con otro pedido |
| `SERVICIO_NO_DISPONIBLE` | 503 | ms-auth no responde al pedir los contactos de RF42 (`servicio: ms-auth`); ms-catalog no responde (`servicio: ms-catalog`) al reservar o cuando la disponibilidad o el resumen deben materializar la oferta (también si el resumen agota su plazo de 2,5 s); o, desde el gateway, ms-booking caído (`servicio: ms-booking`) |
| `ERROR_INTERNO` | 500 | Cualquier otro error |
