# ADR-003 — Estrategia de manejo de conectividad

- **Estado:** Aceptado
- **Origen:** SAD del proyecto (documento fuera del repositorio); transcrito al repositorio el 2026-11-13
- **Componentes:** `api-gateway` y todos los microservicios

## Contexto

Al distribuir el sistema en cinco microservicios, la comunicación directa entre el cliente y cada microservicio
expondría la red interna, complicaría el frontend y generaría fallos en cascada si un servicio colapsa.

## Decisión

Establecer una estrategia de conectividad en tres niveles:

1. API Gateway para la entrada externa;
2. REST para la comunicación síncrona interna;
3. RabbitMQ para la asíncrona.

## Consecuencias

**Positivas:**
- **Seguridad:** el API Gateway enruta, aplica circuit breaker y CORS; cada microservicio valida el JWT con el JWKS de
  `ms-auth`, de modo que ninguna petición se acepta solo por venir de la red interna (Zero-Trust).
- **Desacoplamiento:** el bróker de mensajes (RabbitMQ) garantiza que operaciones secundarias (logs de auditoría en
  `ms-analytics` o envío de correos) no bloqueen el tiempo de respuesta al turista cuando hace una reserva.

**Negativas:** el SAD del proyecto solo registra consecuencias positivas para esta decisión (confirmado por el equipo el 2026-11-13).

## Estado en el prototipo (Construcción)

- **Nivel 1 (implementado):** `api-gateway` (Spring Cloud Gateway) es el único punto de entrada; no valida el JWT y no
  enruta `/internal/**` ([ADR-005](ADR-005-api-gateway.md)).
- **Nivel 2 (implementado):** REST síncrono de `ms-booking` a `ms-catalog` para el detalle y la oferta
  ([ADR-007](ADR-007-reservas-y-catalogo.md)), y de `ms-auth` a los endpoints internos de `ms-catalog` y `ms-booking`.
- **Nivel 3 (no implementado):** el prototipo no tiene RabbitMQ. La sincronización del estado de las cuentas usa un
  outbox transaccional en `auth_db` con un relevo HTTP y reintentos ([ADR-008](ADR-008-sincronizacion-estado-cuentas.md));
  `ms-analytics` y `ms-notificaciones` están fuera del alcance (RF13, RF14, RF30, RF63). El bróker llega con esos
  servicios.
