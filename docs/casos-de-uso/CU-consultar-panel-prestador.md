# CU «Consultar panel del prestador»

Fase de Construcción (RUP), plan del 100 %, iteración K. Reglas que el SRS no fija: DEC-307 y DEC-308.
- **Pantallas:** WF-36 «Mi panel» (`/prestador/panel`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Tableros».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar panel del prestador» |
| **Actores** | Prestador |
| **Requisitos que traza** | RF31 |
| **Alcance** | ms-analytics (`consultas`, `reservas`) |

## Reglas de negocio

- **RF31 (SRS):** panel individual con las consultas o visualizaciones y las reservas recibidas por cada servicio, con un periodo de 7 o 30 días.
- **DEC-308:** solo los servicios del Prestador del token; un servicio sin consultas ni reservas en el periodo no aparece; los días son de Santa Marta.

## Flujo básico

1. El Prestador abre «Mi panel» desde el menú del avatar.
2. La pantalla muestra, por servicio, las consultas y las reservas de los últimos 7 días.
3. El Prestador elige «Últimos 30 días»; el periodo queda en la URL.

## Flujos alternativos

- **FA1 — Sin actividad:** la pantalla lo dice.
- **FA2 — Periodo distinto de 7 o 30:** 400 `DATOS_INVALIDOS`.
- **FA3 — Otro rol:** «Sin permiso» o 403.
