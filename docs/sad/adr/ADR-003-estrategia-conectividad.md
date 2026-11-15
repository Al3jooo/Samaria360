# ADR-003 — Estrategia de manejo de conectividad

- **Estado:** Aceptado
- **Origen:** SAD del proyecto (documento fuera del repositorio); transcrito al repositorio el 2026-11-13
- **Componentes:** `api-gateway` y todos los microservicios
- **Enmienda DEC-278 (2026-11-14):** ms-notificaciones no existe en el Entregable 2 (ver al final)
- **Enmienda DEC-280 (2026-11-14):** conexiones internas nuevas del nivel 2 (ver al final); el nivel 3 se precisa en [ADR-011](ADR-011-mensajeria-rabbitmq.md)

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
  servicios. *(Precisado por la enmienda DEC-278.)*

## Enmienda DEC-278 (2026-11-14): ms-notificaciones no existe

El Entregable 2 (tabla 1 de colecciones y figuras 19 y 20) tiene cinco microservicios: ms-auth, ms-catalog, ms-booking, ms-analytics («Análisis y notificaciones») y ms-chatbot, más RabbitMQ. No tiene ms-notificaciones. Cada responsabilidad que el repositorio le atribuía pasa a un contenedor del diagrama de despliegue:

- **Auditoría (RF13), notificaciones internas (RF63) y aviso al Prestador (RF30, con su correo por SMTP):** ms-analytics, que los recibe por RabbitMQ (nivel 3).
- **Correo de recuperación de contraseña (RF34) y cualquier otro correo de las cuentas:** ms-auth, por SMTP directo.
- **«Mensajería» o «bróker»:** RabbitMQ.

El nivel 3 entra en el alcance con el plan del 100 % (decisión del equipo del 2026-11-14). El relevo HTTP de las cuentas de [ADR-008](ADR-008-sincronizacion-estado-cuentas.md) se mantiene.

## Enmienda DEC-280 (2026-11-14): conexiones internas nuevas

El plan del 100 % agrega al diagrama de despliegue ([`despliegue.puml`](../diagramas/despliegue.puml)) conexiones que la figura 20 no dibuja, sin contenedores nuevos:

- **Nivel 2 (REST):** ms-chatbot → ms-catalog (búsqueda y detalles públicos, sin token ni endpoints nuevos); ms-analytics → ms-auth (contacto del Prestador, RF30) y ms-booking → ms-auth (contactos de los asistentes, RF42), por un endpoint interno con JWT de servicio que emite ms-auth; JWKS de ms-analytics y ms-chatbot hacia ms-auth.
- **Nivel 3 (RabbitMQ):** RabbitMQ → ms-chatbot (`CuentaActualizada`, para validar el claim `ver`). La topología, el outbox y las garantías están en [ADR-011](ADR-011-mensajeria-rabbitmq.md).

Ninguna copia datos del catálogo ni de contactos. El gateway sigue sin enrutar `/internal/**`.
