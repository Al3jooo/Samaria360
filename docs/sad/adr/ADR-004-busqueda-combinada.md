# ADR-004 — Búsqueda combinada de servicios y atractivos en MongoDB

- **Estado:** Aceptada
- **Fecha:** 2026-10-11
- **Fase:** Elaboración (RUP), iteración de la vista principal del catálogo
- **Caso de uso:** Buscar oferta turística (RF16, RF23, RF38, RF50, RF61)
- **Componente:** ms-catalog, puerto de salida `OfertaRepositoryPort`

## Contexto

WF-05 muestra **una sola lista** de resultados con servicios comerciales (colección `servicios`) y atractivos públicos (colección `atractivos`) de `catalog_db`. Esa lista debe:

- aplicar filtros combinables (texto, categoría, precio máximo, distancia y accesibilidad) según las decisiones registradas en `docs/trazabilidad.md`;
- ordenarse por distancia (RF38, RF61), por precio (RF61) o por nombre (orden por defecto sin ubicación);
- paginarse de 10 en 10 (RF16) con un total exacto;
- responder en menos de 2 s (RNF02) con 500 usuarios concurrentes (RNF03).

Las dos colecciones son del modelo NoSQL del Entregable 2 (`docs/sad/modelo-nosql/`) y tienen un índice `2dsphere` sobre `coordenadas`. La distancia se calcula con `$geoNear`, que debe ser la primera etapa de un pipeline.

## Alternativas

### A. Una sola agregación: `$geoNear` + `$unionWith`

`servicios.aggregate([ $geoNear | $match, $project, $unionWith{ atractivos: [$geoNear | $match, $project] }, $sort, $facet ])`

- **Exactitud de la paginación:** exacta. Hay un único flujo ordenado con desempate estable por `_id` (sin repetidos ni faltantes entre páginas) y un total exacto con `$facet` y `$count` en la misma consulta.
- **Rendimiento (RNF02):** una sola ida a la base. Cada `$geoNear` usa el índice `2dsphere` y aplica los filtros en su opción `query`. El `$sort` final ordena en memoria solo lo ya filtrado y proyectado: cientos o pocos miles de documentos en el catálogo de una ciudad, que se ordenan en milisegundos.
- **Complejidad:** media. Toda la lógica está en un pipeline. Los nulos se ubican al final del orden por precio con un campo auxiliar.
- **Impacto en el SAD:** ninguno.
- **Riesgo:** la documentación de MongoDB 7.0 no dice si `$geoNear` se permite dentro de `$unionWith`.

### B. Dos consultas y mezcla ordenada en memoria

- **Exactitud de la paginación:** exacta, pero para la página N (de tamaño 10) hay que traer los primeros (N + 1) × 10 de **cada** colección, mezclarlos y descartar N × 10. El total exige dos `countDocuments` más.
- **Rendimiento:** cuatro idas a la base por búsqueda, con un costo que crece linealmente con el número de página.
- **Complejidad:** alta. El comparador en Java debe replicar exactamente el orden de MongoDB (colación en español, nulos al final, desempates); cualquier diferencia repite o salta elementos entre páginas.
- **Impacto en el SAD:** ninguno.

### C. Colección de lectura `oferta`

- **Exactitud de la paginación:** exacta y con la consulta más simple: un `$geoNear` o un `find` ordenado sobre una sola colección.
- **Rendimiento:** el mejor a gran escala, con índices compuestos y `2dsphere` propios del modelo de lectura.
- **Complejidad:** alta en la escritura. Hay que sincronizar `oferta` en cada alta o cambio de servicio o atractivo y cuando cambia el estado de la cuenta de un prestador (`prestadorActivo`), con consistencia eventual. Esos casos de uso de escritura todavía no existen en el prototipo.
- **Impacto en el SAD:** agrega una colección fuera del Entregable 2.

## Decisión

Se adopta la **alternativa A**, con dos formas del pipeline:

1. **Con ubicación del turista:** `$geoNear` en `servicios` y otro `$geoNear` como primera etapa del pipeline de `$unionWith` sobre `atractivos`. La distancia máxima va en `maxDistance` (metros) y los demás filtros en `query`, para que usen el índice.
2. **Sin ubicación:** `$match`, `$unionWith` (con `$match`) y `$sort`.

En ambas formas hay **siempre un `$sort` final** con desempate estable por `_id`, porque `$unionWith` no garantiza el orden de salida. El orden por nombre usa la colación `es` (nivel 1: no distingue mayúsculas ni tildes). La paginación y el total salen de un `$facet`. `$unionWith` se omite cuando la búsqueda excluye los atractivos (por filtro de categoría o de precio).

### Verificación de `$geoNear` dentro de `$unionWith`

La documentación no menciona este uso. La página de `$unionWith` solo prohíbe `$out` y `$merge` dentro de su pipeline, y la de `$geoNear` exige que sea la primera etapa de un pipeline y muestra un ejemplo dentro de `$lookup`:

- [MongoDB 7.0 — `$unionWith`](https://www.mongodb.com/docs/v7.0/reference/operator/aggregation/unionWith/)
- [MongoDB 7.0 — `$geoNear`](https://www.mongodb.com/docs/v7.0/reference/operator/aggregation/geoNear/)

Por eso se **verificó con una prueba** antes de implementar el adaptador: `GeoNearEnUnionWithFactibilidadTest` (ms-catalog). La prueba usa Testcontainers con la imagen `mongo:7.0` de docker-compose (MongoDB **7.0.43** al momento de la verificación), los scripts de `infra/mongo/init` y la semilla. Comprueba que:

- llegan los 11 servicios visibles y los 7 atractivos, cada uno con su distancia calculada;
- las distancias son coherentes con la geografía: Hotel Casa Tayrona y la Catedral están a menos de 1 km del Parque de los Novios, y la Cascada El Salto, en Minca, a entre 8 y 20 km;
- el `$sort` final intercala ambas colecciones.

Si una versión futura de MongoDB dejara de admitirlo, esa prueba fallará y se pasará a la alternativa B, sin cambiar el puerto.

## Consecuencias

- **Límite de memoria:** cada etapa de agregación tiene un límite de **100 MB** de memoria. El `$sort` y el `$facet` trabajan sobre los documentos ya filtrados y proyectados a los campos del resultado (unos cientos de bytes por elemento), así que el catálogo de la ciudad queda muy por debajo. No se activa `allowDiskUse`; se activará en el adaptador si una prueba de carga muestra que una consulta se acerca al límite.
- **Índices:** no se agregan. `$geoNear` usa los `2dsphere` existentes; los filtros de `servicios` coinciden con el índice compuesto `busqueda_publica`. La búsqueda de texto con una expresión regular sin anclar no aprovecharía un índice. El orden por nombre con colación `es` no puede usar `busqueda_publica` (que tiene colación simple) en la forma sin ubicación; al tamaño del prototipo es aceptable, y si hiciera falta se agregará un índice con colación `es`.
- **Texto del usuario:** se escapa antes de usarlo como expresión regular y se limita a 100 caracteres (`CriteriosBusqueda`), lo que evita patrones costosos. Desde DEC-274 (2026-11-13) tampoco distingue tildes: cada vocal, «n» y «c» pasan a una clase con sus variantes (`TextoBusqueda.patronSinTildes`), sin campos normalizados en el esquema.
- **Evolución prevista:** si las pruebas de carga contra RNF02 y RNF03 lo exigen, se adoptará la **alternativa C** (colección de lectura `oferta`) **detrás de `OfertaRepositoryPort`**. Solo cambiaría el adaptador de salida; el dominio, la aplicación y la API no se modifican. Requerirá actualizar el modelo NoSQL del SAD y definir la sincronización con los casos de uso de escritura.
