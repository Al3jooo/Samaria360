# CU «Registrar ocupación de un atractivo»

Fase de Construcción (RUP), plan del 100 %, iteración J. Reglas que el SRS no fija: decisión 2 del equipo y DEC-304.
- **Pantallas:** WF-24 «Atractivos», columna «Afluencia» con «Registrar ocupación» (propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), sección «Afluencia».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Registrar ocupación de un atractivo» |
| **Actores** | Gestor turístico; ms-analytics (recibe el evento) |
| **Requisitos que traza** | RF39 (las visitas del periodo vigente); alimenta RF64 |
| **Alcance** | ms-catalog (`atractivos`, `ocupaciones`, `outbox_eventos`), ms-analytics (`ocupaciones`) |

## Reglas de negocio

- **Decisión 2 del equipo:** el Gestor registra la ocupación actual de cada atractivo; queda el historial.
- **DEC-304:** de 0 a 1 000 000 personas; el atractivo debe tener límite de capacidad de carga (RF07); la lectura, el historial y el evento `OcupacionRegistrada` se guardan en una transacción.

## Flujo básico

1. El Gestor abre el panel «Atractivos».
2. En la fila del atractivo escribe las personas que hay ahora y pulsa «Registrar ocupación».
3. ms-catalog guarda la lectura, la agrega al historial y encola `OcupacionRegistrada`.
4. La pantalla muestra el nivel y el porcentaje calculados.
5. ms-analytics guarda la lectura para los reportes de afluencia (RF64).

## Flujos alternativos

- **FA1 — Valor inválido:** la pantalla lo avisa sin enviar; la API responde 400 `DATOS_INVALIDOS`.
- **FA2 — Sin límite de capacidad:** 400 `DATOS_INVALIDOS` (`campos.ocupacion = NO_PERMITIDO`).
- **FA3 — Atractivo inexistente:** 404 `ATRACTIVO_NO_ENCONTRADO`.
- **FA4 — Otro rol:** 403.
