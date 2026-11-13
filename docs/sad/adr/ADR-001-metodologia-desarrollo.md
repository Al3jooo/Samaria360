# ADR-001 — Metodología de desarrollo

- **Estado:** Aceptado
- **Origen:** SAD del proyecto (documento fuera del repositorio); transcrito al repositorio el 2026-11-13
- **Componentes:** todos (`ms-auth`, `ms-catalog`, `ms-booking`, `api-gateway`, `frontend`)

## Contexto

El proyecto utiliza RUP, un marco iterativo, incremental y centrado en la arquitectura. Se necesitaba un estilo
arquitectónico que permitiera avanzar por iteraciones sin que el equipo se bloqueara entre sí.

## Decisión

Adoptar una arquitectura de microservicios con diseño hexagonal.

## Consecuencias

**Positivas:** permite aislar los dominios en módulos independientes. Durante las fases de Elaboración y Construcción,
el equipo puede desarrollar, probar y desplegar un microservicio (p. ej., `ms-auth`) en una iteración sin afectar el
desarrollo concurrente de los demás.

**Negativas:** obliga al equipo a establecer contratos de API (JSON) desde la fase de Inicio para que las iteraciones no
pierdan sincronía.

## Estado en el prototipo (Construcción)

- Cada carpeta raíz es un contenedor del diagrama de despliegue, sin `pom.xml` raíz ni librería compartida; cada
  servicio se compila y se prueba con su propio `./mvnw`.
- Las capas `domain`, `application` e `infrastructure` y sus reglas de dependencia se verifican con ArchUnit
  (`HexagonalArchitectureTest` de cada servicio). El `api-gateway` no tiene dominio y no aplica la estructura hexagonal.
- Los contratos de API están en [`docs/sad/api/`](../api/).
