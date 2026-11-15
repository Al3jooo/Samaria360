# CU «Consultar afluencia de un atractivo»

Fase de Construcción (RUP), plan del 100 %, iteración J. Reglas que el SRS no fija: decisión 2 del equipo, DEC-304 y DEC-305.
- **Pantallas:** WF-06 para atractivos («Alerta de afluencia» y «Destinos alternativos con menor afluencia») y WF-05 (nivel en la tarjeta, color del marcador y leyenda).
- **Contrato:** [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), sección «Afluencia».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Consultar afluencia de un atractivo» (con «Sugerir destinos alternativos») |
| **Actores** | Turista o visitante |
| **Requisitos que traza** | RF39, RF08, RF51 |
| **Alcance** | ms-catalog (`atractivos.ocupacionActual`) |

## Reglas de negocio

- **RF39 (SRS):** indicador de afluencia Bajo, Medio o Alto, calculado con el límite de capacidad (RF07) y las visitas del periodo vigente.
- **RF08 (SRS):** sugerir destinos alternativos cuando la afluencia alcanza el límite.
- **RF51 (SRS):** mostrar destinos similares de menor concurrencia.
- **Decisión 2 del equipo:** Bajo por debajo del 40 %, Medio del 40 % al 80 %, Alto por encima del 80 %; RF08 al 100 %; «similar» es de la misma categoría, ordenado por menor ocupación y cercanía.
- **DEC-304:** el periodo vigente es el día en curso en Santa Marta.

## Flujo básico

1. El Turista abre la ficha de un atractivo.
2. La ficha muestra el nivel de afluencia, el porcentaje de la capacidad y la hora de la última lectura.
3. La ficha muestra hasta 3 destinos de la misma categoría, abiertos y con menor ocupación, del más vacío al más lleno y del más cercano al más lejano.
4. En la búsqueda, cada atractivo muestra su nivel y su marcador toma el color del nivel.

## Flujos alternativos

- **FA1 — Límite alcanzado (RF08):** la ficha muestra una alerta que lleva a los destinos alternativos.
- **FA2 — Sin lectura del día:** «Sin datos de afluencia hoy»; los alternativos se ordenan por ocupación conocida.
- **FA3 — Sin alternativos:** la sección lo dice.
- **FA4 — Atractivo inexistente:** 404 `ATRACTIVO_NO_ENCONTRADO`.
