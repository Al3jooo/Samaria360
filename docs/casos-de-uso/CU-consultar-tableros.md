# CU «Consultar tableros»

Fase de Construcción (RUP), plan del 100 %, iteración K. Reglas que el SRS no fija: respuesta 4 y decisión 6 del equipo, DEC-307 y DEC-308.
- **Pantallas:** WF-35 «Tableros» (`/tableros`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Tableros».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar tableros» |
| **Actores** | Administrador, Gestor turístico |
| **Requisitos que traza** | RF14 |
| **Alcance** | ms-analytics (`reservas`, `consultas`, `cuentas`), con los eventos de ms-booking, ms-catalog y ms-auth |

## Reglas de negocio

- **RF14 (SRS):** tableros con el número total de reservas diarias, el top 3 de atractivos más consultados y los visitantes registrados por zona, accesibles según el rol.
- **Respuesta 4 del equipo:** el Administrador y el Gestor ven los tableros; el Prestador no.
- **Decisión 6 del equipo:** «zona» es el país de origen del turista.
- **DEC-308:** reservas por día de confirmación (aunque se cancelen después); rango por defecto de 30 días y máximo de 366; consultas solo de las visitas por el gateway (DEC-307).

## Flujo básico

1. El Administrador o el Gestor abre «Tableros» desde el menú del avatar.
2. La pantalla muestra el total de reservas del periodo, las reservas por día, el top 3 de atractivos más consultados y los turistas registrados por país.
3. El usuario cambia el rango de fechas y aplica; el rango queda en la URL.

## Flujos alternativos

- **FA1 — Rango inválido:** 400 `DATOS_INVALIDOS`.
- **FA2 — Sin datos:** cada gráfico lo dice.
- **FA3 — Otro rol:** «Sin permiso» o 403.
