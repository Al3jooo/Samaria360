# CU «Gestionar base de conocimientos»

Fase de Construcción (RUP), plan del 100 %, iteración L. Reglas que el SRS no fija: DEC-297.
- **Pantallas:** WF-32 «Base de conocimientos» (`/admin/conocimiento`, propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-chatbot.md`](../sad/api/ms-chatbot.md), sección «Base de conocimientos».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Gestionar base de conocimientos» |
| **Actores** | Administrador |
| **Requisitos que traza** | RF05 |
| **Alcance** | ms-chatbot (`base_conocimiento_turistico`) |

## Reglas de negocio

- **RF05 (SRS):** el asistente responde con una base de conocimientos turísticos que alimenta el municipio.
- **DEC-297:** contenido de 1 a 2000 caracteres y categoría de 1 a 50; hasta 500 entradas; el asistente usa hasta 3 por respuesta, las que coinciden con las palabras del mensaje.

## Flujo básico

1. El Administrador abre «Base de conocimientos» desde el menú del avatar.
2. Escribe la categoría y el contenido y pulsa «Agregar entrada».
3. ms-chatbot la guarda; la lista se actualiza, de 10 en 10.
4. El Administrador puede editar una entrada o eliminarla con confirmación.
5. Desde ese momento el asistente puede usarla en sus respuestas.

## Flujos alternativos

- **FA1 — Datos inválidos:** 400 `DATOS_INVALIDOS`.
- **FA2 — Tope de 500:** 409 `LIMITE_CONOCIMIENTO`.
- **FA3 — Entrada inexistente:** 404 `CONOCIMIENTO_NO_ENCONTRADO`.
- **FA4 — Otro rol:** «Sin permiso» o 403.
