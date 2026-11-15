# CU «Interactuar con el asistente virtual»

Fase de Construcción (RUP), plan del 100 %, iteración L. Reglas que el SRS no fija: DEC-297 y DEC-298.
- **Pantallas:** WF-07 «Asistente virtual IA» (`/asistente`).
- **Contrato:** [`sad/api/ms-chatbot.md`](../sad/api/ms-chatbot.md), sección «Conversar».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Interactuar con el asistente virtual» (incluye «Justificar sugerencia») |
| **Actores** | Turista; ms-catalog (búsqueda pública); modelo de lenguaje (reglas o Gemini) |
| **Requisitos que traza** | RF04, RF36, RF59; usa RF05 (base de conocimientos) |
| **Alcance** | ms-chatbot (`chatbot_db`) y la búsqueda pública de ms-catalog |

## Reglas de negocio

- **RF04 (SRS):** el asistente responde preguntas y sugiere atractivos, servicios e itinerarios.
- **RF36 (SRS):** cada sugerencia indica su criterio principal: preferencias, popularidad o disponibilidad.
- **RF59 (SRS):** responde en el idioma de la interfaz.
- **DEC-297:** solo usa el catálogo publicado y la base de conocimientos; si no sabe, lo dice. Mensaje de 1 a 500 caracteres; 20 mensajes por turista y día; sin historial en el servidor; al modelo no le llegan datos personales.
- **DEC-298:** el criterio se calcula con datos (preferencias, después popularidad y después disponibilidad); los atractivos cerrados no se sugieren; el itinerario tiene de 1 a 7 días y hasta 3 actividades por día.

## Flujo básico

1. El Turista abre «Asistente IA» en la navegación.
2. Escribe una pregunta o elige un chip rápido.
3. ms-chatbot consume uno de sus mensajes del día, interpreta la pregunta y consulta la búsqueda pública y la base de conocimientos.
4. Calcula las sugerencias con su criterio y justificación y, si se pidió, el itinerario por día.
5. El modelo redacta la respuesta en el idioma de la interfaz solo con ese contexto.
6. La pantalla muestra la respuesta, el itinerario sugerido, los prestadores mencionados y los mensajes que quedan.

## Flujos alternativos

- **FA1 — Límite diario:** el mensaje 21 responde 429 `LIMITE_MENSAJES`; la pantalla lo avisa y deshabilita el campo.
- **FA2 — Catálogo caído:** responde con la base de conocimientos y avisa que no pudo consultar el catálogo.
- **FA3 — Sin información:** el asistente dice que no tiene información y sugiere sobre qué preguntar.
- **FA4 — Mensaje vacío o largo:** 400 `DATOS_INVALIDOS`.
- **FA5 — Otro rol o sin sesión:** «Sin permiso» o el inicio de sesión.
