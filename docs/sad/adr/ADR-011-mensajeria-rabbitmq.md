# ADR-011 — Mensajería asíncrona con RabbitMQ: outbox transaccional, relevo y consumidores idempotentes

- **Estado:** Aceptada
- **Fecha:** 2026-11-14
- **Fase:** Construcción (RUP), plan del 100 % (iteración F, PR F0)
- **Componentes:** RabbitMQ (contenedor nuevo del diagrama de despliegue del Entregable 2), `ms-auth`, `ms-booking` y `ms-catalog` (publican), `ms-analytics`, `ms-catalog` y `ms-chatbot` (consumen)
- **Requisitos:** RF13, RF14, RF17, RF30, RF31, RF61, RF63, RF64 (los que dependen de eventos); RNF01, RNF02
- **Registra las decisiones:** DEC-281 (MongoDB como réplica de un solo nodo), DEC-283 (consultas agregadas por minuto) y DEC-284 (mensajería); precisa el nivel 3 de [ADR-003](ADR-003-estrategia-conectividad.md)
- **Contrato:** [`docs/sad/api/eventos.md`](../api/eventos.md) y [`eventos.schema.json`](../api/eventos.schema.json)

## Contexto

- ADR-003 fija tres niveles de conectividad; el tercero, **RabbitMQ para la comunicación asíncrona**, no estaba implementado (DEC-278). El Entregable 2 (figura 20) conecta a RabbitMQ con AMQP a ms-auth, ms-booking, ms-catalog y ms-analytics, y argumenta que el bróker evita que las operaciones secundarias (auditoría, correos) bloqueen la respuesta al turista.
- El plan del 100 % (decisión del equipo del 2026-11-14) necesita eventos para la auditoría (RF13), las notificaciones (RF30, RF63), las calificaciones (RF17 y el promedio de RF61) y los tableros (RF14, RF31, RF64). La decisión 6 del equipo fija que **ms-analytics arma sus vistas con los eventos y no lee bases ajenas**.
- ADR-008 ya resolvió la entrega confiable de un cambio con un **outbox transaccional** en `auth_db` y un relevo HTTP con reintentos, y anticipó que el relevo podría publicar en una mensajería. Ese relevo HTTP **se mantiene** (DEC-278): la proyección de cuentas de ms-catalog y ms-booking no cambia.
- La decisión 8 del equipo fija las propiedades: exchange topic versionado, colas durables con *dead-letter*, *publisher confirms*, entrega al menos una vez con deduplicación por `idEvento` y **ningún dato personal en los eventos**.

## Decisión

1. **Topología.** Un *virtual host* `samaria360` con el exchange **`samaria360.eventos.v1`** (topic, durable). Clave de enrutamiento `<origen>.<agregado>.<suceso>` (por ejemplo, `booking.reserva.confirmada`). Una cola durable por consumidor, **`<servicio>.eventos.v1`**, enlazada con las claves que necesita. Cada cola declara `x-dead-letter-exchange: samaria360.eventos.v1.dlx` (topic) y `x-dead-letter-routing-key: <cola>`; su cola de mensajes fallidos es `<cola>.dlq`. Colas clásicas durables (un solo nodo); los mensajes, persistentes (`deliveryMode = 2`).
2. **Sobre común** (contrato en `eventos.md`): `idEvento` (UUID), `tipo`, `version`, `origen`, `ocurridoEn` (instante UTC), `clave` (id del agregado) y `datos`. Propiedades AMQP: `messageId = idEvento`, `type = tipo`, `contentType = application/json`.
3. **Publicación con outbox transaccional.** El evento se guarda en la base del servicio **en la misma transacción** que el cambio de negocio y un relevo lo publica después:
   - **ms-auth:** el outbox de ADR-008 (`outbox_evento`, `outbox_entrega`) suma el destino `rabbitmq`; el relevo HTTP sigue igual para `ms-catalog` y `ms-booking` (un destino caído no bloquea a los demás).
   - **ms-booking:** outbox propio en `reservas_db` (V7), con el mismo diseño.
   - **ms-catalog:** colección `outbox_eventos` en `catalog_db` (M014), escrita en una transacción de MongoDB: por eso MongoDB pasa a ser una **réplica de un solo nodo** (DEC-281).
   - **ms-analytics:** colección `outbox_eventos` en `analytics_db` para lo que publica (el promedio de RF61).
4. **Relevo.** Toma las entregas pendientes (`FOR UPDATE SKIP LOCKED` en PostgreSQL; arriendo con `findOneAndUpdate` en MongoDB), publica con ***publisher confirms*** y `mandatory = true`, y marca la entrega solo con el *ack* del bróker. Un *nack*, un mensaje devuelto o un plazo vencido (5 s) se reintenta con espera creciente. Purga a los 30 días de entregado, como DEC-272.
5. **Consumo al menos una vez, idempotente.** *Ack* manual y *prefetch* de 10. Cada consumidor registra el `idEvento` en su colección **`eventos_procesados`** (`_id = idEvento`, TTL de 30 días) **en la misma transacción** que el efecto; un duplicado se reconoce y se descarta. Las proyecciones de estado aplican además «solo si la versión es mayor» (como ADR-008). Tres intentos en el proceso (1, 5 y 25 s); después, `reject` sin reencolar: el mensaje pasa a la `.dlq`, que se revisa a mano.
6. **Orden.** No se garantiza un orden global ni por clave (los reintentos lo alteran). Cada evento se diseña para aplicarse en cualquier orden: versión monótona en las proyecciones y contadores deduplicados.
7. **Sin datos personales.** Los eventos no llevan nombres, correos, teléfonos, documentos, coordenadas de un usuario ni texto libre escrito por usuarios (comentarios, justificaciones, motivos). Se permiten los **identificadores UUID** de las cuentas (seudónimos: solo ms-auth los asocia a una persona), los identificadores y datos comerciales públicos (el nombre de un servicio) y el país de origen del turista en código ISO 3166-1 alfa-2, que la decisión 6 del equipo necesita para «visitantes por zona» (RF14). Los datos de contacto se piden a ms-auth por un endpoint interno cuando hacen falta (DEC-280), nunca viajan en un evento.
8. **Versionado.** Un cambio compatible (un campo opcional nuevo) conserva `version`; los consumidores ignoran los campos desconocidos. Un cambio incompatible sube `version` y el productor publica las dos versiones mientras haya consumidores de la anterior; un cambio de topología usa un exchange `.v2`.
9. **Seguridad.** Un usuario de RabbitMQ por servicio con permisos mínimos (como DEC-275): los productores solo escriben en el exchange; cada consumidor solo lee su cola. Credenciales en `.env` (`RABBITMQ_*`), nunca en el repositorio. La consola de administración se publica solo en 127.0.0.1 y solo en desarrollo. El gateway no enruta nada hacia RabbitMQ.
10. **Dependencias.** `spring-boot-starter-amqp` (Spring AMQP, versión gestionada por Spring Boot 3.5) en los servicios que publican o consumen, justificada por ADR-003; `org.testcontainers:rabbitmq` solo en pruebas.

## Alternativas descartadas

- **Llamadas HTTP directas a ms-analytics** desde cada servicio: acopla la respuesta al turista a la disponibilidad de la analítica, contra el argumento del Entregable 2 y RNF01.
- **Publicar en RabbitMQ dentro de la transacción de negocio** (sin outbox): un fallo entre el *commit* y la publicación pierde el evento, o lo publica sin que el cambio exista.
- **Captura de cambios (CDC) con Debezium o Kafka:** agrega contenedores fuera del diagrama de despliegue.
- **Reemplazar el relevo HTTP de las cuentas** por RabbitMQ: ADR-008 funciona y está probado; cambiarlo no suma ningún RF (DEC-278).

## Consecuencias

- **Positivas:** la reserva, la cancelación y las decisiones del Administrador no esperan a la analítica ni al correo; un consumidor caído acumula mensajes en su cola y se pone al día al volver; ms-analytics no lee bases ajenas.
- **Negativas:** consistencia eventual (los tableros y la campana van unos segundos detrás); un contenedor más que operar (RabbitMQ) y la réplica de MongoDB (DEC-281); colecciones técnicas que no están en la tabla 1 del Entregable 2 (`outbox_eventos`, `eventos_procesados`), registradas en DEC-284.
- **Riesgo:** un mensaje en la `.dlq` no se reprocesa solo; queda listado en la evidencia de la iteración y se revisa a mano.

## Cumplimiento

- **Contrato:** `eventos.schema.json` valida cada evento publicado en las pruebas del productor y cada ejemplo de `eventos.md`.
- **Productores:** Testcontainers con PostgreSQL o MongoDB y RabbitMQ: el evento existe solo si la transacción se confirma; el relevo no marca una entrega sin *ack*; un bróker caído no hace fallar la operación de negocio.
- **Consumidores:** un mensaje duplicado no repite el efecto; un mensaje inválido termina en la `.dlq` después de tres intentos; los eventos aplicados en desorden dejan el mismo estado.
- **ArchUnit:** `org.springframework.amqp..` solo en `infrastructure` (como `io.github.resilience4j..` en ms-booking).
- **Datos personales:** una prueba por productor verifica que el JSON publicado no contiene los campos prohibidos del punto 7.

## Cuándo revisarla

- Si un servicio se escala a varias instancias (revisar el arriendo del relevo y la deduplicación).
- Si el volumen de `ConsultasAgregadas` (DEC-283) o de reservas hace que una cola crezca sin drenarse.
- Si se necesita reprocesar automáticamente la `.dlq` o un orden estricto por clave.
