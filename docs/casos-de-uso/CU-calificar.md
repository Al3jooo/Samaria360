# CU «Calificar servicio o atractivo»

Fase de Construcción (RUP), plan del 100 %, iteración H. Reglas que el SRS no fija: decisión 1 del equipo, DEC-300 y DEC-303.
- **Pantallas:** WF-06 (sección «Calificaciones y reseñas» y promedio del subtítulo), WF-05 (calificación de la tarjeta y orden «Calificación») y WF-11 («Calificar» en las reservas Completadas).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Calificaciones»; [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), sección «Calificaciones».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Calificar servicio o atractivo» (con «Ordenar por calificación») |
| **Actores** | Turista; ms-booking (la reserva Completada llega por evento); ms-catalog (recibe el promedio) |
| **Requisitos que traza** | RF17, RF61 |
| **Alcance** | ms-analytics (`calificaciones`, `promedios`), ms-catalog (`calificacion` de `servicios` y `atractivos`) |

## Reglas de negocio

- **RF17 (SRS):** de 1 a 5 estrellas y un comentario, solo con la reserva «Completada».
- **Decisión 1 del equipo:** un servicio, solo con una reserva Completada del turista; un atractivo, cualquier turista con la cuenta Activa; una calificación por turista y entidad, sin edición; comentario de 0 a 500; el promedio cuenta solo las visibles.
- **RF61 (SRS):** los resultados se pueden ordenar por calificación promedio (DEC-302).

## Flujo básico

1. El Turista abre la ficha de un servicio o un atractivo (desde la búsqueda o desde «Calificar» en una reserva Completada).
2. La ficha muestra el promedio y las reseñas visibles; ms-analytics confirma que el Turista puede calificar.
3. El Turista elige las estrellas, escribe un comentario opcional y envía.
4. ms-analytics guarda la calificación, recalcula el promedio y encola `PromedioActualizado` en la misma transacción.
5. ms-catalog recibe el evento y actualiza el promedio del servicio o el atractivo; la búsqueda lo muestra y puede ordenar por él.

## Flujos alternativos

- **FA1 — Sin reserva Completada:** 403 `SIN_RESERVA_COMPLETADA`; la ficha explica cuándo podrá calificar.
- **FA2 — Ya calificó:** 409 `CALIFICACION_DUPLICADA`; las calificaciones no se editan.
- **FA3 — Cuenta no Activa:** 403 `CUENTA_INACTIVA`.
- **FA4 — Sin sesión:** la ficha invita a iniciar sesión.
- **FA5 — RabbitMQ caído:** el evento espera en el outbox y el promedio del catálogo se actualiza después.
