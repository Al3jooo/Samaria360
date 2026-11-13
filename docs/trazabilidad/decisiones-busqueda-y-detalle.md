# Decisiones de «Buscar oferta turística» y «Consultar detalle de oferta»

Parte de la [matriz de trazabilidad](../trazabilidad.md): decisiones (DEC), hallazgos y verificación de cada sesión, separadas del archivo principal en la revisión del 2026-11-13. Los números DEC son únicos en todo el proyecto.

## Buscar oferta turística — decisiones de búsqueda (RF16, RF23, RF33, RF38, RF50, RF61)

Decididas por el equipo; se aplican en el caso de uso de ms-catalog (`domain/service/PoliticaBusqueda` y el adaptador MongoDB; arquitectura en [`ADR-004`](../sad/adr/ADR-004-busqueda-combinada.md)).

| # | Decisión | RF |
|---|---|---|
| 1 | Los atractivos aparecen en los resultados junto con los servicios, como en WF-05. | RF16, RF23 |
| 2 | El filtro de categoría aplica solo a servicios: si se elige una categoría, los atractivos quedan fuera. | RF16 |
| 3 | El filtro de precio máximo excluye los atractivos, porque no tienen precio. | RF16, RF37 |
| 4 | Al ordenar por precio, los resultados sin precio van al final. | RF61 |
| 5 | Los atractivos cerrados temporalmente o en mantenimiento sí aparecen, con su estado visible, para que el turista lo sepa y busque una alternativa. | RF33, RF08 |
| 6 | El filtro de accesibilidad aplica a servicios y atractivos. | RF50 |

## Consultar detalle de oferta — decisiones y hallazgos (WF-06)

**Política de visibilidad.** Ver la sección «WF-06 — Detalle de un servicio»: lo que el catálogo trae se muestra; lo que falta (dato ausente o función pendiente) tiene un único estado deshabilitado, sin texto visible de estado, con «—» o borde discontinuo y «No disponible» para lectores de pantalla. Ninguna región de WF-06 se oculta. La desviación del README de wireframes y su justificación están en esa sección.

**Alerta de afluencia y etiqueta «Afluencia en vivo».** WF-06 dibuja los dos elementos: la etiqueta «Aforo en vivo» junto a «Prestador verificado» y «Reserva inmediata», y la alerta con el texto «Aforo en vivo: 8 de 24 cupos ocupados…». El README los lista por separado (etiquetas y «alerta de afluencia»). Se conservan ambos para no fusionar elementos del wireframe, y la región se titula «Alerta de afluencia» para que no repita el texto de la etiqueta.

**RF58 — criterios derivados.** El SRS no define criterios de verificación por RF (ver HC1); estos se derivan del enunciado («registrar los días y horarios de atención de sus servicios o atractivos como un campo de texto visible en la ficha de detalle»):

| # | Criterio | Estado |
|---|---|---|
| C1 | El horario se guarda como campo de texto | Cumple: `horarioAtencion` (`string`) en `servicios` |
| C2 | Expresa días y horarios | Cumple por contenido; el SRS no pide estructura (WF-06 dibuja filas por concepto; manda el SRS) |
| C3 | Visible en la ficha de detalle para el Turista | Cumple: región «Horarios de atención» de WF-06, con los saltos de línea del texto |
| C4 | El Prestador lo registra | Backend listo (iteración C, A1: el registro captura `horarioAtencion`, DEC-131); pantalla WF-08 en B1 |
| C5 | El Gestor lo registra para atractivos | Cumple: `horarioAtencion` en `atractivos` (M012, DEC-208), capturado en WF-09 (alta y edición) y visible en la ficha del atractivo (iteración E, B1) |

Estado de RF58: **Implementado** (C1 a C5; iteración E, B1).

**Riesgo: datos de contacto (Ley 1581 de 2012).** El teléfono y el correo del servicio se publican porque RF09 los captura y RF43 los define como «datos de contacto de atención al público» que «visualizan los turistas». Si el prestador es persona natural, pueden ser datos personales y su publicación requiere autorización previa e informada con esa finalidad (arts. 9 y 12). La casilla de Política de Privacidad del registro (WF-03) existe, pero el texto de la política está pendiente (RF46), así que hoy no se puede verificar que informe esta finalidad. Mitigación: solo se publica el contacto del servicio (no el del usuario de `auth_db`) y solo en servicios visibles; la semilla usa datos ficticios (`.test`). Antes de un despliegue real, la política debe incluir esa finalidad.

**Hallazgos abiertos (no se corrigen en esta iteración):**

| # | Hallazgo | Severidad |
|---|---|---|
| HC1 | `docs/casos-de-uso/` tenía cero CU: ningún RF tenía caso de uso especificado (flujos, precondiciones, postcondiciones) ni pruebas de aceptación, como exige la sección 4 del SRS. Cada iteración agregó los CU de sus RF (A, B1, B2, C, D y E; ver sus secciones), más [«Realizar reserva»](../casos-de-uso/CU-realizar-reserva.md) y [«Cancelar reserva»](../casos-de-uso/CU-cancelar-reserva.md). **Resuelto en la revisión del 2026-11-12** para los RF implementados antes del plan que seguían sin CU: [«Iniciar sesión»](../casos-de-uso/CU-iniciar-sesion.md) (RF15), [«Cerrar sesión»](../casos-de-uso/CU-cerrar-sesion.md) (RF55), [«Registrarse»](../casos-de-uso/CU-registrarse.md) (RF01, RF02), [«Buscar oferta turística»](../casos-de-uso/CU-buscar-oferta.md) (RF16, RF23, RF38, RF50, RF61) y [«Consultar detalle de oferta»](../casos-de-uso/CU-consultar-detalle-oferta.md) (RF37, RF58). RF52 está en «Consultar historial» y «Cancelar reserva»; RF03 (cambiar el idioma) es transversal y no tiene CU propio. Los RF Pendientes y Fuera de alcance no tienen CU. En WF-06, RF57 y RF51 siguen sin CU propio (están deshabilitados) | **Resuelto** (2026-11-12) |
| HC2 | WF-06 pide RNT y dirección. El RNT vive en `auth_db.prestador`, sin endpoint público ni réplica en `catalog_db`; `servicios` solo guarda coordenadas. Mostrarlos requiere una composición entre servicios que el SAD no define (falta un ADR: réplica como `prestadorActivo` o composición de API). **Resuelto en el backend (iteración C, A1):** réplica del RNT por el evento `CuentaActualizada` (enmienda de ADR-008; DEC-126, DEC-146, DEC-147) y `direccion` en `servicios` (M008, M009); el detalle público los entrega (DEC-150). La pantalla los muestra en B1 | Hallazgo Crítico (resuelto en el backend) |
| HC4 | RF58 incluye atractivos, pero `atractivos` no tenía campo de horario | **Resuelto** (iteración E): `horarioAtencion` en el esquema v2 (M012, DEC-208) y en WF-09 y la ficha (B1) |
