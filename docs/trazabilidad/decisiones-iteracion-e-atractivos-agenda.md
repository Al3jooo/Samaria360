# Decisiones de la iteración E «Atractivos y agenda»

Parte de la [matriz de trazabilidad](../trazabilidad.md): decisiones (DEC), hallazgos y verificación de cada sesión, separadas del archivo principal en la revisión del 2026-11-13. Los números DEC son únicos en todo el proyecto.

## Atractivos y agenda — decisiones (iteración E, sesión A1; RF06, RF07, RF24, RF27, RF33, RF58)

Backend y documentos (DEC-213: cuatro PR, una sesión por rama). CU nuevos: [Registrar atractivo](../casos-de-uso/CU-registrar-atractivo.md) (con «Cargar imágenes»), [Actualizar atractivo](../casos-de-uso/CU-actualizar-atractivo.md) (con «Consultar atractivos», WF-24), [Cambiar estado operativo](../casos-de-uso/CU-cambiar-estado-atractivo.md) y [Consultar detalle de atractivo](../casos-de-uso/CU-consultar-detalle-atractivo.md). Imágenes: [ADR-009](../sad/adr/ADR-009-imagenes-gridfs.md). Contrato en [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md).

**Decisiones tomadas antes de la iteración (no se reabren):** DEC-54 (plan del 60 %: la iteración E lleva RF06, RF07, RF24, RF33, RF25, RF27 y RF58), DEC-55 (imágenes en GridFS), DEC-87 (detalle de atractivo), DEC-110 (401 `SESION_INVALIDADA` a un Gestor sin fila en `cuentas`) y DEC-176 (capas del frontend). El pedido cita también DEC-31, N1 y M5, que no tienen texto en el repositorio (DEC-218).

**Decisiones de la iteración E (DEC-206 a DEC-221; propuestas por arquitectura y aprobadas por el usuario el 2026-10-30):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-206 | Alcance del Gestor (D1) | El Gestor registra, edita (todos los datos salvo las imágenes) y cambia el estado de **cualquier** atractivo; sin borrado ni desactivación. El Administrador no gestiona atractivos. **Justificación:** el SRS asigna RF06, RF07, RF24, RF33 y RF58 al Gestor; los atractivos son públicos (del municipio), no de un Gestor: `gestorId` registra quién los dio de alta. El SRS no pide borrar | `AtractivoGestorController`, CU Actualizar atractivo |
| DEC-207 | Modelo de `atractivos` (D2) | Se amplía la colección existente con M012 (validador v2): `version` y `fechaRegistro` obligatorias (relleno: 0 y la fecha del `_id`), `horarioAtencion`, `fechaActualizacion`, enums y límites. **M012 no normaliza datos:** si un atractivo no cumple el v2, se detiene con la lista de `_id` (aprobado por el usuario tras el informe de datos: 7 atractivos, ninguno fuera del v2). Control de concurrencia con `version`, como DEC-186. Puertos segregados: `AtractivoRepositoryPort` (lectura pública) y `GestionAtractivosPort` (gestión), los dos en `AtractivoMongoAdapter`. **Frontera de microservicios:** el atractivo vive solo en `catalog_db`; ms-booking no lo conoce (no se reserva) y `gestorId` es una referencia lógica sin llave física, como `prestadorId` | M012, `AtractivoPublico`, `AtractivoMongoAdapter` |
| DEC-208 | Horario de atención (D3, HC4) | `horarioAtencion`: texto libre opcional, de 1 a 500 caracteres tras `strip()`, como en servicios. Se captura en el registro y la edición y se muestra en la ficha. **Justificación:** RF58 lo define como «campo de texto» | `RegistroAtractivo`, M012 |
| DEC-209 | Imágenes (D4) | Entran, solo para atractivos y **solo al registrar**: de 1 a 5, JPEG o PNG por firma, de 5 MB (5 × 1024 × 1024 bytes) como máximo, en GridFS (bucket `imagenes`), con compensación si el insert falla. `imagenes[]` guarda la ruta pública `/api/v1/catalogo/imagenes/{id}`. Editar la galería se difiere (como RF48). **Justificación:** RF06 exige «carga de imágenes en formato JPEG/PNG»; `GridFSBucket` ya viene con el driver | ADR-009, `PoliticaImagen`, `ImagenGridFsAdapter` |
| DEC-210 | Estado operativo (D5) | `Abierto`, `Cerrado_temporalmente` y `En_mantenimiento`; transición libre entre ellos (RF33 no restringe), pero no al mismo estado (`campos.estadoNuevo: NO_PERMITIDO`). Endpoint propio `POST …/{id}/estado {estadoNuevo, version}`; el registro exige el estado inicial. **Justificación:** el cambio rápido del mismo día que pide WF-09 | `ActualizacionAtractivo`, CU Cambiar estado operativo |
| DEC-211 | RF24 y RF25 (D6) | Los dos entran en la iteración. RF24 es el campo `normasAmbientales` (hasta 1000, WF-09). RF25 (agenda de eventos) va en la sesión A2 con una colección propia. **Justificación:** DEC-54 los asigna a la iteración E; sin RF25 la iteración no llega a 40 | — |
| DEC-212 | RF07 (D7) | Solo el dato: entero de 1 a 100 000, obligatorio, editable y visible en la ficha. Sin semáforo ni cálculo de afluencia y **sin dependencia con ms-booking**. **Justificación:** los RF que lo consumirían (RF08, RF39, RF64) están descartados (DEC-54, DEC-35); el máximo es técnico | `RegistroAtractivo` |
| DEC-213 | Partición (D8) | **Cuatro PR**, una sesión por rama: A1 (backend de atractivos, GridFS, ADR-009 y 4 CU), A2 (backend de eventos, M013 y su CU), B1 (pantallas del Gestor y detalle) y B2 (agenda). **Justificación:** dos colecciones nuevas o ampliadas, multipart, GridFS y cinco CU superan el tamaño de una sesión (la iteración C también usó cuatro PR) | — |
| DEC-214 | Categorías de atractivo | `Naturaleza` y `Cultura`, las de la semilla, como `enum` en el dominio y en el validador v2. **Decisión del equipo, no del SRS:** RF06 pide «categoría» sin fijar el vocabulario. WF-09 dibuja «Sendero y naturaleza»; la pantalla usará los textos del enum | `CategoriaAtractivo`, M012 |
| DEC-215 | Reglas de RF25 (agenda) | La agenda pública muestra los eventos vigentes (`fechaFin` igual o posterior a hoy en Bogotá), por `fechaInicio` ascendente; el Gestor solo registra y lista (sin editar ni borrar). **Decisión del equipo, no del SRS:** RF25 solo dice «registrar» y «publicarlos automáticamente». Se implementa en la sesión A2 | Sesión A2 |
| DEC-216 | Carga multipart | El registro es una sola petición `multipart/form-data`: la parte `datos` (JSON) y de 1 a 5 partes `imagenes`. Primero se valida todo; después se guardan las imágenes y al final el atractivo. Topes: 6 MB por parte y 30 MB por petición en ms-catalog (`max-swallow-size` 32 MB) y 30 MB en nginx; el gateway no cambia. **Justificación:** sin imágenes huérfanas cuando el Gestor abandona el formulario (un endpoint de carga previa las dejaría) | `AtractivoGestorController`, `application.yml`, `frontend/nginx/default.conf` |
| DEC-217 | Detalle público | `GET /api/v1/catalogo/atractivos/{id}`, sin token, visible mientras exista (DEC-87). Expone todo salvo `gestorId`, `version` y las fechas. Un id inválido o inexistente es el mismo 404 `ATRACTIVO_NO_ENCONTRADO` | `AtractivoController`, `RespuestaAtractivo` |
| DEC-218 | Registro tardío de referencias externas (O28) | DEC-31, N1 y M5, citados en el pedido de la iteración, no tienen texto en ningún documento del repositorio: quedan como **«pendiente de texto»** y esta matriz no los cita como IDs propios. Su contenido se tomó del texto del pedido (RF58 se completa en la iteración E; el dueño de los atractivos; el almacenamiento de imágenes, ya en DEC-55) | — |
| DEC-219 | Casilla de veracidad de WF-09 | Se muestra deshabilitada y se lista como pendiente, como la de WF-08 (DEC-167): no tiene RF. Se aplica en la sesión B1 | Sesión B1 |
| DEC-220 | Acceso del Gestor | `/api/v1/catalogo/gestor/**` con `hasRole("Gestor")` en la cadena pública, con el validador de `ver`: DEC-110 se aplica sin cambios (un Gestor sin fila en `cuentas` recibe 401 `SESION_INVALIDADA`). Otro rol, 403 `ACCESO_DENEGADO`. ms-auth no cambia: la cuenta de la semilla ya tiene su evento (V15) | `SecurityConfig` |
| DEC-221 | Vocabulario de accesibilidad de WF-09 | Las tres condiciones de RF27 (`MovilidadReducida`, `Visual`, `Auditiva`), como DEC-131 en WF-08; WF-09 dibuja otras dos (diferencia 3) | `RegistroAtractivo`, M012 |

**Cambio de numeración (pedido del usuario al aprobar el Paso 3):** en la propuesta, el acceso del Gestor era DEC-216 y el vocabulario de RF27, DEC-218; pasan a **DEC-220** y **DEC-221**, los siguientes libres. La propuesta «M012 no normaliza» (antes DEC-219) se integra en DEC-207. DEC-216, DEC-218 y DEC-219 quedan con los temas que fijó el usuario.

**Estado de los RF de la iteración:**

| RF | Estado | Pruebas / pendiente |
|---|---|---|
| RF06 (registrar atractivo) | **Parcial (backend)** | `RegistroAtractivoTest`, `RegistrarAtractivoServiceTest`, `RegistrarAtractivoApiIntegracionTest`. Falta WF-09 (B1) |
| RF07 (capacidad de carga) | **Parcial (backend)** | Registro, edición y detalle. Falta la pantalla (B1) |
| RF24 (normas ambientales) | **Parcial (backend)** | Registro, edición y detalle. Falta la ficha y WF-09 (B1) |
| RF27 (accesibilidad) | **Parcial**: servicios completos; atractivos, backend | `RegistroAtractivoTest`, `DetalleAtractivoApiIntegracionTest`. Falta la pantalla del Gestor (B1) |
| RF33 (estado operativo) | **Parcial (backend)**; la insignia de WF-05 ya existía | `ActualizacionAtractivoTest`, `GestionAtractivoApiIntegracionTest`. Falta el diálogo del Gestor y la ficha (B1) |
| RF58 (horario de atención) | **Parcial**: C1 a C4 y el backend de C5 | Falta la pantalla del Gestor y la ficha del atractivo (B1) |
| RF25 (agenda de eventos) | **Pendiente** | Sesión A2 (backend) y B2 (pantallas) |

Avance: sigue en **33 de 64** (meta 39, DEC-54). La iteración E suma 7 al cerrar B2: 33 → 40.

**Hallazgos y observaciones de la sesión A1:**

| # | Hallazgo | Estado |
|---|---|---|
| HC-E1 | **Hallazgo Crítico.** RF sin CU: RF06, RF07, RF24, RF33 y RF25, y la parte de atractivos de RF27 y RF58. «Gestionar atractivos» solo aparecía en la fila del pie de esta matriz | **Resuelto para RF06, RF07, RF24, RF27, RF33 y RF58** (cuatro CU). Abierto para RF25 hasta la sesión A2. HC1 sigue abierto para RF01, RF02, RF15, RF55, RF16, RF23, RF38 y RF52 |
| HC-E2 | **Hallazgo Crítico.** La colección `eventos` del Entregable 2 no se puede verificar: el documento no está en el repositorio. Un esquema sin esa fuente sería una decisión sin justificación técnica | Abierto: la sesión A2 propone el esquema desde RF25 y el equipo lo confirma contra el Entregable 2 (punto 13 de «Pendientes para el SAD»). **Resuelto en la sesión A2** (DEC-240) |
| HC4 | RF58 en atractivos sin campo de horario | **Resuelto en el backend** (DEC-208) |
| O28 | DEC-31, N1 y M5 no tienen texto en el repositorio, y el Entregable 2 tampoco está en él | Registrado en DEC-218: «pendiente de texto» |
| N4 | El Gestor no tenía pantallas | **Backend listo** (DEC-220). Las pantallas son de la sesión B1 |
| O29 | Pruebas existentes modificadas:<br>- `MigracionesMongoTest`: la lista suma M012; «volver a aplicarlas» espera M012; sin semilla se aplica M012; un contenedor nuevo (`ATRACTIVO_INVALIDO`) para la parada de M012; casos del validador v2 y del relleno;<br>- `AtractivoPublicoTest`: un caso nuevo para los campos de la gestión.<br>`ScriptsInicializacionMongoTest` no cambia: la línea base sigue congelada | Registrado |
| O30 | Nuevo fuera de la lista inicial de A1, por necesidad técnica: `CargaMultipartServidorTest` (Tomcat real: MockMvc no aplica los topes de multipart, así que el 413 no se puede probar con él) | Registrado |
| O31 | Riesgo de imágenes huérfanas si la compensación también falla | Registrado en ADR-009 («Consecuencias»), con la regla para identificarlas; sin recolector en el prototipo |

**Verificación de la sesión A1:** `./mvnw test` completo en ms-catalog y en api-gateway con Docker activo (resultado en el resumen de cierre). Sin `docker compose up`. M012 no se aplicó al volumen local: se aplica con el próximo arranque de `mongo-migraciones`.

**Fuera de alcance de la sesión A1:** RF25 y M013 (sesión A2); las pantallas (B1 y B2); editar la galería, borrar o desactivar atractivos; la casilla de veracidad (DEC-219); afluencia y rutas alternativas (RF39, RF08); un recolector de imágenes huérfanas.

## Atractivos y agenda, frontend — decisiones (iteración E, sesión B1; RF06, RF07, RF24, RF27, RF33, RF58)

Pantallas del Gestor y ficha pública del atractivo, sobre el backend de A1 (PR #30). Un solo PR (DEC-229). CU de referencia, sin cambios: [Registrar atractivo](../casos-de-uso/CU-registrar-atractivo.md), [Actualizar atractivo](../casos-de-uso/CU-actualizar-atractivo.md), [Cambiar estado operativo](../casos-de-uso/CU-cambiar-estado-atractivo.md) y [Consultar detalle de atractivo](../casos-de-uso/CU-consultar-detalle-atractivo.md). Wireframes: `WF-09-registrar-atractivo.puml` (ampliación), `WF-24-atractivos-gestor.puml` y `WF-06-detalle-atractivo.puml`. Componentes: [`sad/diagramas/componentes-frontend-gestor.puml`](../sad/diagramas/componentes-frontend-gestor.puml).

**Contrato usado (C1, `ms-catalog/.../adapter/in/web/AtractivoGestorController.java`):** `@RequestMapping("/api/v1/catalogo/gestor/atractivos")` (:41); registro multipart (:61-62); listado con `pagina` (:75); detalle (:80-81); **edición `PUT /{id}`** (:86-87); **estado `POST /{id}/estado`** (:92-93). Ficha: `AtractivoController.java:15,24-25`. El backend no cambia (C9).

**Enmienda de DEC-217 (C2):** el 404 de la ficha pública es **`ATRACTIVO_NO_ENCONTRADO`** (`ManejadorErrores.java:134`), no `OFERTA_NO_DISPONIBLE` como citaba el pedido de B1. El frontend usa el código real; el backend no cambia.

**Decisiones de la sesión B1 (DEC-222 a DEC-234; propuestas por arquitectura y aprobadas por el usuario el 2026-10-30, con las condiciones C1 a C9):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-222 | WF-24 (D1) | Tabla como WF-18 (por debajo de 40 rem, tarjetas), páginas de 10 con `pagina` en la URL (desde 1; la API desde 0). Los filtros de estado y de texto se dibujan **deshabilitados** y quedan como pendientes: el `GET` del panel solo admite `pagina` (G2). **Justificación:** el patrón probado de WF-18 y el contrato vigente | `AtractivosGestorPage.tsx`, `criteriosAtractivosGestor.ts` |
| DEC-223 | Coordenadas (D2) | Campos y pin del mapa sincronizados, reutilizando `SelectorUbicacion` con una prop `tipo` (pin y nombres accesibles del atractivo). **Justificación:** ya existe y cumple DEC-165 | `FormularioAtractivo.tsx`, `prestador/componentes/SelectorUbicacion.tsx` |
| DEC-224 | Ficha (D3) | Ruta propia `/atractivos/:id` con `DetalleAtractivoPage` y su API pública (`publica: true`). **Justificación:** el contrato de DEC-217 es otro recurso (sin precio, contacto ni reserva); una variante de `/servicios/:id` mezclaría dos formas y dos 404 | `DetalleAtractivoPage.tsx`, `atractivoApi.ts`, `navegacionDetalle.ts` |
| DEC-225 | Cambio de estado (D4) | Diálogo desde el panel y desde la edición, con la versión leída; el estado actual no se puede elegir. 409 → relee: si lo elegido ya es el actual, avisa y solo queda cerrar; si no, conserva lo elegido, avisa y el reenvío es otro clic. 404 → vuelve al panel con un aviso | `DialogoEstadoAtractivo.tsx` |
| DEC-226 | Imágenes (D5) | Alta: de 1 a 5 con vista previa, firma y tamaño en el cliente; el error de cada una junto a su miniatura; 413 como mensaje general. **Edición: las fotos en solo lectura**, con «Agregar o quitar fotos» deshabilitado y como pendiente: no hay contrato (G1; DEC-209). **Justificación:** el contrato vigente; cambiarlo exige otra sesión de backend | `SelectorImagenes.tsx`, `EditarAtractivoPage.tsx` |
| DEC-227 | Horario y normas (D6) | `textarea` con contador en puntos de código (`longitudTexto`): 1000 en la descripción y las normas, 500 en el horario, 120 en el nombre; en la ficha, los saltos de línea se conservan (`white-space: pre-line`) | `CampoServicio` (`maximo`), `borradorAtractivo.ts`, `DetalleAtractivoPage.tsx` |
| DEC-228 | Casilla de veracidad (D7) | Deshabilitada, sin casilla ni marca de obligatoria, y listada como pendiente (DEC-219) | `FormularioAtractivo.tsx` (`DatoNoDisponible`) |
| DEC-229 | Partición (D8) | Un solo PR | — |
| DEC-230 | Multipart en el cliente (G4) | `solicitar` acepta `formulario: FormData`: no lo serializa ni fija `Content-Type` (el navegador pone el *boundary*). La parte `datos` va como `Blob` `application/json`, como exige el backend. Las solicitudes JSON no cambian (C4) | `shared/api/httpClient.ts` |
| DEC-231 | Edición en el cliente | `useEdicionAtractivo`, con la forma de `useEdicionServicio`: un solo envío a la vez, versión leída, 409 → relee y conserva lo escrito, «Lo que otro gestor cambió» y «Recargar». El de servicios no sirve: está atado a sus códigos y a la navegación del Prestador | `useEdicionAtractivo.ts` |
| DEC-232 | Insignia del estado operativo | Se extrae de la tarjeta a un componente común para la tarjeta, la ficha y el panel (RF33: «visible en la ficha») | `InsigniaEstadoOperativo.tsx` |
| DEC-233 | Menú y pie del Gestor | Menú: «Atractivos» y «Registrar atractivo». Pie: «Panel del gestor» lleva a `/gestor/atractivos` (un rol que no es Gestor ve «Sin permiso»). Cierra N4 para el Gestor | `MenuUsuario.tsx`, `PiePagina.tsx`, `App.tsx` |
| DEC-234 | Categoría en WF-09 | Lista desplegable con `Naturaleza` y `Cultura` (DEC-214); la imagen de WF-09 dibuja «Sendero y naturaleza» | `FormularioAtractivo.tsx` |

**Estado de los RF de la iteración:**

| RF | Estado | Pruebas |
|---|---|---|
| RF06 (registrar atractivo) | **Implementado** (pendiente del PR: recorrido visual) | Backend (A1) más `registrarAtractivo.test.tsx`, `imagenesAtractivo.test.ts`, `borradorAtractivo.test.ts`, `detalleAtractivo.test.tsx` |
| RF07 (capacidad de carga) | **Implementado** (pendiente del PR: recorrido visual) | Backend (A1) más `registrarAtractivo.test.tsx`, `editarAtractivo.test.tsx`, `atractivosGestor.test.tsx`, `detalleAtractivo.test.tsx` |
| RF24 (normas ambientales) | **Implementado** (pendiente del PR: recorrido visual) | Backend (A1) más `registrarAtractivo.test.tsx`, `detalleAtractivo.test.tsx` (texto estático con sus saltos de línea) |
| RF27 (accesibilidad) | **Implementado** (servicios desde la iteración C; atractivos en esta sesión; pendiente del PR: recorrido visual) | `registrarAtractivo.test.tsx`, `detalleAtractivo.test.tsx` |
| RF33 (estado operativo) | **Implementado** (pendiente del PR: recorrido visual) | Backend (A1) más `estadoAtractivo.test.tsx`, `atractivosGestor.test.tsx`, `detalleAtractivo.test.tsx` |
| RF58 (horario de atención) | **Implementado** (C1 a C5; pendiente del PR: recorrido visual) | `registrarAtractivo.test.tsx`, `detalleAtractivo.test.tsx` |
| RF25 (agenda de eventos) | **Pendiente** | Sesiones A2 y B2 |

Avance: de **33 a 39 de 64**: se alcanza la meta del 60 % (DEC-54). RF25 lleva a 40 al cerrar B2.

**Hallazgos y observaciones de la sesión B1:**

| # | Hallazgo | Estado |
|---|---|---|
| HC-B1-1 | El pedido pedía editar las imágenes «en su endpoint», que no existe (DEC-209 lo difiere) | **Resuelto con DEC-226:** solo lectura en la edición; el endpoint queda como pendiente |
| G1 | Editar las fotos de un atractivo registrado: sin contrato | Pendiente (ver «Pendientes») |
| G2 | Filtros del panel: el `GET` solo admite `pagina` | Pendiente; filtros deshabilitados (DEC-222) |
| G3 | Código del 404 de la ficha | Resuelto: enmienda de DEC-217 (`ATRACTIVO_NO_ENCONTRADO`) |
| G4 | `httpClient` no enviaba multipart | Resuelto con DEC-230 (C4: prueba de regresión de las solicitudes JSON) |
| G5 | El pedido pedía «dirección» en WF-09; el backend no la tiene | Sin cambios: RF06 admite «dirección o coordenadas según corresponda» |
| N4 | El Gestor no tenía pantallas | **Resuelto** (DEC-233) |
| O32 | La prueba `explorarMapa.test.tsx` «un atractivo no tiene «Ver detalle» en la ventana emergente» pasaba aunque el enlace existiera: `not.toBeInTheDocument()` siempre se cumple sobre un popup que no está montado en el documento | Corregida: la prueba nueva busca el enlace con `getByRole` y su `href` |
| O33 | Pruebas existentes modificadas:<br>- `explorarMapa.test.tsx`, `misFavoritos.test.tsx` y `disponibilidadTarjetas.test.tsx`: el atractivo pasa a tener «Ver detalle» hacia `/atractivos/:id`;<br>- `elementosComunes.test.tsx`: «Panel del gestor» es un enlace;<br>- `test/fetchSimulado.ts`: datos de atractivos y el `FormData` de la solicitud.<br>`rutasPrestador.test.tsx` y `sesion.test.tsx` no cambian | Registrado |
| O34 | Único archivo fuera de la lista aprobada, por necesidad técnica: `prestador/componentes/SelectorUbicacion.tsx`, prop opcional `tipo` (por defecto `'servicio'`). Sin ella, el mapa de WF-09 se anunciaba «Mapa para ubicar el servicio» y usaba el pin de servicio. WF-08 no la pasa y no cambia (`selectorUbicacion.test.tsx` sin cambios); WF-09 pasa `tipo="atractivo"` | Registrado |
| O35 | Archivo de la lista en otra ruta: la prueba de regresión del cliente HTTP (C4) va en `shared/api/httpClientFormulario.test.ts`, junto a `httpClient.test.ts` (la convención del repositorio), y no en `api/__tests__/` como decía el plan | Registrado |

**Verificación de la sesión B1:** `npm test` (74 archivos, 761 pruebas, dos corridas seguidas en verde) y `npm run build`. Sin `docker compose up`.

**Recorrido visual (O17, O18): pendiente de este PR.** No se hizo en la sesión B1: no tenía navegador. Cubre RF06, RF07, RF24, RF27, RF33 y RF58: Vite contra el *stack*, con la sesión del Gestor de la semilla iniciada por el usuario en el navegador (WF-24, WF-09 alta y edición, diálogo de estado y la ficha `/atractivos/:id`).

**Fuera de alcance de la sesión B1:** RF25 (sesiones A2 y B2); agregar o quitar fotos de un atractivo registrado (G1); filtros del panel (G2); la afluencia y los destinos alternativos (RF39, RF08); «Registrar servicio» del pie.

## Atractivos y agenda — decisiones (iteración E, sesión A2; RF25)

Backend y documentos de la agenda de eventos (DEC-213). CU nuevos: [Registrar evento en agenda](../casos-de-uso/CU-registrar-evento-agenda.md) (nombre de la figura 5 del Entregable 2, con el flujo «Consultar eventos registrados») y [Consultar agenda de eventos](../casos-de-uso/CU-consultar-agenda-eventos.md). Contrato en [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md), secciones «Agenda de eventos». Esquema: [`modelo-nosql/eventos.schema.json`](../sad/modelo-nosql/eventos.schema.json) (M013).

**Fuente del esquema:** DocumentoEntregable2.md, §2.4 (documento del Proyecto, fuera del repositorio), colección `eventos`: `required` `gestorId`, `nombre`, `fechaInicio` y `fechaFin`; `ubicacion` y `descripcion` como texto opcional. También del Entregable 2: §1.2, figura 5 (CU «Registrar evento en agenda» del Gestor) y §2.1 (clase `EventoTuristico`; el Gestor registra, el Turista visualiza).

**Decisiones tomadas antes de la sesión (no se reabren):** DEC-214 (categorías de atractivo; sin migración nueva, la semilla ya cumple el `enum` desde M012) y DEC-215 (reglas de la agenda). Del pedido de la sesión: las fechas son de día, guardadas como `date` a las 00:00 de America/Bogota, y `ubicacion` va de 1 a 200.

**Decisiones de la sesión A2 (DEC-235 a DEC-242; propuestas por arquitectura y aprobadas por el usuario el 2026-11-05; DEC-242 separada de DEC-237 a pedido del usuario al cerrar la sesión, C6):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-235 | Campos obligatorios | `nombre`, `fechaInicio`, `fechaFin`, `ubicacion` y `descripcion` son obligatorios. **Justificación:** RF25 dice «capturando nombre, fecha de inicio, fecha de fin, ubicación y descripción», sin marcar ninguno como opcional; el §2.4 deja `ubicacion` y `descripcion` opcionales, pero el SRS es la fuente única de requisitos | `EventoTuristico`, M013 |
| DEC-236 | Límites y formato | `nombre` de 1 a 120 y `descripcion` de 1 a 1000, como en atractivos (DEC-207); `ubicacion` de 1 a 200; recortados con `strip()` y contados en puntos de código. Fechas como texto `yyyy-MM-dd` (año de cuatro cifras): otra forma o un día que no existe (`2026-02-30`) es `FORMATO` | `EventoTuristico`, `SolicitudEvento`, M013 |
| DEC-237 | Orden de las fechas | `fechaFin` igual o posterior a `fechaInicio`: si es anterior, `campos.fechaFin = NO_PERMITIDO`, y el `$expr` del validador lo exige también en la base. Un evento de un día (`fechaInicio = fechaFin`) es válido | `EventoTuristico.registrar`, M013 |
| DEC-238 | Panel del Gestor | **Ampliación** del §2.4: `fechaRegistro` (instante de `RelojPort`, obligatoria). El panel lista **todos** los eventos (también los vencidos), del más reciente al más antiguo (`fechaRegistro`, desempate por id), de 10 en 10; índice `eventos_registro` | `GestionarEventosService`, `EventoMongoAdapter`, M013 |
| DEC-239 | Agenda pública | `GET /api/v1/catalogo/eventos` sin token, como las preguntas frecuentes y los avisos (un Bearer que llegue se valida, como en todo endpoint público). RF25 dice «visible para el Turista»; DEC-215 la llama agenda pública y el catálogo no exige sesión. No expone `gestorId` ni `fechaRegistro` (como DEC-217). Índice `eventos_agenda` `{fechaInicio, _id, fechaFin}`: orden sin ordenar en memoria y el rango de `fechaFin` sobre el índice | `AgendaController`, `SecurityConfig`, M013 |
| DEC-240 | Desviación del Entregable 2 (categoría de atractivo) | El §2.4 define `atractivos.categoria` como texto libre; desde M012 es un `enum` (`Naturaleza`, `Cultura`; DEC-214, **decisión del equipo, no del SRS**). Se registra como desviación del Entregable 2; no hace falta migración nueva (M014): la semilla ya cumple el `enum` desde M012. Cierra **HC-E2** junto con el esquema de `eventos` | M012, punto 12 de «Pendientes para el SAD» |
| DEC-242 | Eventos ya terminados (P5) | `fechaFin` anterior a hoy en America/Bogota (`RelojPort`) → `campos.fechaFin = NO_PERMITIDO`. **Decisión del equipo, no del SRS:** RF25 no lo dice, pero un evento ya terminado nunca aparecería en la agenda (DEC-215). Solo en el dominio: el validador no puede depender de la fecha actual | `EventoTuristico.registrar` |
| DEC-241 | Sin `version` | Los eventos no llevan `version`: sin edición ni borrado (DEC-215) no hay escrituras concurrentes que controlar. Si un RF futuro pide editar, se agrega con una migración nueva (como M011) | M013 |

**Orden de aplicación en el volumen local (C1, O36):** la colección `migraciones` del volumen `sm_tours_aplication_mongo-data` llega a M011 (consulta del 2026-11-05, levantando solo `mongo` y deteniéndolo después): **M012 y M013 están pendientes de aplicar**. El próximo `docker compose up` aplica, con `mongo-migraciones`, **M012 y después M013**, en orden de número (`aplicar.js`). **El recorrido visual de la sesión B2 requiere ese `up`:** sin él no existe la colección `eventos` y la agenda no tiene datos.

**Estado de los RF de la sesión:**

| RF | Estado | Pruebas / pendiente |
|---|---|---|
| RF25 (agenda de eventos) | **Parcial (backend; sin recorrido visual)** | `EventoTuristicoTest`, `GestionarEventosServiceTest`, `ConsultarAgendaServiceTest`, `RegistrarEventoApiIntegracionTest`, `AgendaApiIntegracionTest`, `AccesoGestorApiIntegracionTest`, `MigracionesMongoTest`. Faltan las pantallas (B2) |

Avance: sigue en **39 de 64**; RF25 lleva a 40 al cerrar B2.

**Hallazgos y observaciones de la sesión A2:**

| # | Hallazgo | Estado |
|---|---|---|
| HC-E1 | RF25 sin CU | **Resuelto** (dos CU) |
| HC-E2 | El esquema de `eventos` no se podía verificar contra el Entregable 2 | **Resuelto:** el esquema de M013 parte del §2.4 (DocumentoEntregable2.md, documento del Proyecto, fuera del repositorio); las diferencias están en el punto 13 de «Pendientes para el SAD» |
| O36 | M012 y M013 están pendientes de aplicar en el volumen local: el último arranque fue anterior a la sesión A1 | **Cerrado** el 2026-11-05: el `docker compose up -d --build` aplicó M012 y después M013. Evidencia en «Aplicación de O36 y corrección O38 y O39» |
| O37 | Pruebas existentes modificadas:<br>- `MigracionesMongoTest`: la lista suma M013; «volver a aplicarlas» y «sin semilla» esperan M013; el validador de `eventos` se compara con `docs/`; casos nuevos de índices y del validador;<br>- `AccesoGestorApiIntegracionTest`: `endpoints()` suma `GET` y `POST /gestor/eventos`.<br>Conteo de ms-catalog: de 798 a 836 (`./mvnw clean test`, sin fallos) | Registrado |

**Verificación de la sesión A2:** `./mvnw test` completo en ms-catalog con Docker activo. Sin `docker compose up` (solo el servicio `mongo`, para C1).

**Recorrido visual:** no aplica en A2 (solo backend). El de B2 requiere el `docker compose up` que aplica M012 y M013 (O36).

**Fuera de alcance de la sesión A2:** las pantallas de la agenda (B2); editar, borrar o despublicar eventos (DEC-215); vincular un evento a un atractivo (el §2.4 guarda la ubicación como texto); notificaciones de eventos (ms-notificaciones).

## Aplicación de O36 y corrección O38 y O39 (iteración E; WF-09, WF-23)

Sesión del 2026-11-05 sobre `main` en `7595bdc` (merge del PR #32): `docker compose up -d --build`, prueba de humo y recorrido visual de B1 y de la iteración D. El recorrido encontró dos defectos del frontend (O38 y O39), corregidos en la rama `fix/csp-blob-historial-360`.

**Aplicación de O36 (`mongo-migraciones`):** de M001 a M011, «ya aplicada»; `aplicando: M012__atractivos-gestion` («validador v2 (atractivos); version en 7 y fechaRegistro en 7 atractivos») y después `aplicando: M013__eventos` («colección eventos creada con su validador»; «índices eventos_agenda y eventos_registro»). `postgres-bases` terminó con 0 («Bases de datos al día: auth_db y reservas_db»).

**Verificación del volumen (solo lectura, `catalog_db`):**

| Comprobación | Resultado |
|---|---|
| `migraciones` | `M011__servicios-version` (2026-10-30T20:28:07.103Z), `M012__atractivos-gestion` (2026-11-05T16:41:45.612Z), `M013__eventos` (2026-11-05T16:41:45.778Z) |
| `atractivos` | 7 documentos tras el `up`, todos con `version: 0` y `fechaRegistro`; validador igual a `M012__atractivos.v2.schema.json` (comparación normalizada), `strict` y `error`. Tras el recorrido: 8 (el atractivo de prueba, abajo) |
| `eventos` | Colección creada, 0 documentos; validador igual a `M013__eventos.v1.schema.json` (`$jsonSchema` y `$expr`), `strict`; índices `eventos_agenda` `{fechaInicio: 1, _id: 1, fechaFin: 1}` y `eventos_registro` `{fechaRegistro: -1, _id: -1}` |

**Prueba de humo (gateway, sin token):** `GET /api/v1/catalogo/eventos` → 200 `{"elementos":[],"pagina":0,"tamano":10,"totalElementos":0,"totalPaginas":0}`; `GET /api/v1/catalogo/atractivos/6ad2be3e06a2d5c9d602cec9` → 200, sin `gestorId` ni `version`; `GET /api/v1/catalogo/gestor/atractivos` y `GET /api/v1/catalogo/gestor/eventos` → 401. El nginx del frontend sirve la SPA en `/gestor/atractivos` y `/atractivos/{id}` (200).

**Recorrido visual:** Microsoft Edge visible manejado con Playwright desde fuera del repositorio; el usuario inició sesión en la ventana (O18). 1366 px y 360 px (emulación móvil por CDP: con la barra de desplazamiento clásica del escritorio, 360 px deja 345 de contenido y aparenta un desborde que un teléfono no tiene), en ES y EN. Gestor: WF-24, WF-09 (alta con una imagen, edición y diálogo de cambio de estado) y la ficha `/atractivos/:id`. Prestador: WF-18, WF-08 (RNT en solo lectura), WF-20 a WF-23. Sin claves de i18n sin traducir ni respuestas 4xx o 5xx de la API.

**Hallazgos:**

| # | Hallazgo | Estado |
|---|---|---|
| O38 | WF-09 (alta de atractivo), solo en el contenedor: la vista previa de la foto elegida no se ve (se muestra su texto alternativo). La CSP de `frontend/nginx/seguridad.conf` no permitía `blob:` en `img-src` y la vista previa usa `URL.createObjectURL`. Error de consola: «Loading the image 'blob:http://localhost:8000/…' violates the following Content Security Policy directive: "img-src 'self' data: https://tile.openstreetmap.org"». Vite no aplica esa CSP: no se ve en desarrollo ni en Vitest. El alta funcionaba igual | **Resuelto** (DEC-243) |
| O39 | WF-23 «Historial de reservas» a 360 px: la página mide 460 px (ES) y 468 px (EN). La pista `1fr` de `.reserva__campos` (`minmax(auto, 1fr)`) no baja del ancho mínimo de su contenido, y el `select` de servicio toma el de su opción más larga («Bicicletas Centro Histórico · Alquiler de equipos») | **Resuelto** (DEC-244) |

**Decisiones de la corrección (DEC-243 y DEC-244; aprobadas por el usuario el 2026-11-05):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-243 | Vista previa de las fotos (O38) | `blob:` en `img-src`, y en ninguna otra directiva: `default-src`, `script-src`, `connect-src` y `font-src` siguen en `'self'`. Una URL `blob:` solo la crea la propia página con un archivo que el usuario eligió | `frontend/nginx/seguridad.conf`; prueba `src/app/seguridadNginx.test.ts` (lee el archivo con `?raw`) |
| DEC-244 | Campos de los filtros a 360 px (O39) | `min-width: 0` en `.reserva__campos > .campo`, y `min-width: 0; width: 100%` en `.reserva__campos .campo__control select` (solo en esa grilla; no toca `admin.css`). A 1366 px el `select` de WF-23 pasa a medir lo mismo que los campos de fecha. Afecta también a WF-10 y WF-11, que usan la misma grilla | `frontend/src/features/reservas/reservas.css`. Sin prueba automática: jsdom no calcula el diseño; lo cubre el recorrido |

**Verificación de la corrección:**

- `npm test`: de 761 pruebas en 74 archivos a 764 en 75, sin fallos. `npm run build` (`tsc -b` y `vite build`): sin errores.
- `docker compose up -d --build frontend`; cabecera servida: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://tile.openstreetmap.org; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`.
- WF-09 alta (Gestor; foto elegida, formulario sin enviar): vista previa con `naturalWidth` 800 en ES y EN, a 1366 y 360 px; sin errores de CSP en la consola.
- `scrollWidth` y `clientWidth` a 360 px, en ES y EN: WF-23 360 y 360; WF-11 360 y 360; WF-10 360 y 360 (Alojamiento `6ad2be3e06a2d5c9d602cebc` y Tour `6ad2be3e06a2d5c9d602cec5`, sin reservar). A 1366 px, 1366 y 1366 en todas.

**Datos de prueba en el volumen local:** atractivo `6aecb3b0b4962254d113323d` («Prueba recorrido O36»), creado por el Gestor de la semilla en el recorrido con una imagen en GridFS (`6aecb3b0b4962254d113323e`); `limiteCapacidadCarga` de 50 a 60 (versión 1); estado de Abierto a Cerrado temporalmente (versión 2) y, en la verificación de la corrección, de vuelta a Abierto (versión 3). No hay borrado de atractivos (DEC-206): queda en el volumen.

## Atractivos y agenda, frontend — decisiones (iteración E, sesión B2; RF25)

Pantallas de la agenda de eventos sobre el backend de A2 (PR #32). Un solo PR desde `feature/eventos-agenda-frontend`. CU de referencia: [Registrar evento en agenda](../casos-de-uso/CU-registrar-evento-agenda.md) y [Consultar agenda de eventos](../casos-de-uso/CU-consultar-agenda-eventos.md); solo cambia su línea «Pantallas». Wireframes nuevos, propuestos por arquitectura y validados por el equipo: [WF-25](../wireframes/WF-25-eventos-gestor.puml) (panel y registro del Gestor) y [WF-26](../wireframes/WF-26-agenda-eventos.puml) (agenda pública). Sin cambios en el backend.

**Decisiones que no se reabren:** DEC-215, DEC-235 a DEC-242, DEC-47 (públicos sin `Authorization`), DEC-176 (capas), DEC-233 (menú y pie del Gestor) y DEC-243 (CSP). Riesgo de A2: el frontend **no recalcula «hoy»**.

**Decisiones de la sesión B2 (DEC-245 a DEC-251; propuestas por arquitectura y aprobadas por el usuario el 2026-11-05, con las condiciones C1 a C4):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-245 | Rutas y alta (D1) | `/agenda` (pública), `/gestor/eventos` y `/gestor/eventos/nuevo` (Gestor). El alta es una **página propia**, no un diálogo. **Justificación:** el patrón de WF-24 y WF-09; el CU dice que el Gestor «abre "Registrar evento" desde su panel» y el menú necesita una ruta; cinco campos y una descripción de 1000 no caben bien en un diálogo a 360 px | `App.tsx`, `RegistrarEventoPage.tsx` |
| DEC-246 | Enlace público (D2) | «Agenda de eventos» en la columna «Municipio» del pie, junto a «Noticias» (pendiente). El encabezado (navegación v2) y el inicio (WF-04) no cambian. **Mejora de diseño** (elemento común, README de wireframes). **Justificación:** los eventos son contenido del municipio; no altera la navegación común ni agrega una petición al inicio; es visible para todos (RF25) | `PiePagina.tsx` |
| DEC-247 | Presentación (D3) | **Agenda:** tarjetas en el orden del servidor, con el bloque del día y el mes de inicio, nombre, fechas, lugar y la descripción con sus saltos de línea (`pre-line`, como DEC-227); sin insignias de «Hoy» o «En curso». **Panel:** tabla como WF-24 con nombre, fechas, ubicación y registro; sin acciones (DEC-215), sin el Gestor (uuid sin nombre, como DEC-144) ni marca de «vencido». **Justificación:** las marcas exigirían calcular «hoy» en el cliente | `AgendaPage.tsx`, `agenda.css`, `EventosGestorPage.tsx` |
| DEC-248 | Validación en el cliente (D4) | Los límites del servidor (1 a 120, 200 y 1000, con `trim` y en puntos de código), `FORMATO` (`esFechaIso`) y fin anterior al inicio → `NO_PERMITIDO` (comparación de texto `yyyy-MM-dd`, sin zona). **«No anterior a hoy» lo decide solo el servidor:** sin validación local ni `min` en los `input`. Un 400 `fechaFin = NO_PERMITIDO` con el fin no anterior al inicio en el borrador solo puede ser DEC-242: se muestra «La fecha de fin ya pasó (hoy en Santa Marta)». **Justificación:** una sola fuente de «hoy» (`RelojPort`, Bogotá); el reloj o la zona del navegador fallarían cerca de la medianoche | `borradorEvento.ts` |
| DEC-249 | Estados (D5) | Agenda vacía: «No hay eventos próximos» con «Explorar la oferta turística» (FA1); página fuera de rango: a la última (FA2); `pagina` inválida: se lee como 1, sin 400 (FA3); 503 o red: aviso con «Reintentar». Panel: lo mismo y «Sin permiso» ante un 403. Alta: 400 por campo con foco en el primero (lo que no tiene control, mensaje general); 403, 503 y red, mensaje general con foco; un solo envío a la vez; nada borra lo escrito; el 201 vuelve al panel con «Evento registrado: ya está publicado en la agenda» | `AgendaPage.tsx`, `EventosGestorPage.tsx`, `RegistrarEventoPage.tsx` |
| DEC-250 | Capas y fechas de día | La agenda pública en `features/agenda/`; el Gestor en `features/gestor/`. **Imports entre features aceptados (C4):** `gestor → agenda/fechasEvento` y `agenda/agendaApi`, y los ya existentes hacia `prestador` (`CampoServicio`, `rutasCampos`, `useMisServicios`), `admin` (`textoAcotado`, `PaginaCuentas`), `reservas` (`fechasBogota`) y `catalogo` (`formato`). Nada en `shared/`, que no depende de `features/` (DEC-176). Las fechas de día se formatean **solo** con `formatearFecha` (UTC) y `Intl.DateTimeFormat.formatRange` en UTC; nunca con `formatearDia` (O42) | `fechasEvento.ts` |
| DEC-251 | Acceso del Gestor | Menú: «Eventos» y «Registrar evento», después de los atractivos. «Panel del gestor» del pie sigue en `/gestor/atractivos` (DEC-233); WF-24 no cambia | `MenuUsuario.tsx` |

**Estado de los RF de la sesión:**

| RF | Estado | Pruebas / pendiente |
|---|---|---|
| RF25 (agenda de eventos) | **Implementado** | Backend de A2 y, en el frontend, `agenda.test.tsx`, `fechasEvento.test.ts`, `borradorEvento.test.ts`, `registrarEvento.test.tsx`, `eventosGestor.test.tsx`, `rutasGestor.test.tsx`, `piePagina.test.tsx` y `elementosComunes.test.tsx`; recorrido visual abajo |

Avance: de **39 a 40 de 64**. La iteración E está cerrada.

**Hallazgos y observaciones de la sesión B2:** sin Hallazgos Críticos (RF25 tiene dos CU; el modelo es de ms-catalog y no cruza bases; cada decisión está justificada).

| # | Hallazgo | Estado |
|---|---|---|
| O40 | El merge del PR #33 se rehízo con force-push; `4c7b9db` (con `6bc1c09`) quedó huérfano; `main` = `eb0e82d` (con `2ad232b`); árbol idéntico (`git diff` vacío). Riesgo de proceso: recomendada protección de rama en `main` sin force-push | **Confirmado por el usuario** el 2026-11-05. No bloquea |
| O41 | `Location` del 201 apunta a `GET /gestor/eventos/{id}`, que no existe (ya documentado en el contrato). El frontend no lo usa | Registrado |
| O42 | `formatearDia` (para instantes) mostraría «2026-11-20» como el 19 en Bogotá: lo lee como 00:00 UTC | Evitado con DEC-250; `fechasEvento.test.ts` lo prueba en dos zonas |
| O43 | Sin `maximo`, `CampoServicio` toma `LONGITUD_MAXIMA['descripcion'] = 2000` de WF-08 | Evitado: el alta pasa 120, 200 y 1000 explícitos (`MAXIMO_EVENTO`) |
| O44 | Los dos CU decían «sin wireframe todavía» | **Resuelto:** línea «Pantallas» actualizada con permiso |
| O45 | Prueba existente modificada fuera de los 21 archivos del plan: `elementosComunes.test.tsx` (la columna «Municipio» del pie suma «Agenda de eventos» y su `href`) | Aceptado a posteriori: el archivo no estaba en el plan aprobado; el permiso se pidió durante el Paso 3, después de que la prueba fallara |

**Verificación de la sesión B2:**
- `npm test`: de **764 pruebas en 75 archivos a 808 en 80**, sin fallos. `npm run build` (`tsc -b` y `vite build`): sin errores.
- `docker compose up -d --build --no-deps frontend` (N26); el resto de los contenedores ya estaba arriba.
- **Recorrido visual** (2026-11-05): Chromium visible manejado con Playwright desde el scratchpad, fuera del repositorio; el usuario inició sesión en la ventana (O18). Pantallas: agenda sin sesión desde el pie (vacía y con el evento), panel del Gestor (vacío y con el anuncio) y alta (vacía, errores por campo, fin anterior al inicio y fin ayer), a 1366 y 360 px, en ES y EN. Sin desbordes horizontales. Consola: solo el 400 esperado del caso «fin ayer». API: un 400 `DATOS_INVALIDOS` con `campos.fechaFin = NO_PERMITIDO` (fin ayer, no guardó nada) y un 201. El caso «fin anterior al inicio» no envió ningún POST.

**Datos de prueba en el volumen local (C3):** evento `6aecd33cb4962254d1133241` («Prueba recorrido B2»), `fechaInicio = fechaFin = 2026-11-05` (hoy en Bogotá), creado por el Gestor de la semilla (`00000000-0000-4000-8000-000000000003`) el 2026-11-05T19:04:28.948Z. No hay borrado de eventos (DEC-215): queda en el volumen y sale de la agenda a las 00:00 de Bogotá del 2026-11-06.

**Fuera de alcance de la sesión B2:** editar, borrar o despublicar eventos (DEC-215); filtros de la agenda y ficha propia de un evento (CU); un bloque «Próximos eventos» en el inicio (pendiente opcional de DEC-246); notificaciones de eventos (ms-notificaciones).
