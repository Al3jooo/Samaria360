# Samaria360

[![ms-auth](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-auth.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-auth.yml)
[![ms-catalog](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-catalog.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-catalog.yml)
[![ms-booking](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-booking.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-booking.yml)
[![ms-analytics](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-analytics.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-analytics.yml)
[![ms-chatbot](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-chatbot.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/ms-chatbot.yml)
[![api-gateway](https://github.com/Al3jooo/Samaria360/actions/workflows/api-gateway.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/api-gateway.yml)
[![frontend](https://github.com/Al3jooo/Samaria360/actions/workflows/frontend.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/frontend.yml)
[![compose](https://github.com/Al3jooo/Samaria360/actions/workflows/compose-humo.yml/badge.svg)](https://github.com/Al3jooo/Samaria360/actions/workflows/compose-humo.yml)

Plataforma de turismo de Santa Marta. Proyecto Capstone Design — Arquitectura de Software, Universidad del Magdalena.

- **Metodología:** RUP. Fase actual: Construcción (prototipo arquitectónico ejecutable que valida la línea base de la
  arquitectura; no es un producto completo).
- **Alcance:** los 64 RF del SRS implementados (plan del 100 %, DEC-278); estado de cada RF y RNF en
  [`docs/trazabilidad.md`](docs/trazabilidad.md).
- **Arquitectura:** cinco microservicios con diseño hexagonal interno (ms-auth, ms-catalog, ms-booking, ms-analytics y
  ms-chatbot), detrás de un API Gateway, con eventos asíncronos por RabbitMQ
  ([ADR-011](docs/sad/adr/ADR-011-mensajeria-rabbitmq.md)).
- **Persistencia:** PostgreSQL (`auth_db`, `reservas_db`) y MongoDB (`catalog_db`, `analytics_db`, `chatbot_db`).

| Inicio (WF-04) | Exploración con mapa (WF-05) | Preguntas frecuentes (WF-13) |
|---|---|---|
| ![Pantalla de inicio con la búsqueda y las categorías](docs/recursos-graficos/capturas/inicio.webp) | ![Exploración con filtros, mapa y resultados](docs/recursos-graficos/capturas/explorar.webp) | ![Preguntas frecuentes con buscador](docs/recursos-graficos/capturas/preguntas-frecuentes.webp) |

<sub>Capturas del prototipo con los datos de la semilla. Mapa: © OpenStreetMap contributors.</sub>

## Tecnologías

| Componente | Tecnología | Base de datos |
|---|---|---|
| `frontend` | React 19, Vite, TypeScript, i18next, Leaflet; servido por nginx | — |
| `api-gateway` | Spring Cloud Gateway 4.3 (WebFlux), Resilience4j | — |
| `ms-auth` | Java 17, Spring Boot 3.5, Spring Security (JWT RS256, BCrypt), Flyway | PostgreSQL 16 (`auth_db`) |
| `ms-catalog` | Java 17, Spring Boot 3.5, Spring Data MongoDB, GridFS | MongoDB 7 (`catalog_db`) |
| `ms-booking` | Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, Resilience4j | PostgreSQL 16 (`reservas_db`) |
| `ms-analytics` | Java 17, Spring Boot 3.5, Spring Data MongoDB, Spring AMQP, correo SMTP | MongoDB 7 (`analytics_db`) |
| `ms-chatbot` | Java 17, Spring Boot 3.5, Spring Data MongoDB, Spring AMQP; Gemini como modelo de lenguaje (opcional) | MongoDB 7 (`chatbot_db`) |
| `rabbitmq` | RabbitMQ 4.1: exchange `samaria360.eventos.v1`, una cola por consumidor con su `.dlq` | — |
| `mailpit` | Servidor SMTP de desarrollo (solo perfil `dev`; en la demo, Gmail) | — |

Pruebas: JUnit 5, Testcontainers (PostgreSQL, MongoDB y RabbitMQ) y ArchUnit en los servicios Java; Vitest y Testing Library en el frontend. La línea
Spring Boot 3.5 se mantiene durante la Construcción ([ADR-010](docs/sad/adr/ADR-010-linea-spring-boot-3-5.md)).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/srs/`](docs/srs/) | Especificación de requisitos (fuente única de los RF y RNF) |
| [`docs/sad/adr/`](docs/sad/adr/) | Decisiones de arquitectura (ADR-001 a ADR-011) |
| [`docs/sad/api/`](docs/sad/api/) | Contratos de la API de los cinco microservicios y catálogo de eventos de RabbitMQ (`eventos.md`, con su JSON Schema) |
| [`docs/sad/diagramas/`](docs/sad/diagramas/) y [`docs/sad/modelo-nosql/`](docs/sad/modelo-nosql/) | Modelo lógico (DBML), diagrama de despliegue y esquemas JSON vigentes de `catalog_db`, `analytics_db` y `chatbot_db` |
| [`docs/casos-de-uso/`](docs/casos-de-uso/) | Especificaciones textuales de los casos de uso |
| [`docs/wireframes/`](docs/wireframes/README.md) | Wireframes de las pantallas |
| [`docs/trazabilidad.md`](docs/trazabilidad.md) | Matriz wireframe ↔ caso de uso ↔ requisito ↔ endpoint ↔ componente, y decisiones (DEC) |
| [`docs/demo/reservas/`](docs/demo/reservas/README.md) | Demostración del CU «Reservar servicio» con la prueba de concurrencia |
| [`infra/carga/`](infra/carga/README.md) | Pruebas de los RNF de operación: carga con k6, Lighthouse y tolerancia a fallos (DEC-276) |
| [`frontend/README.md`](frontend/README.md) | Frontend por pantalla y guía visual |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) y [`SECURITY.md`](SECURITY.md) | Flujo de trabajo (ramas, commits, pruebas) y reporte de vulnerabilidades |

## Estructura del monorepo

Cada carpeta raíz corresponde a un contenedor del diagrama de despliegue del SAD.

```
samaria360/
├── .github/workflows/            # CI: un workflow por servicio, filtrado por paths (_java.yml es el común)
├── docs/                         # artefactos RUP versionados con el código (ver «Documentación»)
├── infra/
│   ├── postgres/init/            # 01-crear-bases.sql (auth_db, reservas_db), solo con el volumen vacío
│   ├── postgres/asegurar-bases.sql   # lo ejecuta postgres-bases en cada arranque (bases que falten)
│   ├── mongo/init/               # línea base congelada (v1) de catalog_db: validadores, índices y semilla
│   ├── mongo/migraciones/        # catalog_db: aplicar.js + esquema/ y semilla/ (M001…M018)
│   ├── mongo/analytics/          # analytics_db: A001…A006, cada una con su copia del validador
│   ├── mongo/chatbot/            # chatbot_db: C001
│   ├── rabbitmq/                 # definiciones.json (exchange, colas y .dlq) e iniciar.sh (un usuario por servicio)
│   ├── humo/                     # arranque-infra.sh (primer arranque aislado) y humo.sh (sistema completo)
│   ├── carga/                    # k6, Lighthouse y resiliencia (RNF01 a RNF03)
│   └── keys/                     # llaves RSA del JWT (no se versionan)
├── frontend/                     # React + Vite + TypeScript
│   ├── nginx/                    # default.conf (SPA y proxy /api/) y seguridad.conf (CSP y encabezados)
│   └── src/
│       ├── app/                  # router, diseños, rutas protegidas por rol y tokens visuales (estilos.css)
│       ├── features/             # una carpeta por área: auth, catalogo, reservas, perfil, favoritos,
│       │                         #   contenido, agenda, servicios, prestador, gestor, admin, oferta,
│       │                         #   afluencia, calificaciones, notificaciones, tableros, asistente
│       ├── shared/api/           # httpClient.ts (fetch + token de sesión + ProblemDetail)
│       ├── shared/components/
│       └── i18n/                 # es.json, en.json
├── api-gateway/                  # Spring Cloud Gateway (sin dominio: no aplica la estructura hexagonal)
├── ms-auth/                      # Spring Boot 3 + PostgreSQL (auth_db)
│   └── src/
│       ├── main/java/co/edu/unimagdalena/samaria360/auth/
│       │   ├── domain/           # model/, service/ (p. ej. PoliticaBloqueo), exception/
│       │   ├── application/      # port/in, port/out y service/ (casos de uso)
│       │   └── infrastructure/
│       │       ├── adapter/in/   # web/ (controladores y DTO), programador/ (relevo del outbox)
│       │       ├── adapter/out/  # persistence/, security/ (BCrypt, JWT, JWKS), relevo/, reloj/, notificacion/
│       │       └── config/       # SecurityConfig, BeanConfig
│       ├── main/resources/db/migration/   # esquema y datos de referencia (Flyway)
│       ├── main/resources/db/semilla/     # usuarios de prueba, solo perfiles dev y test
│       └── test/java/.../auth/            # domain/, application/, infrastructure/, architecture/
├── ms-catalog/                   # Spring Boot 3 + MongoDB (catalog_db), misma estructura hexagonal
├── ms-booking/                   # Spring Boot 3 + PostgreSQL (reservas_db), misma estructura hexagonal
├── ms-analytics/                 # Spring Boot 3 + MongoDB (analytics_db): auditoría, notificaciones, calificaciones, tableros
├── ms-chatbot/                   # Spring Boot 3 + MongoDB (chatbot_db): asistente virtual e itinerarios
├── docker-compose.yml
├── .env.example
└── .editorconfig
```

Los servicios no comparten bases: ms-analytics arma sus vistas con los eventos de RabbitMQ y ms-chatbot consulta la
búsqueda pública de ms-catalog ([DEC-280](docs/trazabilidad/decisiones-transversales.md)).

M018 corrige únicamente el texto del horario de la semilla del Tour de Café en Minca para alinearlo con las salidas
reservables de 08:00 y 14:00; no cambia el inventario ni los validadores
([modelo NoSQL](docs/sad/modelo-nosql/README.md), DEC-333).

## Reglas de dependencia (verificadas con ArchUnit)

- `domain` no depende de nada externo: ni Spring, ni JPA, ni Mongo, ni AMQP.
- `application` depende solo de `domain`.
- `infrastructure` implementa los puertos de `application`.
- No hay `pom.xml` raíz ni librería compartida: cada servicio se compila y despliega de forma independiente.

## Arranque en un comando

Levanta todo el sistema desde cero: PostgreSQL, MongoDB, RabbitMQ, los cinco microservicios, el api-gateway, el
frontend y, con el perfil `dev`, Mailpit.

**Requisitos**
- Docker Desktop (Windows/macOS) o Docker Engine (Linux), con Compose v2 (`docker compose`).
- OpenSSL, para generar las llaves del JWT. En Windows viene con Git Bash.
- Unos **6 GB de memoria** en la máquina virtual de Docker (ver «Memoria», abajo). En Docker Desktop se revisa en
  *Settings → Resources*, o en `.wslconfig` con WSL 2.
- Puertos libres en el host, todos solo en 127.0.0.1: **8000** (frontend), **8080** (API Gateway), 5432 y 27017 (bases
  de datos), 5672 y 15672 (RabbitMQ) y 8025 (Mailpit, perfil `dev`). Todos se pueden cambiar en `.env`, salvo el 8080.

**Pasos** (desde la raíz del repositorio, en Git Bash o en una terminal de Linux/macOS)

```bash
cp .env.example .env                       # y cambiar todas las contraseñas y secretos (los «cambiar_esto»)
cd infra/keys                              # llaves RS256 del JWT (no se versionan)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-privada.pem
openssl rsa -in jwt-privada.pem -pubout -out jwt-publica.pem
cd ../..
docker compose up -d --build               # la primera vez tarda varios minutos (descarga dependencias)
docker compose ps                          # esperar a que todos digan (healthy)
```

Abrir **http://localhost:8000**. Usuarios de prueba (perfil `dev`, solo para demostración):
`turista@samaria360.test`, `prestador@samaria360.test`, `gestor@samaria360.test` y `admin@samaria360.test`, todos
con la contraseña `Samaria360*`. El prestador de prueba está validado (migración de semilla V6); un prestador recién
registrado queda Pendiente de Validación y no puede iniciar sesión hasta que el Administrador lo valide (RF28).
`prestador2@samaria360.test` tiene la cuenta Inactiva (403 `CUENTA_INACTIVA` con la contraseña correcta).

Para crear cuentas nuevas: **«Registrarse»** en la pantalla de inicio de sesión (`/registro`, RF01 y RF02). Los
correos (recuperar la contraseña, RF34; reserva nueva al Prestador, RF30) llegan a la bandeja de Mailpit:
**http://localhost:8025**.

**Asistente virtual (WF-07).** Por defecto responde con reglas, sin llave. Para que redacte con Gemini, agregar
`LLM_PROVEEDOR=gemini` y `GEMINI_API_KEY` en `.env` (nunca en el repositorio) y recrear ms-chatbot
(`docker compose up -d ms-chatbot`). Al modelo no le llegan datos personales del turista (DEC-297).

**Cómo queda desplegado**
- El navegador habla solo con el **frontend** (nginx). nginx sirve la SPA y pasa `/api/` al **api-gateway**, que es
  el único punto de entrada a los servicios ([ADR-005](docs/sad/adr/ADR-005-api-gateway.md)).
- En el host se publican, solo en `127.0.0.1`, el 8000 (frontend) y el 8080 (gateway): el sistema no queda expuesto
  a la red local, porque corre con el perfil `dev` y los usuarios de prueba (DEC-266). ms-auth (8081), ms-catalog
  (8082), ms-booking (8083), ms-analytics (8084) y ms-chatbot (8085) solo se alcanzan dentro de la red de Docker.
  PostgreSQL, MongoDB, RabbitMQ (5672 y la consola en 15672) y Mailpit (8025) se publican solo en `127.0.0.1`.
- **Eventos:** ms-auth, ms-booking, ms-catalog y ms-analytics publican con un *outbox* transaccional y *publisher
  confirms*; cada consumidor deduplica por `idEvento` y los mensajes inválidos van a la `.dlq`
  ([ADR-011](docs/sad/adr/ADR-011-mensajeria-rabbitmq.md), contrato en
  [`docs/sad/api/eventos.md`](docs/sad/api/eventos.md)). MongoDB corre como réplica `rs0` de un nodo para las
  transacciones (DEC-281).
- Las llaves del JWT se montan como *secrets* de solo lectura en `/run/secrets/` de ms-auth; nunca se copian a una
  imagen.
- **Servicios de un solo uso**, que se ejecutan en cada `docker compose up`, terminan y no dejan nada corriendo:
  `postgres-bases` crea las bases que falten y el usuario propio de ms-auth y de ms-booking
  (`infra/postgres/asegurar-bases.sql`, DEC-275), y `mongo-migraciones` inicia la réplica `rs0` si hace falta y aplica
  las migraciones que falten de catalog_db (M…), analytics_db (A…) y chatbot_db (C…), cada una registrada en la
  colección `migraciones` de su base, con el usuario propio de ms-catalog, ms-analytics y ms-chatbot. Cada servicio
  se conecta con su usuario, con el mínimo privilegio: el administrador de `POSTGRES_USER` y `MONGO_USER` solo lo
  usan estos dos servicios (DEC-275). Así un volumen creado con una versión anterior se pone al día **sin borrar
  datos**. Los microservicios esperan a que terminen bien (`service_completed_successfully`); en
  `docker compose ps -a` aparecen como `Exited (0)`.
- **Reservas:** ms-booking materializa los cupos de los próximos 60 días bajo demanda, a partir de la oferta que cada
  servicio define en ms-catalog (días de operación y salidas; DEC-135): la primera consulta de disponibilidad, de las
  tarjetas o de una reserva los crea. La demostración del CU, con la prueba de concurrencia, está en
  [`docs/demo/reservas/`](docs/demo/reservas/README.md).

**Memoria.** Docker Desktop con WSL 2 le da por defecto a su máquina virtual el 50 % de la RAM: unos 4 GB en un
portátil de 8 GB. Por eso cada contenedor tiene un límite (`mem_limit` en `docker-compose.yml`):

| Contenedor | Límite | Nota |
|---|---|---|
| ms-auth, ms-catalog, api-gateway | 768 MB cada uno | La JVM arranca con `-XX:MaxRAMPercentage=75`: heap de unos 576 MB (ancla `x-memoria-java`) |
| ms-booking, ms-analytics, ms-chatbot | 512 MB cada uno | Servicios pequeños: heap de unos 384 MB |
| mongo | 512 MB | `--wiredTigerCacheSizeGB 0.25`; sin eso, su caché tomaría la mitad de la máquina virtual |
| rabbitmq | 384 MB | En reposo usa ~145 MB |
| postgres | 256 MB | En reposo usa ~60-150 MB |
| frontend (nginx), mailpit (perfil `dev`) | 64 MB cada uno | ~10 y ~25 MB en reposo |
| servicios de un solo uso | — | Terminan y liberan la memoria |

La suma de los límites es de unos 5 GB; en reposo el sistema usa unos **2,4 GB** (medido con `docker stats` el
2026-11-15). Sin estos límites, cada JVM calcularía su memoria sobre **toda** la máquina virtual y MongoDB tomaría su
caché de ella, y juntos podrían agotarla. Con 4 GB el sistema arranca, pero bajo carga puede quedarse sin memoria
(contenedores reiniciados con código 137): se recomienda `memory=6GB` en `.wslconfig`.
- En Docker Compose los servicios Java corren con el perfil `dev` (`SPRING_PROFILES_ACTIVE` en
  `docker-compose.yml`): carga los usuarios de prueba y Swagger. Es una configuración de demostración; un despliegue
  real debe quitarlo. Fuera de Compose ningún servicio activa `dev` si no se le pide.
- Las pruebas no corren dentro de las imágenes (usan Testcontainers). Se ejecutan fuera, con `./mvnw test` en cada
  servicio y `npm test` en `frontend/`. Con menos de 8 GB en la máquina virtual de Docker, **detener el sistema antes de
  correr las pruebas** (`docker compose stop`): los contenedores de Testcontainers y el sistema completo juntos no caben.

**Comandos útiles**

```bash
docker compose logs -f ms-auth             # logs de un servicio
docker compose up -d --build --no-deps frontend   # reconstruir solo un servicio (sin --no-deps recrea también sus dependencias)
docker compose stop ms-catalog             # simular la caída de un servicio (la búsqueda da 503; el login sigue)
docker compose start ms-catalog
docker compose down                        # detener todo (conserva los datos)
docker compose down -v                     # detener todo y BORRAR los volúmenes de las bases
```

**Solución de problemas**

| Síntoma | Causa | Solución |
|---|---|---|
| `Bind for 127.0.0.1:8000 failed: port is already allocated` (o `Bind for 127.0.0.1:8080`, `:5432` o `:27017`) | Otro programa usa el puerto | Cambiar `FRONTEND_PORT`, `POSTGRES_PORT` o `MONGO_PORT` en `.env`, o cerrar el programa. Para encontrarlo: `netstat -ano \| findstr :8000` en Windows o `lsof -i :8000` en Linux/macOS. El 8080 es fijo: hay que liberarlo. |
| PostgreSQL no arranca en 5432, o ms-auth se conecta a otra base | En Windows, el servicio de PostgreSQL instalado localmente (`postgresql-x64-*`) ya ocupa el 5432 | Poner `POSTGRES_PORT=5433` en `.env`, o detener ese servicio en `services.msc`. Dentro de Docker no cambia nada: ms-auth usa `postgres:5432` por la red interna. |
| `docker compose up` falla con un error que menciona `infra/keys/jwt-privada.pem` (o la pública) | Faltan las llaves del JWT | Generarlas en `infra/keys/` (paso 2) y volver a ejecutar `docker compose up -d`. |
| `required variable POSTGRES_PASSWORD is missing a value: Falta POSTGRES_PASSWORD en .env` | Falta el `.env` o una variable | `cp .env.example .env` y completar los valores. |
| `required variable AUTH_DB_PASSWORD is missing a value` (o `BOOKING_DB_PASSWORD`, `CATALOG_DB_PASSWORD`) | El `.env` es anterior a los usuarios propios de cada servicio (DEC-275) | Copiar de `.env.example` el bloque `AUTH_DB_*`, `BOOKING_DB_*` y `CATALOG_DB_*` y poner contraseñas propias. `docker compose up -d` crea los usuarios y les pasa las bases sin borrar datos. |
| `required variable RABBITMQ_USER is missing a value` (o `RABBITMQ_*_PASSWORD`, `ANALYTICS_DB_PASSWORD`, `CHATBOT_DB_PASSWORD`, `SERVICIO_ANALYTICS_SECRETO`, `SERVICIO_BOOKING_SECRETO`) | El `.env` es anterior al plan del 100 % (RabbitMQ, ms-analytics y ms-chatbot) | Copiar de `.env.example` esos bloques con valores propios y volver a ejecutar `docker compose up -d`: crea los usuarios y aplica las migraciones sin borrar datos. |
| No llega el correo de «¿Olvidaste tu contraseña?» | Sin el perfil `dev` no corre Mailpit, y sin `SMTP_*` el correo no tiene a dónde ir | En local, `COMPOSE_PROFILES=dev` y revisar http://localhost:8025; en un despliegue, configurar `SMTP_*` (`.env.example`). |
| En Linux, ms-auth no puede leer la llave privada | Los *secrets* de Compose son archivos montados con los permisos del host | Dar permiso de lectura al usuario del contenedor: `chmod 644 infra/keys/jwt-*.pem`. |
| Faltan datos de prueba, o no aparecen cambios de los scripts de `infra/*/init/` | Los scripts de inicialización solo corren con el volumen vacío, y son la línea base: no cambian | Los cambios de esquema y de datos llegan como migraciones (Flyway en ms-auth; `infra/mongo/migraciones` en catalog_db), que se aplican solas con `docker compose up -d` sin borrar nada. Solo como último recurso, y sabiendo que **borra** los datos: `docker compose down -v`. |
| ms-catalog, ms-analytics o ms-chatbot no arranca y `mongo-migraciones` aparece como `Exited (1)` (o ms-auth y ms-booking, y `postgres-bases`) | Una migración falló; por ejemplo, M001 se detiene si un alojamiento no tiene `precioBase` | `docker compose logs mongo-migraciones` (o `postgres-bases`) muestra el motivo. Corregir el dato y volver a ejecutar `docker compose up -d`: las migraciones ya aplicadas no se repiten. |
| Un servicio Java se reinicia solo, con código de salida 137 | Superó su límite de memoria (`OOMKilled`) | Confirmarlo con `docker inspect -f '{{.State.OOMKilled}}' samaria360-ms-auth` y subir `mem_limit` en `docker-compose.yml`. |
| Justo después de `up -d`, el sitio muestra «servicio no disponible» durante unos 40 s | El gateway todavía está arrancando. El frontend ya lo espera (`service_healthy`), pero ms-auth y ms-catalog pueden seguir arrancando, y tras reiniciar Docker los contenedores vuelven sin respetar ese orden | Esperen a que `docker compose ps` marque todos los servicios como `healthy` y pulsen «Reintentar». |
| Con `./mvnw spring-boot:run`, ms-auth no arranca: `Validate failed` y `Detected applied migration not resolved locally: 2.` (y 3, 6, 7, 14) | Se arrancó sin el perfil `dev` contra una base que ya tiene la semilla aplicada: sin ese perfil, Flyway no ve `db/semilla` | Arrancar con `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (sección «Arranque local»). |
| Un servicio queda en `unhealthy` o se reinicia | Error al arrancar (credenciales, llaves, base no disponible) | `docker compose logs <servicio>`. |

## Arranque local (desarrollo)

Para desarrollar con recarga en caliente: solo las bases en Docker y los servicios con `./mvnw` y Vite. Si el sistema
completo está levantado, primero `docker compose stop ms-auth ms-catalog ms-booking ms-analytics ms-chatbot api-gateway frontend`: el
gateway del contenedor ocupa el 8080, y así no quedan dos instancias de cada servicio.

```bash
cp .env.example .env          # y cambiar las contraseñas
docker compose up -d postgres-bases mongo-migraciones rabbitmq mailpit   # bases y migraciones al día, bróker y correo
cd ms-auth && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # :8081 (igual en ms-catalog :8082, ms-booking :8083, ms-analytics :8084 y ms-chatbot :8085)
cd api-gateway && ./mvnw spring-boot:run                              # :8080
cd frontend && npm install && npm run dev     # http://localhost:5173 (proxy /api → 8080)
```

El perfil `dev` no se activa solo: sin `-Dspring-boot.run.profiles=dev`, ms-auth arranca sin los usuarios de prueba y
ningún servicio publica Swagger. Docker Compose lo activa con `SPRING_PROFILES_ACTIVE=dev`.

## Pruebas

Cada servicio Java se prueba con su propio wrapper (no hay `pom.xml` raíz), y el frontend con npm. Las pruebas de
infraestructura usan Testcontainers: necesitan Docker en marcha y, con menos de 8 GB en la máquina virtual de Docker,
el sistema detenido (`docker compose stop`).

```bash
cd ms-auth && ./mvnw test      # igual en ms-catalog, ms-booking, ms-analytics, ms-chatbot y api-gateway
cd ms-auth && ./mvnw verify    # pruebas + informe de cobertura en target/site/jacoco/index.html (JaCoCo)
cd frontend && npm test        # y npm run build (tipos) y npm run lint (ESLint)
```

En GitHub Actions (`.github/workflows/`) corren las mismas pruebas en cada *push* a `main` y en cada PR, solo para los
servicios cuyas carpetas cambiaron. El informe de cobertura de cada servicio Java queda como artefacto del workflow.
`compose-humo.yml` verifica primero el arranque aislado de PostgreSQL y RabbitMQ con `infra/humo/arranque-infra.sh`
(Docker y Node.js; almacenamiento temporal, sin puertos publicados ni volúmenes de la aplicación), levanta el sistema
completo y corre `infra/humo/humo.sh`, que también comprueba los tres consumidores de eventos. Se ejecuta cuando cambian
`docker-compose.yml`, `infra/`, un `Dockerfile` o `frontend/nginx/`. En local: `bash infra/humo/arranque-infra.sh` y,
con el sistema arriba, `bash infra/humo/humo.sh`. Las pruebas de los RNF de operación (k6, Lighthouse y resiliencia) están fuera del
CI, en [`infra/carga/`](infra/carga/README.md).

## Equipo

Proyecto Capstone Design de Arquitectura de Software, Universidad del Magdalena:

- Alejandro Luis Hincapie Pulido
- Bryan Andre Robles Mojica
- Juan David Rivera Santana
- Karyme Judith Sanchez Fernandez
- Mateo Damian López Ospino

## Licencia

Por ahora el repositorio **no tiene licencia** (DEC-254): todos los derechos quedan reservados a sus autores hasta que
el docente apruebe una, por la propiedad intelectual del capstone y su relación con la Alcaldía. Las fotografías de
`frontend/public/imagenes/` y las capturas de `docs/recursos-graficos/capturas/` no tienen autor, fuente ni licencia
verificados y se usan solo para la demostración académica; las teselas del mapa son © OpenStreetMap contributors.
