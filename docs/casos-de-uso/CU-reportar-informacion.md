# CU «Reportar información falsa o desactualizada»

Fase de Construcción (RUP), plan del 100 %, iteración H. Reglas que el SRS no fija: respuesta 5 del equipo y DEC-301.
- **Pantallas:** WF-06 («Reportar información falsa o desactualizada», en el servicio y en el atractivo) y WF-34 «Moderación», pestaña «Reportes» (`/admin/moderacion?pestana=reportes`).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Reportes».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Reportar información falsa o desactualizada» (con «Revisar reportes») |
| **Actores** | Turista, Administrador |
| **Requisitos que traza** | RF57 |
| **Alcance** | ms-analytics (`reportes`) |

## Reglas de negocio

- **RF57 (SRS):** el Turista reporta un atractivo o servicio con un motivo de una lista predefinida y una descripción opcional; los reportes son visibles para el Administrador en un panel de moderación.
- **Respuesta 5 del equipo:** motivos «Información falsa», «Información desactualizada», «Precio incorrecto», «Ubicación incorrecta», «Fotos que no corresponden» y «Otro»; el Administrador solo marca el reporte como Revisado.
- **DEC-301:** descripción de hasta 500; un reporte pendiente por turista y entidad.

## Flujo básico

1. El Turista pulsa «Reportar información falsa o desactualizada» en la ficha.
2. Elige un motivo, escribe una descripción opcional y envía.
3. ms-analytics guarda el reporte como Pendiente.
4. El Administrador abre «Moderación», pestaña «Reportes», ve los pendientes del más antiguo al más reciente y abre la ficha.
5. Marca el reporte como Revisado.

## Flujos alternativos

- **FA1 — Sin sesión:** el enlace lleva al inicio de sesión.
- **FA2 — Ya hay uno pendiente:** 409 `REPORTE_DUPLICADO`.
- **FA3 — Ya revisado:** 409 `ESTADO_CAMBIADO`.
- **FA4 — Otro rol:** el botón no aparece; el panel muestra «Sin permiso».
