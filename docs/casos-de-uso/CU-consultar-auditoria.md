# CU «Consultar log de auditoría»

Fase de Construcción (RUP), plan del 100 %, iteración G. Corresponde a la figura 2 del Entregable 2 («Administrar auditoría»). Reglas que el SRS no fija: DEC-294.
- **Pantallas:** WF-30 «Auditoría» (`/admin/auditoria`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Auditoría».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar log de auditoría» (con «Registrar acción crítica», automático) |
| **Actores** | Administrador; ms-auth, ms-catalog y ms-booking (disparan los eventos) |
| **Requisitos que traza** | RF13; RF29 (la moderación también se audita, iteración H) |
| **Alcance** | ms-analytics (`logs_auditoria`) y los eventos de RabbitMQ |

## Reglas de negocio

- **RF13 (SRS):** log automático de las acciones críticas (aprobaciones, cancelaciones, cambios de rol) con el usuario, la fecha y la acción.
- **DEC-294:** se registran la aprobación o el rechazo de un prestador (RF28) y de un servicio (RF21), la cancelación de una reserva (RF18) y el cambio de rol (RF45). El detalle lleva identificadores, sin texto libre ni datos personales.

## Flujo básico

1. Una acción crítica ocurre en su servicio, que encola su evento en la misma transacción.
2. ms-analytics lo recibe una sola vez y guarda la entrada con el usuario que hizo la acción, la acción y la fecha.
3. El Administrador abre «Auditoría» desde el menú del avatar.
4. La pantalla muestra las entradas de la más reciente a la más antigua, 20 por página.
5. El Administrador filtra por acción y rango de fechas; los filtros quedan en la URL.

## Flujos alternativos

- **FA1 — Fechas inválidas:** 400 `DATOS_INVALIDOS`; la pantalla pide revisar las fechas.
- **FA2 — Otro rol:** «Sin permiso» (ruta protegida) o 403 de la API.
- **FA3 — ms-analytics no responde:** aviso de que la auditoría no está disponible.

## Fuera de alcance

- Exportar el log y borrar entradas (sin RF).
