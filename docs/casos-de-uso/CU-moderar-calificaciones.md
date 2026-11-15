# CU «Moderar calificaciones»

Fase de Construcción (RUP), plan del 100 %, iteración H. Reglas que el SRS no fija: decisión 1 del equipo y DEC-300.
- **Pantallas:** WF-34 «Moderación», pestaña «Calificaciones» (`/admin/moderacion`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Calificaciones».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Moderar calificaciones» |
| **Actores** | Administrador |
| **Requisitos que traza** | RF29; RF13 (la acción queda en la auditoría) |
| **Alcance** | ms-analytics (`calificaciones`, `logs_auditoria`) |

## Reglas de negocio

- **RF29 (SRS):** el Administrador oculta o elimina calificaciones que incumplan las políticas y registra la acción y su motivo en el log de auditoría.
- **Decisión 1 del equipo:** ocultar es reversible; eliminar es definitivo; el motivo tiene de 1 a 500 caracteres; el promedio cuenta solo las visibles.

## Flujo básico

1. El Administrador abre «Moderación» desde el menú del avatar.
2. Filtra las calificaciones por estado (todas, visibles u ocultas).
3. Elige «Ocultar», «Volver a mostrar» o «Eliminar» y escribe el motivo.
4. ms-analytics cambia el estado (o la borra), registra la acción con el motivo en la auditoría y recalcula el promedio, todo en una transacción.
5. El promedio nuevo llega al catálogo por `PromedioActualizado`.

## Flujos alternativos

- **FA1 — Motivo vacío o largo:** 400 `DATOS_INVALIDOS`.
- **FA2 — Otro Administrador ya la cambió:** 409 `ESTADO_CAMBIADO` con `estadoActual`; la lista se actualiza.
- **FA3 — Ya no existe:** 404 `CALIFICACION_NO_ENCONTRADA`.
- **FA4 — Otro rol:** «Sin permiso» o 403.
