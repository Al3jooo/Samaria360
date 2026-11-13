# Guía de contribución

Cómo se trabaja en el repositorio de Samaria360. Las reglas de arquitectura, de datos y de alcance de cada iteración
están en [CLAUDE.md](CLAUDE.md), que vale para personas y para agentes de código; esta guía resume el flujo de trabajo.

## Antes de empezar

- **Proceso:** RUP, fase de Construcción. Se usan sus términos: fases, iteraciones, casos de uso (CU), requisitos
  funcionales (RF) y no funcionales (RNF).
- **Requisitos:** la fuente única es [`docs/srs/especificacion de requisitos.pdf`](docs/srs/). Si una regla de
  negocio no está en el SRS, se pregunta al equipo; no se inventa.
- **Pantallas:** deben corresponder a los wireframes de [`docs/wireframes/`](docs/wireframes/README.md). Lo que quede
  fuera del alcance se muestra deshabilitado y se lista como pendiente.
- **Entorno:** ver «Arranque en un comando» y «Arranque local» en el [README](README.md).

## Ramas

- `main` siempre compila y pasa las pruebas. No se trabaja directamente sobre ella.
- Una rama por cambio, creada desde `main`:
  - `feature/<descripcion-corta>` para funcionalidad nueva (p. ej. `feature/eventos-agenda-backend`);
  - `fix/<descripcion-corta>` para corregir un defecto (p. ej. `fix/csp-blob-historial-360`);
  - `docs/<descripcion-corta>` para cambios solo de documentación.
- Cada rama se integra con un *pull request* revisado por al menos otro integrante.

## Mensajes de commit

Formato [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/), en español:

```
<tipo>(<alcance>): <descripción en minúscula, sin punto final> (<RF o DEC>) - <sesión>
```

- **Tipos:** `feat` (funcionalidad), `fix` (defecto), `docs`, `test`, `refactor`, `build` (dependencias o Docker),
  `ci` (workflows) y `chore` (mantenimiento).
- **Alcances:** `auth`, `catalogo`, `booking`, `gateway`, `frontend`, `infra` y `docs`; varios separados por coma.
- Los requisitos y decisiones entre paréntesis, y la sesión de la iteración al final cuando aplique.

Ejemplos del historial:

```
feat(backend): agenda de eventos del Gestor y agenda publica (RF25) - A2
fix(frontend): vista previa de fotos con CSP y filtros de WF-23 a 360 px (O38, O39)
feat(booking): inventario bajo demanda desde la oferta del catalogo (DEC-135)
```

## Pruebas (obligatorias en cada cambio)

| Capa | Tipo de prueba |
|---|---|
| `domain` | Unitarias puras, sin Spring |
| `application` | Casos de uso con dobles (*fakes*) de los puertos |
| `infrastructure` | Testcontainers contra PostgreSQL o MongoDB reales |
| `architecture` | Reglas de capas con ArchUnit |
| `frontend` | Vitest y Testing Library |

Antes de abrir el PR, en cada servicio que cambió:

```bash
cd ms-auth && ./mvnw verify    # pruebas + cobertura (JaCoCo); igual en ms-catalog, ms-booking y api-gateway
cd frontend && npm run lint && npm run build && npm test
```

`npm run lint` no debe dar errores; los avisos (*warnings*) se revisan, pero no bloquean.

GitHub Actions corre las mismas pruebas en el PR; no se integra con el CI en rojo.

## Bases de datos

- **Nunca se edita una migración ya aplicada** (Flyway en ms-auth y ms-booking, `infra/mongo/migraciones` en
  catalog_db): rompe las bases de los compañeros. Cada cambio va en una migración nueva, con el siguiente número.
- El esquema y los datos de referencia van en `db/migration` (o `esquema/`); los datos de prueba, en `db/semilla`
  (o `semilla/`).
- Ningún servicio lee ni escribe la base de otro.

## Documentación

- Cada CU nuevo agrega su especificación en `docs/casos-de-uso/`, sus filas en
  [`docs/trazabilidad.md`](docs/trazabilidad.md) y sus decisiones (DEC) en el archivo de su iteración en
  [`docs/trazabilidad/`](docs/trazabilidad/).
- Las decisiones de arquitectura van como ADR en `docs/sad/adr/` y los contratos de la API en `docs/sad/api/`.
- Un elemento de pantalla que no está en el wireframe y tiene función se registra como «Mejora de diseño» en la
  trazabilidad.

## Lo que nunca se sube

- `.env` ni nada de `infra/keys/` salvo su README: los secretos se quedan en cada máquina.
- Grabaciones, fotos o datos personales de las entrevistas.
- Dependencias nuevas sin justificar en el PR por qué hacen falta.
