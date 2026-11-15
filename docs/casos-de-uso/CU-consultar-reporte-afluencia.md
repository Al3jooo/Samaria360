# CU «Consultar reporte de afluencia»

Fase de Construcción (RUP), plan del 100 %, iteración K. Reglas que el SRS no fija: decisión 2 del equipo, DEC-304 y DEC-308.
- **Pantallas:** WF-37 «Reporte de afluencia» (`/gestor/afluencia`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Tableros».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar reporte de afluencia» |
| **Actores** | Gestor turístico |
| **Requisitos que traza** | RF64 |
| **Alcance** | ms-analytics (`ocupaciones`, alimentada por `OcupacionRegistrada` de ms-catalog) |

## Reglas de negocio

- **RF64 (SRS):** reportes del nivel de afluencia de los atractivos, calculado con el límite de capacidad y las visitas vigentes, con filtros por rango de fechas y por atractivo.
- **DEC-304:** las visitas son las lecturas de ocupación que registra el Gestor; el porcentaje es sobre el límite de capacidad de carga.
- **DEC-308:** por atractivo, las lecturas, el porcentaje promedio y el máximo y el último nivel; por día, el promedio y el máximo.

## Flujo básico

1. El Gestor abre «Reporte de afluencia» desde el menú del avatar.
2. La pantalla muestra los atractivos con lecturas en los últimos 30 días y la ocupación promedio por día.
3. El Gestor cambia el rango o elige un atractivo y aplica; los filtros quedan en la URL.

## Flujos alternativos

- **FA1 — Sin lecturas:** la pantalla lo dice.
- **FA2 — Filtros inválidos:** 400 `DATOS_INVALIDOS`.
- **FA3 — Otro rol:** «Sin permiso» o 403.
