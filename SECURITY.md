# Política de seguridad

## Alcance

Samaria360 es un **prototipo arquitectónico académico** (RUP, fase de Construcción). Se ejecuta en local y, para la
sustentación, en una demostración temporal; no tiene un despliegue con tráfico real y no debe usarse en producción:

- usa Spring Boot 3.5, sin soporte OSS desde el 30/06/2026 ([ADR-010](docs/sad/adr/ADR-010-linea-spring-boot-3-5.md));
- Docker Compose activa el perfil `dev`, que carga usuarios de prueba con una contraseña conocida (README, «Arranque
  en un comando»); fuera de Compose ese perfil solo se activa si se pide;
- el asistente (ms-chatbot) puede usar el nivel gratuito de Gemini, que puede usar lo enviado para entrenar: al
  modelo no le llegan datos personales del turista, pero sí el texto que escribe (DEC-297).

Estos puntos son conocidos y no hace falta reportarlos.

## Reportar una vulnerabilidad

No abras un *issue* público. Repórtala en privado desde la pestaña **Security → Report a vulnerability** del
repositorio, con:

- el componente afectado (`frontend`, `api-gateway`, `ms-auth`, `ms-catalog`, `ms-booking`, `ms-analytics`,
  `ms-chatbot` o `rabbitmq`);
- los pasos para reproducirla y su impacto;
- la versión (commit) en que la encontraste.

No incluyas tokens, contraseñas reales ni datos personales en el reporte.

## Prácticas del proyecto

- Contraseñas con BCrypt (RNF05) y JWT RS256 de 15 minutos, renovable con actividad hasta 8 horas (DEC-317), validados
  por cada servicio con el JWKS de ms-auth y el claim `ver` (una sesión se invalida al cambiar la cuenta).
- Los secretos (`.env`, `infra/keys/`) no se versionan; las llaves se montan como *secrets* de Docker de solo lectura.
- El API Gateway no expone los endpoints `/internal/**`, rechaza rutas no normalizadas y limita la tasa del inicio de
  sesión, el registro y la recuperación de contraseña.
- Cada servicio se conecta a su base con su propio usuario de mínimo privilegio (DEC-275), y a RabbitMQ con su propio
  usuario y permisos mínimos (DEC-284).
- Los eventos de RabbitMQ no llevan datos personales (ADR-011); los servicios internos se autentican con un JWT de
  servicio que emite ms-auth (DEC-280).
