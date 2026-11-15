# CU «Modificar oferta»

Fase de Construcción (RUP), plan del 100 %, iteración I. Reglas que el SRS no fija: decisión 3 del equipo y DEC-310.
- **Pantallas:** WF-38 «Oferta y disponibilidad» (`/prestador/servicios/:id/oferta`, propuesta por arquitectura), desde el detalle de un servicio Publicado de WF-18.
- **Contrato:** [`sad/api/ms-booking.md`](../sad/api/ms-booking.md), sección «Oferta del Prestador (RF19)»; evento `OfertaModificada` en [`sad/api/eventos.md`](../sad/api/eventos.md).

| Campo | Valor |
|---|---|
| **Caso de uso** | «Modificar oferta» |
| **Actores** | Prestador |
| **Requisitos que traza** | RF19 (y RF41, que guarda cada cambio) |
| **Alcance** | ms-booking (`inventario_cupo`, outbox); ms-catalog (`servicios.oferta`, `historial_modificaciones`) |

## Reglas de negocio

- **RF19 (SRS):** el Prestador modifica las fechas, los horarios y la cantidad de cupos de sus servicios registrados, y los cambios se reflejan en tiempo real en las nuevas búsquedas.
- **Decisión 3 del equipo (DEC-310):** solo fechas futuras (desde mañana en Santa Marta, hasta 60 días); de 1 a 10 000 cupos; los cupos **no bajan de lo reservado** y una fecha o salida con reservas Confirmadas **no se cierra**; sin nueva aprobación.
- Solo Tour (fechas y salidas) y Alojamiento (fechas por unidad) tienen oferta; Gastronomía y Alquiler no.
- La **plantilla** (días de operación y salidas) se aplica a las fechas que todavía no están materializadas; las existentes no cambian.

## Flujo básico

1. El Prestador abre el detalle de un servicio Publicado y elige «Gestionar disponibilidad».
2. La pantalla muestra las fechas de los próximos 14 días (o el rango elegido) con los cupos totales, los reservados, los disponibles y el estado de cada fecha o salida.
3. El Prestador cambia los cupos de una fila y elige «Guardar cupos».
4. ms-booking valida la regla, actualiza la fila en una transacción con el evento `OfertaModificada` y responde con la fila nueva; las nuevas búsquedas y reservas ven el cambio de inmediato.
5. ms-catalog recibe el evento y guarda la modificación en el historial (RF41).

## Flujos alternativos

- **FA1 — Cerrar una fecha o salida:** «Cerrar»; la fila queda cerrada (sin cupos para reservar). Con reservas Confirmadas: 409 `FECHA_CON_RESERVAS` y la pantalla lo explica.
- **FA2 — Abrir una fecha cerrada:** «Abrir» con los cupos de la fila.
- **FA3 — Agregar una salida o una fecha:** fecha, horario (Tour) o unidad (Alojamiento) y cupos; si ya existía, se ajusta.
- **FA4 — Cupos por debajo de lo reservado:** 409 `CUPOS_RESERVADOS` con la cantidad reservada.
- **FA5 — Cambiar la plantilla:** días y salidas; 202. ms-catalog actualiza la oferta del servicio cuando llega el evento y las fechas que se materialicen después la usan.
- **FA6 — Datos inválidos** (fecha pasada o fuera del horizonte, horario mal formado, unidad ajena, cupos fuera de rango): 400 `DATOS_INVALIDOS` con `campos`.
- **FA7 — Servicio ajeno, no visible o sin oferta:** 409 `OFERTA_NO_DISPONIBLE` (o 400 si la categoría no se reserva).
- **FA8 — Catálogo caído y falta materializar:** 503 `SERVICIO_NO_DISPONIBLE`.
