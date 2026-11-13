# Subsistema «Gestionar servicios»: «Aprobar o rechazar servicio»

Fase de Construcción (RUP). Caso de uso de la iteración C «Registrar y aprobar servicio» (DEC-54, DEC-134): RF21. Incluye «Consultar listado pendiente» y lo extiende «Justificar rechazo de servicio».
- **Pantalla:** WF-19 «Aprobar servicios» ([wireframe](../wireframes/WF-19-aprobar-servicios.puml), propuesto por arquitectura y validado por el equipo). Implementada en la sesión B2: `/admin/servicios` (cola) y `/admin/servicios/:id` (revisión), solo para el Administrador (DEC-174 a DEC-182; ver «Pantalla» abajo).
- **Requisitos:** citados de `docs/srs/especificacion de requisitos.pdf`.
- **Decisiones:** en `docs/trazabilidad.md` («Registrar y aprobar servicio — decisiones», DEC-124 a DEC-151).
- **Contrato HTTP:** en [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), sección «Aprobar o rechazar servicio».
- **Nombre:** se conservan los nombres originales de los CU (Entregable 2, figura 4; DEC-59).

| Campo | Valor |
|---|---|
| **Casos de uso** | «Aprobar o rechazar servicio», que incluye «Consultar listado pendiente» y al que extiende «Justificar rechazo de servicio» |
| **Actores** | Administrador |
| **Requisitos que traza** | RF21. Además RNF06, RF09 (el servicio registrado que se revisa) y RF22 (un servicio publicado de un prestador inactivo sigue oculto) |
| **Nivel** | Objetivo de usuario |
| **Alcance** | `ms-catalog` (`catalog_db`: `servicios` y la proyección `cuentas`) |

## Reglas de negocio (citadas del SRS)

- **RF21:** «El sistema debe mostrar al Administrador un listado de los servicios turísticos con estado "Pendiente de aprobación" (registrados según el RF08), permitiéndole cambiar su estado a "Publicado" para que sea visible, o a "Rechazado" incluyendo un campo de texto obligatorio con la justificación.» La referencia a RF08 es una errata: debe ser RF09 (`docs/trazabilidad.md`, «Erratas del SRS»; hallazgo N21).
- **RF09:** «Todo servicio registrado requerirá aprobación previa del Administrador antes de ser publicado en la plataforma.»
- **RF22:** el Inactivo de un prestador oculta todos sus servicios de las búsquedas públicas.
- **RNF06:** control de acceso basado en roles.

Reglas que el SRS no fija y que decidió el equipo (`docs/trazabilidad.md`):
- **DEC-124:** las únicas transiciones son Pendiente → Publicado y Pendiente → Rechazado.
- **DEC-128:** el patrón de RF28: `{decision, justificacion}`, UPDATE condicional por estado y 409 `ESTADO_CAMBIADO`. `Rechazado` es final en esta iteración (la edición es de la iteración D).
- **DEC-144:** listado con `estado` (por defecto `Pendiente`), del más antiguo al más reciente, en páginas de 10; detalle sin la regla de visibilidad pública.
- **DEC-145:** justificación de 1 a 500 caracteres sin los espacios de los extremos (como DEC-42), ignorada al aprobar; se guardan `revisadoPor` y `fechaRevision`. Aprobar a un prestador ya inactivo se permite: queda Publicado y oculto.

## Diagrama de casos de uso

```plantuml
@startuml
left to right direction
actor Administrador
rectangle "Gestionar servicios" {
  usecase "Aprobar o rechazar servicio\n(RF21)" as AR
  usecase "Consultar listado pendiente" as CLP
  usecase "Justificar rechazo de servicio" as JR
}
Administrador --> AR
AR ..> CLP : <<include>>
JR ..> AR : <<extend>>\n[decisión = Rechazar]
@enduml
```

- **Punto de extensión** de «Aprobar o rechazar servicio»: «decisión», en el paso 4 del flujo básico. «Justificar rechazo de servicio» lo extiende cuando el Administrador elige Rechazar.
- El modelo completo del subsistema (con «Registrar servicio comercial») está en [`CU-registrar-servicio.md`](CU-registrar-servicio.md).

## CU «Aprobar o rechazar servicio» (RF21)

**Actor:** Administrador.

**Precondiciones:**
1. El Administrador inició sesión (RF15) y su token está vigente; ms-catalog tiene su fila en `cuentas` (DEC-110).
2. Hay al menos un servicio `Pendiente` registrado según RF09.

**Flujo básico:**
1. El Administrador abre WF-19.
2. **Include «Consultar listado pendiente»:** el sistema lista los servicios `Pendiente` con `GET /api/v1/catalogo/admin/servicios?estado=Pendiente&pagina=`, del más antiguo al más reciente, de 10 en 10: nombre, categoría, prestador (id y RNT), copia del estado del prestador y fecha de registro.
3. El Administrador elige un servicio. El sistema muestra su detalle con `GET /api/v1/catalogo/admin/servicios/{id}`: todos los datos del registro, la oferta, el RNT y el estado de la cuenta del prestador según la proyección.
4. **Punto de extensión «decisión».** El Administrador elige **Aprobar**.
5. El sistema envía `POST /api/v1/catalogo/admin/servicios/{id}/aprobacion` con `{decision: "Aprobar"}`.
6. ms-catalog hace el UPDATE condicional: pasa a `Publicado` solo si sigue `Pendiente`, y guarda `revisadoPor` (`sub`) y `fechaRevision`.
7. El sistema responde 200 con el detalle. Si el prestador está activo, el servicio aparece en la búsqueda y en el detalle públicos.

**Extend «Justificar rechazo de servicio» (en el paso 4):**
1. El Administrador elige **Rechazar** y escribe la justificación (obligatoria, de 1 a 500 caracteres).
2. El sistema envía `{decision: "Rechazar", justificacion}`.
3. ms-catalog hace el UPDATE condicional a `Rechazado` con `motivoRechazo` (sin los espacios de los extremos), `revisadoPor` y `fechaRevision`.
4. El sistema responde 200. El prestador ve el motivo en «Mis servicios» (CU «Registrar servicio comercial»).

**Flujos alternos:**
- **FA1. Justificación inválida.** Rechazar sin justificación, en blanco o con más de 500 caracteres → 400 `DATOS_INVALIDOS` con `campos.justificacion` (`REQUERIDO` o `LONGITUD_MAXIMA`). No cambia nada.
- **FA2. Decisión inválida.** `decision` ausente → `campos.decision: REQUERIDO`; fuera de `Aprobar` y `Rechazar` → `NO_PERMITIDO`. Un cuerpo que no es JSON → 400 sin `campos`.
- **FA3. El servicio no existe.** Id inexistente o que no es un ObjectId → 404 `SERVICIO_NO_ENCONTRADO`.
- **FA4. Otro Administrador ya decidió.** El servicio ya no está `Pendiente` → 409 `ESTADO_CAMBIADO` con `estadoActual`. La pantalla avisa y recarga la lista (DEC-52).
- **FA5. Quien actúa no es Administrador.** 403 `ACCESO_DENEGADO`. Sin token → 401 `NO_AUTENTICADO`; con `ver` viejo → 401 `SESION_INVALIDADA`.
- **FA6. El prestador está inactivo.** Aprobar se permite: el servicio queda `Publicado` con `prestadorActivo = false` y no aparece en público (RF22).
- **FA7. Estado o página inválidos en el listado.** 400 `DATOS_INVALIDOS` con `campos.estado` o `campos.pagina`.

**Postcondiciones:**
- **Éxito (aprobar):** el servicio está `Publicado`, con quién y cuándo; es visible si su prestador está activo.
- **Éxito (rechazar):** el servicio está `Rechazado`, con el motivo, quién y cuándo; nunca es visible.
- **Fallo:** no cambia nada.

## Criterios de aceptación derivados

| # | Criterio | Prueba |
|---|---|---|
| C1 | El listado de Pendientes va del más antiguo al más reciente, de 10 en 10 | `AprobacionServicioApiIntegracionTest`, `AdministrarServiciosServiceTest` |
| C2 | Aprobar publica el servicio y lo vuelve visible en la búsqueda | `AprobacionServicioApiIntegracionTest` |
| C3 | Rechazar sin justificación es 400; con justificación guarda el motivo | `AprobacionServicioApiIntegracionTest`, `AdministrarServiciosServiceTest` |
| C4 | Una segunda decisión es 409 `ESTADO_CAMBIADO` con `estadoActual` | `AprobacionServicioApiIntegracionTest`, `AdministrarServiciosServiceTest` |
| C5 | Un id inexistente o inválido es 404 | `AprobacionServicioApiIntegracionTest` |
| C6 | Solo el Administrador revisa | `AprobacionServicioApiIntegracionTest` |
| C7 | Inactivar al prestador oculta también su servicio nuevo publicado (RF22) | `AprobacionServicioApiIntegracionTest` |

## Secuencia

```plantuml
@startuml
actor Administrador as A
participant "API Gateway" as G
participant "ms-catalog\nServicioAdminController" as C
participant "AdministrarServiciosService" as S
database "catalog_db" as M
A -> G : GET /api/v1/catalogo/admin/servicios?estado=Pendiente&pagina=0
G -> C : (Authorization sin cambios)
C -> S : listar(Pendiente, 0)
S -> M : find(estado) sort(fechaRegistro, _id)
M --> S : página
S --> C : página
C --> G : 200 página (más antiguo primero)
G --> A : 200
A -> G : POST /api/v1/catalogo/admin/servicios/{id}/aprobacion {decision, justificacion}
G -> C : (Authorization sin cambios)
alt rechazo sin justificación / >500
  C --> G : 400 DATOS_INVALIDOS
else
  C -> S : decidir(id, decision, sub)
  S -> M : updateOne({_id, estadoPublicacion: Pendiente}, $set estado, motivo, revisadoPor, fechaRevision)
  M --> S : matched
  alt matched = 1
    S --> C : servicio
    C --> G : 200 detalle (Publicado | Rechazado)
  else matched = 0
    S -> M : find(_id)
    M --> S : documento | nada
    alt no existe (o id no es ObjectId)
      S --> C : ServicioNoEncontrado
      C --> G : 404 SERVICIO_NO_ENCONTRADO
    else
      S --> C : EstadoServicioCambiado(estadoActual)
      C --> G : 409 ESTADO_CAMBIADO {estadoActual}
    end
  end
end
G --> A : respuesta
@enduml
```

## Pantalla (WF-19, sesión B2)

Cómo recorre el Administrador el flujo en WF-19. Decisiones en `docs/trazabilidad.md`, sesión B2, DEC-174 a DEC-182.

- **Paso 1:** «Aprobar servicios» en el menú del avatar lleva a la cola.
- **Pasos 2 y 3:**
  - «Revisar» abre `/admin/servicios/:id`, con los datos del registro, el mapa de solo lectura y el prestador (id, RNT, estado de la cuenta).
  - Con el prestador no activo aparece el aviso de FA6.
- **Paso 4:** «Aprobar → Publicado» envía la decisión sin diálogo. Es definitiva en C (DEC-177).
- **Extensión:** «Rechazar» abre el diálogo de la justificación, con el contador y «Confirmar rechazo» deshabilitado mientras esté vacía.
- **Paso 7 y extensión:** tras el 200, la pantalla vuelve a la cola, que se recarga y anuncia el estado nuevo.
- **FA1:** el error va junto al campo.
- **FA3 y FA4:** se vuelve a la cola con el aviso (DEC-52).
- **FA5:** «Sin permiso» o el login con el motivo.
- **Estados finales:** un servicio ya decidido no muestra acciones; muestra la fecha, el revisor (id) y el motivo (DEC-179).

```plantuml
@startuml
actor Administrador as A
participant "AprobarServiciosPage\n(/admin/servicios)" as C
participant "RevisarServicioPage\n(/admin/servicios/:id)" as R
participant "API Gateway" as G
participant "ms-catalog" as M
A -> C : «Aprobar servicios»
C -> G : GET /api/v1/catalogo/admin/servicios?estado=Pendiente&pagina=0
G -> M : (Authorization sin cambios)
M --> C : 200 página (más antiguo primero)
A -> R : «Revisar»
R -> G : GET …/admin/servicios/{id}
G -> M
M --> R : 200 forma de gestión (+ revisadoPor, estadoCuentaPrestador)
A -> R : «Aprobar → Publicado»
R -> G : POST …/{id}/aprobacion {decision: "Aprobar"}
G -> M
M --> R : 200 (Publicado, revisadoPor, fechaRevision)
R -> C : vuelve a la cola con el aviso
C -> G : GET …/admin/servicios?estado=Pendiente&pagina=0
M --> C : 200 (sin el servicio)
C --> A : región aria-live: «Se aprobó «…»: ahora está Publicado»
@enduml
```

```plantuml
@startuml
actor Administrador as A
participant "RevisarServicioPage" as R
participant "DialogoRechazo" as D
participant "ms-catalog\n(vía API Gateway)" as M
participant "AprobarServiciosPage" as C
A -> R : «Rechazar»
R -> D : abre (foco en la justificación)
A -> D : escribe de 1 a 500 caracteres y confirma
D -> R : onConfirmar(texto)
R -> M : POST …/{id}/aprobacion {decision: "Rechazar", justificacion}
alt 200
  M --> R : Rechazado + motivoRechazo
  R -> C : vuelve a la cola con «Se rechazó «…»»
else 400 DATOS_INVALIDOS (FA1)
  M --> R : campos.justificacion
  R -> D : error junto al campo (sigue abierto)
else 409 ESTADO_CAMBIADO (FA4)
  M --> R : estadoActual
  R -> C : vuelve a la cola con «ya fue revisado… (ahora está {estadoActual})»
  C -> M : GET cola (recarga)
end
@enduml
```

## Fuera de alcance

- Reenviar o editar un servicio rechazado (iteración D).
- Notificar al prestador la decisión (ms-notificaciones; RF13 y RF30 siguen fuera de alcance) y el log de auditoría de la aprobación (RF13): `revisadoPor` y `fechaRevision` lo preparan.
- Despublicar un servicio ya publicado: el SRS no lo define.
