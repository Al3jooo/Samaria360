# Catálogo de eventos (RabbitMQ)

Contrato de los eventos asíncronos del nivel 3 de [ADR-003](../adr/ADR-003-estrategia-conectividad.md): topología, sobre, eventos y consumidores. Mecanismo y garantías en [ADR-011](../adr/ADR-011-mensajeria-rabbitmq.md); esquema verificable en [`eventos.schema.json`](eventos.schema.json) (JSON Schema 2020-12). Este documento se publica **antes** del código (decisión 8 del equipo): cada iteración que agrega un evento lo agrega aquí primero, con su esquema.

## Topología

| Elemento | Nombre | Notas |
|---|---|---|
| Virtual host | `samaria360` | — |
| Exchange | `samaria360.eventos.v1` | topic, durable |
| Exchange de mensajes fallidos | `samaria360.eventos.v1.dlx` | topic, durable |
| Cola de ms-analytics | `ms-analytics.eventos.v1` | durable; enlaces: `auth.#`, `booking.reserva.*`, `catalog.servicio.decidido`, `catalog.ficha.*`, `catalog.atractivo.*` |
| Cola de ms-catalog | `ms-catalog.eventos.v1` | durable; enlaces: `analytics.calificacion.*` y, desde la iteración I, `booking.oferta.*` |
| Cola de ms-chatbot | `ms-chatbot.eventos.v1` | durable; enlace: `auth.cuenta.actualizada` (DEC-280) |
| Colas de mensajes fallidos | `<cola>.dlq` | una por cola; `x-dead-letter-routing-key: <cola>` |

Clave de enrutamiento: `<origen>.<agregado>.<suceso>`, en minúsculas y con guiones dentro de cada parte.

## Sobre

Todos los eventos comparten el sobre; `datos` depende de `tipo`.

```json
{
  "idEvento": "0b6f3c1e-8f0e-4b7a-9a52-2c1d7f4e9a10",
  "tipo": "ReservaConfirmada",
  "version": 1,
  "origen": "ms-booking",
  "ocurridoEn": "2030-03-01T15:04:05Z",
  "clave": "7d3c1f0a-1b2c-4d5e-8f90-a1b2c3d4e5f6",
  "datos": { }
}
```

- `idEvento`: UUID que asigna el productor al escribir el outbox; los consumidores deduplican con él. También va en la propiedad AMQP `messageId`.
- `tipo`: nombre del evento (tabla de abajo); también en la propiedad `type`. `version`: entero desde 1 (ADR-011, punto 8).
- `origen`: `ms-auth`, `ms-booking`, `ms-catalog` o `ms-analytics`. `ocurridoEn`: instante UTC del cambio de negocio (la hora sale de `RelojPort`).
- `clave`: id del agregado (la cuenta, la reserva, el servicio o la entidad calificada).
- **Prohibido en cualquier evento** (ADR-011, punto 7): nombres, correos, teléfonos, documentos, coordenadas de un usuario y texto libre escrito por usuarios.

## Eventos

| Clave de enrutamiento | `tipo` | Productor | `clave` | Consumidores (RF) | Iteración |
|---|---|---|---|---|---|
| `auth.cuenta.actualizada` | `CuentaActualizada` | ms-auth | `idUsuario` | ms-analytics (validador de `ver`; visitantes por país, RF14); ms-chatbot (validador de `ver`) | G |
| `auth.prestador.validado` | `PrestadorValidado` | ms-auth | `idPrestador` | ms-analytics (auditoría, RF13) | G |
| `auth.cuenta.rol-cambiado` | `RolCambiado` | ms-auth | `idUsuario` | ms-analytics (auditoría, RF13) | G |
| `booking.reserva.confirmada` | `ReservaConfirmada` | ms-booking | `idReserva` | ms-analytics (RF30, RF63, RF14, RF31) | G |
| `booking.reserva.cancelada` | `ReservaCancelada` | ms-booking | `idReserva` | ms-analytics (RF13, RF63, RF14, RF31) | G |
| `booking.reserva.completada` | `ReservaCompletada` | ms-booking | `idReserva` | ms-analytics (RF63; habilita RF17) | G |
| `catalog.servicio.decidido` | `ServicioDecidido` | ms-catalog | `idServicio` | ms-analytics (auditoría, RF13) | G |
| `catalog.ficha.consultas-agregadas` | `ConsultasAgregadas` | ms-catalog | inicio de la ventana | ms-analytics (RF31, RF14) | K |
| `analytics.calificacion.promedio-actualizado` | `PromedioActualizado` | ms-analytics | `idEntidad` | ms-catalog (RF61; promedio en la ficha, RF17) | H |
| `catalog.atractivo.ocupacion-registrada` | `OcupacionRegistrada` | ms-catalog | `idAtractivo` | ms-analytics (historial de afluencia para RF64) | J |
| `booking.oferta.modificada` | `OfertaModificada` | ms-booking | `idServicio` | ms-catalog (historial de RF41; oferta de la plantilla, RF19) | I |

**Previstos:** ninguno; el catálogo de eventos del plan del 100 % está completo.

### CuentaActualizada

Cambio de rol, estado o versión de una cuenta: los mismos casos que el relevo HTTP de ADR-008 (que **sigue** entregando su propio cuerpo a ms-catalog y ms-booking). Sin `rnt` (solo lo usa ms-catalog, que lo recibe por HTTP).

```json
{ "idUsuario": "7d3c1f0a-1b2c-4d5e-8f90-a1b2c3d4e5f6", "rol": "Turista", "estadoCuenta": "Activo", "version": 3, "pais": "CO" }
```

- `rol`: `Turista`, `Prestador`, `Gestor` o `Administrador`. `estadoCuenta`: `Pendiente`, `Activo`, `Rechazado`, `Inactivo` o `Desactivado`. `version`: `usuario.version_cuenta`: 0 en la cuenta recién registrada (RF01, RF02; solo RabbitMQ) y desde 1 en cada cambio de estado, de rol o de contraseña (RF34).
- `pais`: solo si `rol = Turista` (`turista.pais_origen`, ISO 3166-1 alfa-2; decisión 6 del equipo). Una cuenta de personal sin fila en `turista` no lo lleva.
- El consumidor aplica el evento solo si `version` es mayor que la guardada.

### PrestadorValidado

Decisión del Administrador sobre un prestador (RF28). La justificación del rechazo **no viaja** (texto libre).

```json
{ "idPrestador": "…", "decision": "Rechazado", "idAdministrador": "…" }
```

`decision`: `Aprobado` o `Rechazado`.

### RolCambiado

Cambio de rol (RF45).

```json
{ "idUsuario": "…", "rolAnterior": "Turista", "rolNuevo": "Gestor", "idAdministrador": "…" }
```

### ReservaConfirmada

Reserva creada y confirmada automáticamente (RF20). Lo que RF30 pide mostrar: la actividad (`nombreServicio`), la fecha (`fechaInicio`) y la cantidad de personas (`personas`).

```json
{
  "idReserva": "…", "idTurista": "…", "idPrestador": "…",
  "idServicio": "65f0c0ffee0000000000000a", "nombreServicio": "Tour a Playa Cristal",
  "categoria": "Tour", "idUnidad": null,
  "fechaInicio": "2030-03-10T13:00:00Z", "fechaFin": "2030-03-10T18:00:00Z",
  "personas": 4, "cantidad": 4
}
```

- `categoria`: `Tour` o `Alojamiento`. `idUnidad`: el ObjectId de la unidad en Alojamiento; `null` en Tour. `cantidad`: cupos descontados (`cantidad_solicitada`).
- `nombreServicio`, en todos los eventos que lo llevan: de 1 a 150 caracteres, el largo del nombre comercial (DEC-323; antes decía 120).

### ReservaCancelada

Cancelación de una reserva Confirmada (RF18). `canceladaPor` es el usuario que hizo la acción (la auditoría de RF13 la registra con su fecha).

```json
{ "idReserva": "…", "idTurista": "…", "idPrestador": "…", "idServicio": "…", "nombreServicio": "…", "fechaInicio": "2030-03-10T13:00:00Z", "personas": 4, "canceladaPor": "…" }
```

### ReservaCompletada

Paso automático de Confirmada a Completada (RF52). Con él, ms-analytics habilita la calificación del servicio (RF17, decisión 1 del equipo).

```json
{ "idReserva": "…", "idTurista": "…", "idPrestador": "…", "idServicio": "…", "nombreServicio": "…", "fechaFin": "2030-03-10T18:00:00Z" }
```

### ServicioDecidido

Aprobación o rechazo de un servicio (RF21). La justificación **no viaja**.

```json
{ "idServicio": "…", "nombreServicio": "Tour a Playa Cristal", "idPrestador": "…", "decision": "Publicado", "idAdministrador": "…" }
```

`decision`: `Publicado` o `Rechazado`. `nombreServicio` (opcional, agregado en DEC-323): el nombre comercial, para que la auditoría lo muestre; no es un dato personal.

### ConsultasAgregadas

Consultas a las fichas públicas (detalle de un servicio o de un atractivo) contadas en una ventana de un minuto (DEC-283). Sin datos de quien consulta.

```json
{
  "ventanaInicio": "2030-03-01T15:04:00Z", "ventanaFin": "2030-03-01T15:05:00Z",
  "conteos": [
    { "tipoEntidad": "servicio", "idEntidad": "…", "nombre": "Tour Tayrona", "idPrestador": "…", "consultas": 12 },
    { "tipoEntidad": "atractivo", "idEntidad": "…", "nombre": "Playa Cristal", "consultas": 30 }
  ]
}
```

- De 1 a 1000 conteos; si hay más entidades en la ventana, se publican varios eventos con la misma ventana. `idPrestador`, solo en `servicio`. `nombre` (opcional, agregado en la iteración K, DEC-307): el nombre de la ficha, para que ms-analytics arme RF14 y RF31 sin leer catalog_db. `consultas`: entero desde 1. Solo se cuentan las consultas que llegan por el gateway (con `X-Forwarded-For`): las de ms-booking a la red interna no son visitas.

### PromedioActualizado

Promedio de las calificaciones **visibles** de un servicio o atractivo (decisión 1 del equipo), después de calificar, ocultar, volver a mostrar o eliminar.

```json
{ "tipoEntidad": "servicio", "idEntidad": "…", "promedio": 4.33, "cantidad": 3, "version": 7 }
```

- `promedio`: de 1 a 5 con dos decimales; `null` si `cantidad = 0`. `version`: monótona por entidad; ms-catalog aplica el evento solo si es mayor que la guardada.

### OcupacionRegistrada

Ocupación de un atractivo que registra el Gestor (RF39; decisión 2 del equipo, DEC-304). `ocurridoEn` es el momento del registro. Sin datos personales: ni el Gestor que la registró.

```json
{ "idAtractivo": "…", "nombreAtractivo": "Playa Cristal", "categoria": "Naturaleza", "ocupacion": 240, "limiteCapacidadCarga": 300, "porcentaje": 80, "nivel": "Medio" }
```

- `porcentaje`: `ocupacion / limiteCapacidadCarga`, redondeado (puede pasar de 100). `nivel`: `Bajo` por debajo de 40, `Medio` de 40 a 80, `Alto` por encima de 80.

### OfertaModificada

Un cambio del Prestador en su oferta (RF19; decisión 3 del equipo, DEC-310), hecho en ms-booking con su validación atómica. Sin datos personales.

```json
{ "idServicio": "…", "idPrestador": "…", "tipo": "Cupos", "fecha": "2030-03-11", "horaInicio": "08:00", "valorAnterior": "10", "valorNuevo": "5" }
```

- `tipo`: `Cupos` (cupos de una fecha u horario), `Apertura` (una fecha u horario que no estaba o estaba cerrado; `valorAnterior` es `Cerrado` o no viene), `Cierre` (`valorNuevo` = `Cerrado`) o `Plantilla` (días de operación y salidas de las fechas que faltan materializar, con `diasOperacion` y `salidas`, vacías en un alojamiento).
- `fecha` en las tres primeras; `horaInicio` en un tour; `idUnidad` en un alojamiento.
- ms-catalog guarda cada uno en el historial de RF41 (deduplicado por `idEvento`) y, con `Plantilla`, actualiza la `oferta` del servicio.
