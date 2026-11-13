# ADR-010 — Mantener la línea Spring Boot 3.5 durante la fase de Construcción

- **Estado:** Aceptada
- **Fecha:** 2026-11-05
- **Fase:** Construcción (RUP), después de la iteración E «Atractivos y agenda»
- **Componentes:** `ms-auth`, `ms-catalog`, `ms-booking` y `api-gateway` (Spring Boot 3.5.16; Spring Cloud 2025.0.3 en
  `ms-booking` y `api-gateway`; Java 17)
- **Requisitos:** RNF05 (seguridad), RNF01 (disponibilidad)

## Contexto

El Entregable 1 fija «Spring Boot 3» como tecnología de los microservicios Java. Los cuatro servicios usan el mismo
*parent*, `spring-boot-starter-parent` 3.5.16, la última versión OSS de la línea 3.x.

El soporte OSS de la línea 3.5 terminó el 30/06/2026. Desde esa fecha, las correcciones de seguridad de esa línea solo
llegan con soporte comercial. La línea vigente es Spring Boot 4 (Spring Framework 7, Spring Cloud 2025.1), que cambia
APIs que el prototipo usa: Jackson 3, la configuración de Spring Security y las propiedades de Spring Cloud Gateway.

El código es el prototipo arquitectónico ejecutable, que solo se ejecuta en local con fines académicos (README,
«Arranque en un comando»). No hay un despliegue con tráfico real.

## Decisión

Mantener Spring Boot 3.5.16 en los cuatro servicios hasta el final de la fase de Construcción.

- Las versiones de Spring Boot y Spring Cloud no cambian dentro de una iteración; cualquier cambio de línea es una
  iteración propia, con todas las pruebas (`./mvnw test`) en verde.
- Las dependencias que no gestiona el BOM (`springdoc-openapi`, `archunit`) siguen la versión compatible con Boot 3.5.

## Alternativas descartadas

- **Migrar a Spring Boot 4 ahora.** Toca los cuatro servicios a la vez (Jackson 3, Spring Security 7, prefijos de
  Spring Cloud Gateway) en mitad de la Construcción y pone en riesgo la línea base que el prototipo debe validar. El
  beneficio, recibir correcciones de seguridad, no aplica a un prototipo que no se publica.
- **Bajar a una línea anterior con soporte.** No existe: la 3.5 es la última de la 3.x.

## Consecuencias

- **Positivas:** la línea base de la arquitectura no cambia; las decisiones y pruebas de las iteraciones A a E siguen
  siendo válidas.
- **Negativas:** el prototipo no recibe correcciones de seguridad de Spring. No debe desplegarse con tráfico real sin
  migrar antes.

## Cuándo revisar esta decisión

- Al pasar a la fase de Transición, o antes de cualquier despliegue fuera del entorno local.
- Si se publica una vulnerabilidad que afecte a un componente usado por el prototipo (Spring Security, Spring Cloud
  Gateway, Tomcat, Reactor Netty) y no tenga mitigación por configuración.
