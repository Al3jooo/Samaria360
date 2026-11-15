# API de ms-analytics (análisis y notificaciones)

Contenedor «Análisis y notificaciones» del Entregable 2 (tabla 1, figura 20). Recibe los eventos de RabbitMQ ([ADR-011](../adr/ADR-011-mensajeria-rabbitmq.md), [`eventos.md`](eventos.md)), los guarda en `analytics_db` (MongoDB) y expone la API por el gateway (`/api/v1/analytics/**`); escucha en el 8084 solo dentro de la red de Docker. Decisiones en [`trazabilidad/decisiones-plan-100-iteracion-g.md`](../../trazabilidad/decisiones-plan-100-iteracion-g.md).

## Convenciones

- **Autenticación:** `Authorization: Bearer <JWT de ms-auth>`, validado con el JWKS (ADR-005) y el claim `ver` contra la proyección de cuentas de `analytics_db`, alimentada por `CuentaActualizada` (DEC-110: sin fila, un Gestor o un Administrador recibe 401 `SESION_INVALIDADA`). Toda la API exige un token, salvo `GET /api/v1/analytics/calificaciones` (público, RF17; un Bearer que llegue igual se valida).
- **Errores:** ProblemDetail (RFC 9457) con `codigo`, como los demás servicios: `NO_AUTENTICADO` y `SESION_INVALIDADA` (401), `ACCESO_DENEGADO` (403), `DATOS_INVALIDOS` (400, con `campos`), `SIN_RESERVA_COMPLETADA` y `CUENTA_INACTIVA` (403), `NOTIFICACION_NO_ENCONTRADA`, `CALIFICACION_NO_ENCONTRADA` y `REPORTE_NO_ENCONTRADO` (404), `CALIFICACION_DUPLICADA`, `REPORTE_DUPLICADO` y `ESTADO_CAMBIADO` (409, con `estadoActual`), `ERROR_INTERNO` (500).
- **Fechas:** instantes ISO 8601 en UTC; los filtros de día (`yyyy-MM-dd`) son días de Santa Marta (`America/Bogota`).

## Notificaciones (RF63)

Solo el **Turista** (cambios de estado de sus reservas) y el **Prestador** (reservas nuevas). El usuario es siempre el `sub` del token.

`GET /api/v1/analytics/notificaciones?pagina=0` → **200**

```json
{
  "elementos": [
    {
      "id": "6a1f…", "tipo": "NUEVA_RESERVA", "mensaje": "Nueva reserva de «Tour a Playa Cristal» para el 2030-03-10: 4 personas.",
      "datos": { "idReserva": "…", "idServicio": "…", "nombreServicio": "Tour a Playa Cristal", "fecha": "2030-03-10", "personas": "4" },
      "leida": false, "fecha": "2030-03-01T15:00:00Z"
    }
  ],
  "pagina": 0, "tamano": 20, "totalElementos": 1, "noLeidas": 1
}
```

- Páginas de 20, de la más reciente a la más antigua. `tipo`: `RESERVA_CONFIRMADA`, `RESERVA_CANCELADA`, `RESERVA_COMPLETADA` (al Turista) o `NUEVA_RESERVA` (al Prestador). `mensaje` es el respaldo en español; la interfaz arma el texto con `tipo` y `datos` en el idioma activo (RF03). El Prestador no recibe datos del Turista.
- `POST /api/v1/analytics/notificaciones/{id}/lectura` → **204**; una notificación ajena o inexistente → **404** `NOTIFICACION_NO_ENCONTRADA`.
- `POST /api/v1/analytics/notificaciones/lectura` → **200** `{"marcadas": n}`: todas las del usuario.

## Auditoría (RF13)

Solo el **Administrador**.

`GET /api/v1/analytics/admin/auditoria?accion=&usuario=&desde=&hasta=&pagina=0` → **200**

```json
{
  "elementos": [
    { "usuarioId": "00000000-0000-4000-8000-000000000004", "accion": "PRESTADOR_APROBADO",
      "detalle": "Prestador 00000000-0000-4000-8000-000000000002", "fecha": "2030-03-01T15:00:00Z" }
  ],
  "pagina": 0, "tamano": 20, "totalElementos": 1
}
```

- `usuarioId`: quien hizo la acción. `accion`: `PRESTADOR_APROBADO`, `PRESTADOR_RECHAZADO`, `SERVICIO_APROBADO`, `SERVICIO_RECHAZADO`, `ROL_CAMBIADO`, `RESERVA_CANCELADA` y, desde la iteración H, las de moderación de calificaciones. `detalle`: los identificadores afectados, sin texto libre ni datos personales.
- Filtros opcionales: `accion`, `usuario` (UUID), `desde` y `hasta` (`yyyy-MM-dd`, `hasta` incluido). **400** `DATOS_INVALIDOS` con `campos` (`NO_PERMITIDO`, `FORMATO`) si uno no tiene forma o `hasta` es anterior a `desde`. Páginas de 20, de la más reciente a la más antigua.

## Calificaciones (RF17, RF29, RF56)

Decisión 1 del equipo y DEC-300. Una calificación por turista y entidad, sin edición; el promedio cuenta solo las visibles y cada cambio publica `PromedioActualizado` ([`eventos.md`](eventos.md)) para ms-catalog (RF61).

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `GET /api/v1/analytics/calificaciones?tipo=servicio&id=…&pagina=0` | Público | **200** `{promedio, cantidad, elementos: [{id, estrellas, comentario, fecha}], pagina, tamano, totalElementos}`: las visibles, de la más reciente a la más antigua, de 10 en 10; sin el id del turista. `promedio` con dos decimales, `null` sin calificaciones |
| `GET /api/v1/analytics/calificaciones/elegibilidad?tipo=…&id=…` | Turista | **200** `{puedeCalificar, motivo, propia}`; `motivo`: `SIN_RESERVA_COMPLETADA`, `CALIFICACION_DUPLICADA` o `CUENTA_INACTIVA` |
| `POST /api/v1/analytics/calificaciones` `{tipoEntidad, idEntidad, estrellas, comentario}` | Turista | **201** `{id, tipoEntidad, idEntidad, estrellas, comentario, fecha}`. 403 `SIN_RESERVA_COMPLETADA` (un servicio sin una reserva Completada del turista) o `CUENTA_INACTIVA`; 409 `CALIFICACION_DUPLICADA` |
| `GET /api/v1/analytics/prestador/calificaciones?servicio=&desde=&hasta=&pagina=0` | Prestador | **200** `{promedio, cantidad, servicios: [{idServicio, nombre, promedio, cantidad}], elementos: [{id, idServicio, nombreServicio, estrellas, comentario, fecha}], pagina, tamano, totalElementos}`: las visibles de sus servicios, de 20 en 20, sin datos del turista. `desde` y `hasta` son días de Santa Marta (`hasta` incluido) |
| `GET /api/v1/analytics/admin/calificaciones?estado=&pagina=0` | Administrador | **200** página de 20 con `{id, tipoEntidad, idEntidad, nombreEntidad, turistaId, estrellas, comentario, estado, motivoModeracion, fecha, fechaModeracion}`; `estado` opcional: `Visible` u `Oculta` |
| `POST /api/v1/analytics/admin/calificaciones/{id}/ocultamiento` `{motivo}` | Administrador | **200** la calificación `Oculta`; si ya lo estaba, 409 `ESTADO_CAMBIADO` |
| `POST /api/v1/analytics/admin/calificaciones/{id}/visibilidad` `{motivo}` | Administrador | **200** la calificación `Visible` de nuevo; si ya lo estaba, 409 `ESTADO_CAMBIADO` |
| `POST /api/v1/analytics/admin/calificaciones/{id}/eliminacion` `{motivo}` | Administrador | **204**: definitivo |

- `tipoEntidad`: `servicio` o `atractivo`; `idEntidad`: un ObjectId. `estrellas` de 1 a 5; `comentario` de 0 a 500; `motivo` de 1 a 500 (recortados, en puntos de código).
- Un servicio se califica solo con una reserva **Completada** del turista (la proyección `reservas`); un atractivo, cualquier turista con la cuenta **Activa**.
- Cada moderación queda en `logs_auditoria` (`CALIFICACION_OCULTADA`, `CALIFICACION_MOSTRADA`, `CALIFICACION_ELIMINADA`) con el Administrador, la fecha y el motivo (RF29).

## Reportes (RF57)

DEC-301. `POST /api/v1/analytics/reportes` (Turista) `{tipoEntidad, idEntidad, motivo, descripcion}` → **201** `{id, estado: "Pendiente", fecha}`. `motivo`: `INFORMACION_FALSA`, `INFORMACION_DESACTUALIZADA`, `PRECIO_INCORRECTO`, `UBICACION_INCORRECTA`, `FOTOS_NO_CORRESPONDEN` u `OTRO`; `descripcion` opcional, hasta 500. Uno pendiente por turista y entidad (409 `REPORTE_DUPLICADO`).

Panel del Administrador: `GET /api/v1/analytics/admin/reportes?estado=Pendiente&pagina=0` (del más antiguo al más reciente, de 20 en 20; `{id, tipoEntidad, idEntidad, turistaId, motivo, descripcion, estado, fecha, revisadoPor, fechaRevision}`) y `POST /api/v1/analytics/admin/reportes/{id}/revision` → **200** el reporte `Revisado`; si ya lo estaba, 409 `ESTADO_CAMBIADO`.

## Eventos que publica

`PromedioActualizado` (`analytics.calificacion.promedio-actualizado`, clave `idEntidad`): outbox `outbox_eventos` escrito en la transacción de la calificación y relevo con *publisher confirms* y `mandatory`, con el usuario `ms-analytics` (ADR-011). Lo consume ms-catalog.

## Tableros (RF14, RF31, RF64)

DEC-307 y DEC-308. Las fechas son días de Santa Marta (`yyyy-MM-dd`, `hasta` incluido); sin `desde` ni `hasta`, los últimos 30 días hasta hoy; como máximo 366 días (400 `DATOS_INVALIDOS` con `campos`).

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `GET /api/v1/analytics/tableros/general?desde=&hasta=` | Administrador y Gestor | **200** `{desde, hasta, reservasPorDia: [{dia, reservas}], totalReservas, atractivosMasConsultados: [{id, nombre, consultas}], visitantesPorZona: [{pais, turistas}]}`. Reservas por día de confirmación (aunque después se cancelen), con los días sin reservas en cero; top 3 de atractivos por consultas a su ficha; turistas registrados por país (`pais` null si no lo tiene) |
| `GET /api/v1/analytics/prestador/panel?dias=7` | Prestador | **200** `{dias, desde, hasta, servicios: [{idServicio, nombre, consultas, reservas}]}`: los servicios del token con consultas o reservas en los últimos `dias` (7 o 30; otro valor, 400) |
| `GET /api/v1/analytics/gestor/afluencia?desde=&hasta=&atractivo=` | Gestor | **200** `{desde, hasta, atractivo, atractivos: [{idAtractivo, nombre, lecturas, porcentajePromedio, porcentajeMaximo, ultimoNivel, ultimaLectura}], porDia: [{dia, porcentajePromedio, porcentajeMaximo, lecturas}], atractivosDisponibles: [{idAtractivo, nombre}]}` con las lecturas de `OcupacionRegistrada` |

## Correo de RF30 (sin endpoint)

Por cada `ReservaConfirmada`, ms-analytics encola un correo al Prestador (`correos_pendientes`) en la misma transacción que la notificación. Un programador lo envía por SMTP (Mailpit en desarrollo, Gmail en la demo) con la actividad, la fecha y las personas; el contacto se pide a ms-auth en el momento (`POST /internal/v1/contactos` con un JWT de servicio, DEC-280) y no se guarda. Si ms-auth o el SMTP fallan, reintenta con espera creciente hasta una hora.

## Eventos que consume

Cola `ms-analytics.eventos.v1` (contrato en [`eventos.md`](eventos.md)): `CuentaActualizada`, `PrestadorValidado`, `RolCambiado`, `ServicioDecidido`, `ReservaConfirmada`, `ReservaCancelada`, `ReservaCompletada`, `OcupacionRegistrada` (iteración J: se guarda en `ocupaciones` para RF64; DEC-304) y `ConsultasAgregadas` (iteración K: se suma en `consultas` por ficha y día; DEC-308). De a un mensaje, cada uno en una transacción de MongoDB con su marca en `eventos_procesados` (deduplicación por `idEvento`, TTL de 30 días). Un mensaje sin el sobre o sin los campos del contrato va a `ms-analytics.eventos.v1.dlq` sin reintentos; otro fallo se reintenta tres veces y después va a la `.dlq`. Un tipo desconocido se ignora (ADR-011, punto 8).

## Colecciones de `analytics_db`

Migraciones en `infra/mongo/analytics/` (A001 a A004), aplicadas por `mongo-migraciones` con el usuario propio `analytics_app` (readWrite solo en `analytics_db`). Esquemas en `docs/sad/modelo-nosql/analytics_db/`.

| Colección | Origen | Contenido |
|---|---|---|
| `logs_auditoria` | Entregable 2, §2.4 (+ `idEvento`, DEC-292) | RF13 |
| `notificaciones` | Entregable 2, §2.4 (+ `datos`, `idEvento`, DEC-292) | RF63 |
| `cuentas` | Técnica (DEC-292) | Proyección: validador de `ver` y país del turista (RF14) |
| `reservas` | Técnica (DEC-292) | Hechos de las reservas: RF14, RF31 y la elegibilidad de RF17 |
| `correos_pendientes` | Técnica (DEC-292) | Cola del correo de RF30 |
| `eventos_procesados` | Técnica (DEC-284) | Deduplicación, TTL de 30 días |
| `calificaciones` | Entregable 2, §2.4 (+ `estado`, `motivoModeracion`, `idPrestador`, `nombreEntidad`; DEC-300) | RF17, RF29, RF56 |
| `reportes` | Entregable 2, §2.4 (motivos de la respuesta 5; DEC-301) | RF57 |
| `promedios` | Técnica (A002) | Último promedio visible y versión por entidad |
| `outbox_eventos` | Técnica (A002) | `PromedioActualizado` para RabbitMQ |
| `ocupaciones` | Técnica (A003; DEC-304) | Historial de `OcupacionRegistrada` para RF64 |
| `consultas` | Técnica (A004; DEC-308) | Consultas por ficha y día: RF14 (top 3) y RF31 |
