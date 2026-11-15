# CU «Editar galería del servicio»

Fase de Construcción (RUP), plan del 100 %, iteración I. Reglas: DEC-313 y ADR-009.
- **Pantallas:** WF-18 «Mis servicios», detalle de un servicio Publicado, sección «Galería» (propuesta por arquitectura).
- **Contrato:** [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), sección «Historial de modificaciones y galería (iteración I)».

| Campo | Valor |
|---|---|
| **Caso de uso** | «Editar galería del servicio» |
| **Actores** | Prestador |
| **Requisitos que traza** | RF48 |
| **Alcance** | ms-catalog (`servicios.imagenes`, GridFS `imagenes`) |

## Reglas de negocio

- **RF48 (SRS):** el Prestador edita la galería de un servicio previamente aprobado: adjunta fotografías nuevas o elimina las existentes, siempre de 1 a 5 imágenes JPEG o PNG.
- **DEC-313:** solo servicios Publicados del Prestador; una imagen por subida, de hasta 5 MB (**decisión del equipo, no del SRS**), validada por firma; sin nueva aprobación.

## Flujo básico

1. El Prestador abre el detalle de un servicio Publicado; la sección «Galería» muestra las imágenes.
2. Elige «Agregar imagen» y selecciona un archivo JPEG o PNG.
3. ms-catalog valida la imagen, la guarda en GridFS y la agrega a la galería; responde con la galería nueva, que la ficha pública muestra de inmediato.
4. El Prestador quita una imagen con «Quitar»; ms-catalog la saca de la galería y la borra de GridFS.

## Flujos alternativos

- **FA1 — Ya hay 5 imágenes:** «Agregar imagen» está deshabilitado; si dos subidas compiten, 409 `LIMITE_IMAGENES` y la imagen guardada se borra (compensación).
- **FA2 — Queda una sola imagen:** «Quitar» está deshabilitado; el servidor responde 409 `IMAGEN_MINIMA`.
- **FA3 — Archivo que no es JPEG o PNG, o de más de 5 MB:** la pantalla no lo envía; el servidor responde 400 `DATOS_INVALIDOS` (o 413).
- **FA4 — Servicio no Publicado:** 409 `ESTADO_CAMBIADO`; servicio ajeno: 404 `SERVICIO_NO_ENCONTRADO`.
