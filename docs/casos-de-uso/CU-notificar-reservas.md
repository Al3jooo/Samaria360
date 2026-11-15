# CU «Notificar cambios de reserva»

Fase de Construcción (RUP), plan del 100 %, iteración G. Caso de uso que los CU «Realizar reserva» y «Cancelar reserva» dejaban pendiente («Notificar cambios de reserva»). Describe lo que hace el prototipo; las reglas que el SRS no fija están en DEC-294.
- **Pantallas:** WF-29, campana del encabezado (propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md) («Notificaciones» y «Correo de RF30») y [`sad/api/eventos.md`](../sad/api/eventos.md).

| Campo | Valor |
|---|---|
| **Caso de uso** | «Notificar cambios de reserva», con «Consultar notificaciones» |
| **Actores** | Turista, Prestador; ms-booking (dispara los eventos) |
| **Requisitos que traza** | RF63, RF30; RF03 (idioma del texto) |
| **Alcance** | ms-booking (eventos), RabbitMQ, ms-analytics (`notificaciones`, `correos_pendientes`), ms-auth (contacto del Prestador) y el servidor SMTP |

## Reglas de negocio

- **RF63 (SRS):** notificaciones internas con un ícono de campana en la barra de navegación, para informar al Turista sobre los cambios de estado de sus reservas y al Prestador sobre las reservas nuevas.
- **RF30 (SRS):** notificar automáticamente al Prestador cuando una reserva de uno de sus servicios queda confirmada, con la actividad, la fecha y la cantidad de personas.
- **DEC-294 (decisión del equipo, no del SRS):** el Turista recibe Confirmada, Cancelada y Completada; el Prestador, las reservas nuevas, en la campana y por correo; la campana se consulta al cargar, cada minuto y al abrirla.

## Flujo básico

1. El Turista confirma una reserva (CU «Realizar reserva»); ms-booking encola `ReservaConfirmada` en la misma transacción.
2. El relevo publica el evento en RabbitMQ; ms-analytics lo recibe una sola vez (deduplicación por `idEvento`).
3. ms-analytics guarda una notificación para el Turista y otra para el Prestador, y encola el correo de RF30.
4. El programador de correos pide el contacto del Prestador a ms-auth y envía el correo.
5. En el encabezado, la campana muestra el contador de no leídas. El usuario la abre y ve el texto en su idioma.
6. El usuario abre una notificación (va a «Mis reservas» o al historial del Prestador) o elige «Marcar todas como leídas».

## Flujos alternativos

- **FA1 — Cancelación o paso a Completada:** el Turista recibe la notificación correspondiente; la cancelación queda además en la auditoría (RF13). El Prestador no recibe correo.
- **FA2 — RabbitMQ, ms-analytics, ms-auth o el SMTP caídos:** la reserva no se afecta; el evento espera en el outbox o en la cola, y el correo se reintenta con espera creciente.
- **FA3 — Evento repetido:** no se duplica nada.
- **FA4 — ms-analytics no responde a la campana:** la campana sigue visible con un aviso.

## Fuera de alcance

- Notificaciones por otros canales (WhatsApp, push) y de eventos que no son reservas (sin RF).
