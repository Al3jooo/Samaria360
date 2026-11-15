# ADR-002 — Elección de tipo de base de datos

- **Estado:** Aceptado
- **Origen:** SAD del proyecto (documento fuera del repositorio); transcrito al repositorio el 2026-11-13
- **Componentes:** `ms-auth`, `ms-booking` (PostgreSQL), `ms-catalog`, `ms-analytics` y `ms-chatbot` (MongoDB;
  los dos últimos desde el plan del 100 %, DEC-324)

## Contexto

Samaria360 maneja información altamente estructurada y crítica (transacciones de reserva, inventario de reservas) y, al
mismo tiempo, información dinámica de estructura heterogénea (el catálogo de un hotel frente al de un tour).

## Decisión

Implementar persistencia políglota con PostgreSQL (SQL) y MongoDB (NoSQL).

## Consecuencias

**Positivas:** PostgreSQL (`ms-auth`, `ms-booking`) garantiza transacciones ACID y evita la sobreventa de cupos.
MongoDB (`ms-catalog`, `ms-chatbot`) permite usar JSON Schemas para validar entidades polimórficas (atractivos,
servicios, FAQs) y hacer consultas geoespaciales nativas para los mapas.

**Negativas:** incrementa la complejidad del despliegue, pues exige levantar y mantener varios contenedores de bases de
datos.

## Estado en el prototipo (Construcción)

- `auth_db` y `reservas_db` son dos bases PostgreSQL distintas (Flyway); `catalog_db` es MongoDB con validadores
  `$jsonSchema` (`infra/mongo/migraciones/`) e índice `2dsphere` para la búsqueda geoespacial
  ([ADR-004](ADR-004-busqueda-combinada.md)). Modelo en [`modelo-logico.dbml`](../diagramas/modelo-logico.dbml) y
  [`modelo-nosql/`](../modelo-nosql/).
- La sobreventa se evita con un UPDATE condicional y un CHECK sobre `inventario_cupo`, un día por sentencia, dentro de
  una transacción ([ADR-007](ADR-007-reservas-y-catalogo.md)).
- ~~`ms-chatbot` está fuera del alcance del prototipo (DEC-253): no existe su contenedor.~~ **Enmienda DEC-324:**
  con el plan del 100 % (DEC-278) existen `ms-chatbot` (`chatbot_db`, DEC-279) y `ms-analytics` (`analytics_db`,
  DEC-292), los dos en MongoDB con validadores `$jsonSchema` (`infra/mongo/chatbot/` e `infra/mongo/analytics/`), como
  fija la tabla 1 del Entregable 2. MongoDB corre como réplica `rs0` de un nodo para las transacciones del *outbox*
  (DEC-281).
