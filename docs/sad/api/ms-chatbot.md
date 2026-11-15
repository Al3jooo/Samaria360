# API de ms-chatbot (asistente virtual)

Contenedor «Chatbot IA» del Entregable 2 (tabla 1, figura 20), en Java 17 y Spring Boot 3.5 (DEC-279). Expone la API por el gateway (`/api/v1/chatbot/**`) y escucha en el 8085 solo dentro de la red de Docker. Guarda sus datos en `chatbot_db` (MongoDB, migración C001) y consulta la búsqueda pública de ms-catalog (DEC-280). Decisiones en [`trazabilidad/decisiones-plan-100-iteracion-l.md`](../../trazabilidad/decisiones-plan-100-iteracion-l.md).

## Convenciones

- **Autenticación:** `Authorization: Bearer <JWT de ms-auth>`, validado con el JWKS (ADR-005) y el claim `ver` contra la proyección `cuentas` de `chatbot_db`, alimentada por `CuentaActualizada` desde la cola `ms-chatbot.eventos.v1` (ADR-011; DEC-110: sin fila, un Gestor o un Administrador recibe 401 `SESION_INVALIDADA`). Toda la API exige un token.
- **Errores:** ProblemDetail (RFC 9457) con `codigo`: `NO_AUTENTICADO` y `SESION_INVALIDADA` (401), `ACCESO_DENEGADO` (403), `DATOS_INVALIDOS` (400, con `campos`), `ITINERARIO_NO_ENCONTRADO` y `CONOCIMIENTO_NO_ENCONTRADO` (404), `LIMITE_ITINERARIOS` y `LIMITE_CONOCIMIENTO` (409), `LIMITE_MENSAJES` (429), `ERROR_INTERNO` (500).
- **Fechas:** instantes ISO 8601 en UTC. El día del límite de mensajes es el de Santa Marta (`America/Bogota`).

## Conversar (RF04, RF36, RF59)

Solo el **Turista**. `POST /api/v1/chatbot/mensajes`

```json
{ "mensaje": "Arma un itinerario de 2 días con tours", "idioma": "es", "intereses": ["Aventura"] }
```

- `mensaje`: de 1 a 500 caracteres (puntos de código). `idioma`: el de la interfaz (`es` o `en`; otro valor, `es`). `intereses`: las categorías de interés del perfil (RF53), opcionales; solo se usan para el criterio de RF36 y no se guardan.
- Cada mensaje válido consume uno de los **20 del día** del turista; el 21 → **429** `LIMITE_MENSAJES` (sin consultar el catálogo).

**200**

```json
{
  "respuesta": "Te propongo un itinerario de 1 día con lo que encontré en Samaria360. Puedes guardarlo en tu perfil.",
  "sugerencias": [
    { "tipo": "Servicio", "id": "6a1f…", "nombre": "Tour Tayrona", "categoria": "Tour",
      "criterio": "PREFERENCIAS", "justificacion": "Coincide con tus intereses declarados (Tour)." }
  ],
  "itinerario": [
    { "orden": 1, "tipo": "servicio", "refId": "6a1f…", "tituloActividad": "Tour Tayrona", "diaSugerido": 1 }
  ],
  "fuentes": ["Clima"],
  "catalogoDisponible": true,
  "mensajesRestantes": 19
}
```

- `sugerencias`: hasta 6, ordenadas por `criterio` (`PREFERENCIAS`, `POPULARIDAD`, `DISPONIBILIDAD`; DEC-298) y, dentro de cada uno, por calificación. Los atractivos que no están `Abierto` no se sugieren.
- `itinerario`: vacío si el mensaje no pide uno; si lo pide, de 1 a 7 días con hasta 3 pasos por día, tomados de las sugerencias.
- `fuentes`: las categorías de las entradas de la base de conocimientos usadas (RF05).
- `catalogoDisponible: false` si ms-catalog no respondió (500 ms de conexión, 1,5 s de lectura): la respuesta usa solo la base de conocimientos y lo dice.
- El texto de `respuesta` lo redacta el modelo configurado (reglas o Gemini) **solo** con ese contexto; al modelo no le llegan datos del turista.

## Itinerarios (RF35)

Solo el **Turista**; siempre los del `sub` del token. Un itinerario ajeno o inexistente → 404 `ITINERARIO_NO_ENCONTRADO`.

| Método y ruta | Respuesta |
|---|---|
| `GET /api/v1/chatbot/itinerarios` | **200** `{elementos}`, del más reciente al más antiguo (máximo 20) |
| `GET /api/v1/chatbot/itinerarios/{id}` | **200** el itinerario |
| `POST /api/v1/chatbot/itinerarios` | **201** el itinerario creado; con 20 guardados, 409 `LIMITE_ITINERARIOS` |
| `PUT /api/v1/chatbot/itinerarios/{id}` | **200** el itinerario (reemplazo completo) |
| `DELETE /api/v1/chatbot/itinerarios/{id}` | **204** |

Cuerpo: `{nombre, items: [{orden, tipo, refId, tituloActividad, diaSugerido}]}`. Itinerario: `{id, nombre, items, fechaCreacion, fechaActualizacion}`.

- `nombre` de 1 a 100; de 1 a 30 `items`; `tipo` `servicio` o `atractivo`; `refId` un ObjectId (24 hexadecimales), sin repetir el mismo `tipo` y `refId`; `tituloActividad` de 1 a 120; `diaSugerido` de 1 a 7. `orden` se renumera desde 1. Errores en `campos` con nombres anidados (`items[2].refId = DUPLICADO`).

## Base de conocimientos (RF05)

Solo el **Administrador**.

| Método y ruta | Respuesta |
|---|---|
| `GET /api/v1/chatbot/admin/conocimiento?pagina=0` | **200** `{elementos, pagina, tamano, totalElementos}`, de 10 en 10, por categoría |
| `POST /api/v1/chatbot/admin/conocimiento` | **201** la entrada; con 500 entradas, 409 `LIMITE_CONOCIMIENTO` |
| `PUT /api/v1/chatbot/admin/conocimiento/{id}` | **200** la entrada |
| `DELETE /api/v1/chatbot/admin/conocimiento/{id}` | **204**; inexistente, 404 `CONOCIMIENTO_NO_ENCONTRADO` |

Cuerpo: `{contenido, categoria}` (contenido de 1 a 2000 y categoría de 1 a 50, recortados). Entrada: `{id, contenido, categoria, fechaActualizacion}`. El asistente usa hasta 3 entradas por respuesta: las que contienen alguna palabra del mensaje o su categoría de atractivo, de la más reciente a la más antigua.

## Datos (`chatbot_db`, C001)

Esquemas en [`modelo-nosql/chatbot_db/`](../modelo-nosql/chatbot_db/): `itinerarios_sugeridos` y `base_conocimiento_turistico` (§2.4 del Entregable 2, con `nombre`, `diaSugerido` de 1 a 7 y `fechaActualizacion`), `uso_diario` (`{turistaId, dia, mensajes}`, único por turista y día) y `cuentas` (proyección para el claim `ver`).
