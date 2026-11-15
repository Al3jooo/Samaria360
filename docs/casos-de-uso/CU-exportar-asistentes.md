# CU «Exportar asistentes»

Fase de Construcción (RUP), plan del 100 %, iteración I. Reglas: decisión 7 del equipo y DEC-312.
- **Pantallas:** WF-40 «Asistentes» (`/prestador/asistentes`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-booking.md`](../sad/api/ms-booking.md), sección «Asistentes (RF42)».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Exportar asistentes» |
| **Actores** | Prestador |
| **Requisitos que traza** | RF42 |
| **Alcance** | ms-booking (`reserva`); ms-auth (contactos, endpoint interno) |

## Reglas de negocio

- **RF42 (SRS):** módulo para visualizar y exportar (CSV o PDF) el listado de turistas con reservas Confirmadas para una actividad y una fecha, con el nombre y los datos de contacto de cada asistente.
- **Decisión 7 (DEC-312):** los contactos los entrega ms-auth por su endpoint interno con el JWT de servicio de ms-booking; no se copian en reservas_db. Solo el Prestador dueño del servicio.
- En Tour, las reservas de la fecha (y de la salida, si se elige); en Alojamiento, las estadías que incluyen la fecha.

## Flujo básico

1. El Prestador abre «Asistentes» desde el menú del avatar o desde la disponibilidad de un servicio.
2. Elige el servicio, la fecha y, si quiere, la salida, y consulta.
3. La pantalla muestra cada reserva Confirmada con el nombre, el correo, el teléfono, las personas, la salida o la unidad y las fechas, más el total de personas.
4. El Prestador elige «Descargar CSV» (se descarga el archivo) o «Descargar PDF» (se abre el diálogo de impresión con el listado).

## Flujos alternativos

- **FA1 — Sin reservas Confirmadas:** la pantalla lo dice y no ofrece exportar.
- **FA2 — ms-auth no responde:** 503 `SERVICIO_NO_DISPONIBLE` con `servicio: ms-auth`; la pantalla lo explica.
- **FA3 — Sin servicio o sin fecha:** 400 `DATOS_INVALIDOS`; la pantalla no consulta hasta tenerlos.
- **FA4 — Servicio ajeno:** lista vacía (las reservas se filtran por el Prestador del token).
