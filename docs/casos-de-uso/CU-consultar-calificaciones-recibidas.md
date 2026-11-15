# CU «Consultar calificaciones recibidas»

Fase de Construcción (RUP), plan del 100 %, iteración H. Reglas que el SRS no fija: DEC-300.
- **Pantallas:** WF-33 «Calificaciones recibidas» (`/prestador/calificaciones`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-analytics.md`](../sad/api/ms-analytics.md), sección «Calificaciones».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar calificaciones recibidas» |
| **Actores** | Prestador |
| **Requisitos que traza** | RF56 |
| **Alcance** | ms-analytics (`calificaciones`) |

## Reglas de negocio

- **RF56 (SRS):** el Prestador ve todas las calificaciones y comentarios de sus servicios, con el promedio y filtros por servicio y fecha.
- **DEC-300:** solo las visibles (las ocultas por el Administrador no se muestran); sin datos del turista; fechas en días de Santa Marta; 20 por página.

## Flujo básico

1. El Prestador abre «Calificaciones recibidas» desde el menú del avatar.
2. La pantalla muestra el promedio general, un resumen por servicio y las calificaciones de la más reciente a la más antigua.
3. El Prestador filtra por servicio y rango de fechas; los filtros quedan en la URL.

## Flujos alternativos

- **FA1 — Sin calificaciones:** «No hay calificaciones con estos filtros».
- **FA2 — Fechas inválidas:** 400 `DATOS_INVALIDOS`.
- **FA3 — Otro rol:** «Sin permiso» o 403.
