# CU «Guardar itinerario»

Fase de Construcción (RUP), plan del 100 %, iteración L. Reglas que el SRS no fija: DEC-298.
- **Pantallas:** WF-07 («Guardar itinerario en perfil») y WF-31 «Mis itinerarios» (`/perfil/itinerarios`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-chatbot.md`](../sad/api/ms-chatbot.md), sección «Itinerarios».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Guardar itinerario» (con «Consultar mis itinerarios») |
| **Actores** | Turista |
| **Requisitos que traza** | RF35 |
| **Alcance** | ms-chatbot (`itinerarios_sugeridos`) |

## Reglas de negocio

- **RF35 (SRS):** el turista guarda en su perfil el itinerario que le sugirió el asistente.
- **DEC-298:** hasta 20 itinerarios por turista; nombre de 1 a 100; de 1 a 30 pasos sin repetidos; día de 1 a 7. Solo el dueño los ve, edita y borra.

## Flujo básico

1. El asistente muestra un itinerario sugerido (CU «Interactuar con el asistente virtual»).
2. El Turista pulsa «Guardar itinerario en perfil».
3. ms-chatbot lo guarda con un nombre por defecto y la pantalla ofrece «Ver mis itinerarios».
4. En «Mis itinerarios» el Turista ve sus itinerarios por día, los renombra, cambia el día de un paso, quita pasos o los elimina con confirmación.

## Flujos alternativos

- **FA1 — Tope de 20:** 409 `LIMITE_ITINERARIOS`; la pantalla pide eliminar uno.
- **FA2 — Datos inválidos al editar:** 400 `DATOS_INVALIDOS` con `campos`.
- **FA3 — Itinerario ajeno o borrado:** 404 `ITINERARIO_NO_ENCONTRADO`; al eliminar, la tarjeta igual se quita.
