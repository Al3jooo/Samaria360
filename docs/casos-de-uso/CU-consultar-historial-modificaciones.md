# CU «Consultar historial de modificaciones»

Fase de Construcción (RUP), plan del 100 %, iteración I. Reglas: DEC-311.
- **Pantallas:** WF-39 «Historial de modificaciones» (`/prestador/historial-modificaciones`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), sección «Historial de modificaciones y galería (iteración I)».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar historial de modificaciones» |
| **Actores** | Prestador |
| **Requisitos que traza** | RF41 |
| **Alcance** | ms-catalog (`historial_modificaciones`, M017) |

## Reglas de negocio

- **RF41 (SRS):** historial detallado de las modificaciones de precios, cupos y horarios de los servicios del Prestador en los últimos 30 días, con la fecha, la hora y el valor anterior frente al nuevo.
- **DEC-311:** los precios los registra RF32 en la misma transacción que la edición; los cupos, las fechas, los horarios y la plantilla llegan de ms-booking con `OfertaModificada` (sin duplicados). Solo los servicios del Prestador del token; 20 por página.

## Flujo básico

1. El Prestador abre «Historial de modificaciones» desde el menú del avatar.
2. La pantalla muestra las modificaciones de los últimos 30 días, de la más reciente a la más antigua: fecha y hora, servicio, tipo de cambio, fecha o unidad afectada, valor anterior y valor nuevo.
3. El Prestador filtra por un servicio; el filtro y la página quedan en la URL.

## Flujos alternativos

- **FA1 — Sin modificaciones:** la pantalla lo dice.
- **FA2 — Filtro inválido:** 400 `DATOS_INVALIDOS`.
- **FA3 — Otro rol:** «Sin permiso» o 403.
