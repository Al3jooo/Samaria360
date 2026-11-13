# Decisiones de la iteración C «Registrar y aprobar servicio»

Parte de la [matriz de trazabilidad](../trazabilidad.md): decisiones (DEC), hallazgos y verificación de cada sesión, separadas del archivo principal en la revisión del 2026-11-13. Los números DEC son únicos en todo el proyecto.

## Registrar y aprobar servicio — decisiones (RF09, RF10, RF37, RF21)

Iteración C del plan del 60 % (DEC-54), dividida en cuatro PR (DEC-134): **A1** (esta sesión: ms-catalog, ms-auth y documentos), **A2** (ms-booking: materializar el inventario bajo demanda, DEC-135, y enmienda de ADR-007), **B1** (frontend del Prestador: WF-08, WF-18 y RNT y dirección en WF-06) y **B2** (frontend del Administrador: WF-19). CU: [«Registrar servicio comercial»](../casos-de-uso/CU-registrar-servicio.md) (con el flujo «Consultar mis servicios»; el nombre se corrigió en B1, N22) y [«Aprobar o rechazar servicio»](../casos-de-uso/CU-aprobar-rechazar-servicio.md). Contrato en [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md); el evento, en la [enmienda de la iteración C de ADR-008](../sad/adr/ADR-008-sincronizacion-estado-cuentas.md).

**Decisiones del equipo (DEC-124 a DEC-135; se registran aquí, no se volvieron a discutir):**

| ID | Tema | Decisión |
|---|---|---|
| DEC-124 | Dueño y estados | ms-catalog (`servicios`). Estado inicial `Pendiente` («Pendiente de aprobación» en la interfaz). Transiciones solo Pendiente → Publicado y Pendiente → Rechazado |
| DEC-125 | Quién registra | Token con rol Prestador **y** fila en `cuentas` con `estado = Activo`; sin fila o con otro estado, 403. `prestadorActivo` inicial sale de la proyección |
| DEC-126 | HC2 | `direccion` obligatoria (ampliación en M008). `CuentaActualizada` y la proyección `cuentas` llevan `rnt` (solo Prestador); el servicio guarda una copia al registrarse. V17 encola los eventos de los prestadores existentes. El RNT no tiene RF de edición: la copia no se desactualiza |
| DEC-127 | RF37 | `precioBase` obligatorio en todas las categorías, mostrado como «desde». En Alojamiento sigue derivado de las unidades; en Gastronomía se conserva además `rangoPrecioPlato` |
| DEC-128 | RF21 | Listado y `POST …/aprobacion` `{decision, justificacion}` (patrón de RF28). El rechazo exige la justificación (`motivoRechazo`, ampliación). 409 `ESTADO_CAMBIADO`. `Rechazado` es final en esta iteración (la edición es de la iteración D) |
| DEC-129 | Resultado para el prestador | `GET /prestador/servicios` con el estado y el motivo del rechazo, como flujo del CU «Registrar servicio comercial». Ampliación del modelo de CU (como N15) |
| DEC-130 | Oferta | El registro captura la **oferta inicial**. No reabre DEC-34: RF19 sigue fuera de alcance. Ampliación de RF10 («Diferencias con el SRS») |
| DEC-131 | RF27 y RF58 | El registro captura `condicionesAccesibilidad` y `horarioAtencion` |
| DEC-132 | Ubicación | Coordenadas obligatorias (pin en el mapa) y `direccion`; sin geocodificación. Sin imágenes en C (GridFS en la iteración E, DEC-55) |
| DEC-133 | Unidades | Alojamiento con 1 a N unidades (nombre, tipo de habitación, habitaciones, capacidad, `precioNoche`) |
| DEC-134 | Entrega | Cuatro PR: A1, A2, B1 y B2 (arriba) |
| DEC-135 | Inventario | El catálogo es dueño de la **definición** de la oferta; ms-booking la materializa bajo demanda en A2 (`INSERT … ON CONFLICT DO NOTHING`), leyéndola del detalle público (ADR-007). En A1 solo se define, se guarda y se expone |

**Decisiones de la sesión A1 (DEC-136 a DEC-151; propuestas por arquitectura y aprobadas con el plan):**

| ID | Tema | Decisión y justificación técnica | Cambio |
|---|---|---|---|
| DEC-136 | Forma de la oferta | Solo Tour y Alojamiento (**decidido por el equipo** en la sesión): `oferta.diasOperacion` (1 a 7 de `Lunes`…`Domingo`, sin tildes y sin repetir) y, en Tour, `oferta.salidas` (1 a 12 horas `HH:mm`, sin repetir, en orden). Gastronomía y Alquiler no la tienen. La capacidad no se repite: `aforoMaxGrupo` por salida y `numHabitaciones` por unidad y noche. Sin horizonte ni fechas excluidas. **Justificación:** es lo que modela `inventario_cupo` (fecha y hora en Tour; unidad y noche en Alojamiento; CHECK `categoria IN ('Tour','Alojamiento')`) y lo que hoy hardcodea D5 (todos los días; 08:00 y 14:00). El horizonte es la ventana de ms-booking (60 días, A2); excluir fechas es RF19 | M008; `Oferta`, `DiaSemana` |
| DEC-137 | Coordenadas | Solo el rango global (**decidido por el equipo**): el SRS no fija un límite geográfico. Entrada `{longitud, latitud}`, como `ElementoOferta.coordenada`; se guarda como GeoJSON `[longitud, latitud]` | `RegistroServicio` |
| DEC-138 | Límites por campo | Los de [`sad/api/ms-catalog.md`](../sad/api/ms-catalog.md) («Registrar»). Textos con `strip()` y en puntos de código (como `maxLength`); precios > 0, con 2 decimales y hasta 99 999 999,99 (`reserva.precio_unitario numeric(10,2)`); los máximos numéricos son técnicos, no reglas de negocio. Teléfono y correo, con las reglas de ms-auth | `RegistroServicio` |
| DEC-139 | 403 propio | 403 `PRESTADOR_NO_HABILITADO` sin fila en `cuentas`, con estado distinto de `Activo` o sin RNT; el 403 por rol sigue siendo `ACCESO_DENEGADO`. **Justificación:** el usuario es el dueño de la cuenta (no hay enumeración) y B1 necesita distinguir «sin permiso» de «cuenta no habilitada». Se evalúa antes que los datos | `CodigoError` |
| DEC-140 | Tope por prestador | Sin tope: el SRS no lo define y los listados están paginados | — |
| DEC-141 | Campos del servidor | `prestadorId` (= `sub`), `estadoPublicacion`, `prestadorActivo`, `rnt`, `idUnidad`, `fechaRegistro`, `motivoRechazo` y el `precioBase` de un alojamiento se ignoran si llegan en el cuerpo; el servidor genera los `idUnidad` | `SolicitudServicio` |
| DEC-142 | Carrera registro ↔ RF22 | (a) El registro relee la proyección después de insertar y, si ya no está Activa, oculta ese servicio. (b) `ActualizarCuentaService` repite `fijarPrestadorActivo` después de guardar la proyección de un Prestador. El orden fijar → guardar se conserva por los reintentos. **Justificación:** un `insert` anterior a la segunda fijación lo corrige (b); uno posterior ve la proyección guardada (a) | `RegistrarServicioService`, `ActualizarCuentaService` |
| DEC-143 | «Mis servicios» | Listado (páginas de 10, del más reciente al más antiguo, filtro de estado) y detalle propio `GET /prestador/servicios/{id}` (404 si es de otro). **Justificación:** el 201 necesita un `Location` válido y el detalle público responde 404 a un Pendiente | `ServicioPrestadorController` |
| DEC-144 | Cola del Administrador | `estado` por defecto `Pendiente`, del más antiguo al más reciente, páginas de 10; detalle sin la regla de visibilidad, con el RNT, `prestadorActivo` y el estado de la cuenta según la proyección (el nombre del prestador vive en auth_db y no se compone) | `ServicioAdminController` |
| DEC-145 | Decisión | Justificación de 1 a 500 sin los extremos, obligatoria al rechazar e ignorada al aprobar (como DEC-42). UPDATE condicional sobre `Pendiente`; 404 o 409 `ESTADO_CAMBIADO` con `estadoActual`. Guarda `revisadoPor` y `fechaRevision` (como `prestador.revisado_por` y `revisado_en`; preparan RF13). Aprobar a un prestador inactivo se permite: queda Publicado y oculto | `AdministrarServiciosService`, `Revision` |
| DEC-146 | RNT en el evento | `EventoCuentaActualizada.rnt`: obligatorio con `rol = Prestador`, ausente en otro rol. Va en la carga del outbox (evento autocontenido) y el relevo lo envía solo si existe. ms-catalog lo guarda solo para un Prestador; ms-booking lo ignora (verificado: sin `FAIL_ON_UNKNOWN_PROPERTIES`) | ms-auth: evento, outbox, relevo; ms-catalog: `Cuenta`, M008 |
| DEC-147 | V17 | Un evento por prestador, en cualquier estado, con el estado derivado en SQL (precedencia de `dePrestador`) y `version_cuenta + 1`. **Justificación:** sin subir la versión, una proyección que ya tenía al prestador ignoraría el evento. Efecto: un 401 `SESION_INVALIDADA` una vez para el token de un prestador anterior (como L7) | `V17__backfill_rnt_prestadores.sql` |
| DEC-148 | M008 | Validador v3 de `servicios` (detalle en «Pendientes para el SAD (modelo NoSQL)», punto 10) y v2 de `cuentas`. Lo derivable (`fechaRegistro` desde el `_id`; la oferta de D5) se exige y se rellena; `direccion`, `rnt` y la revisión, opcionales en el validador y obligatorios en el dominio. **Justificación:** la semilla de `init/` existe aunque `MONGO_SEMILLA=false`. M008 aborta sin tocar nada si un servicio no tiene `precioBase`; la semilla no tiene ninguno (0 de 14, ajuste 1 de la aprobación) | M008 |
| DEC-149 | M009 | Semilla: dirección ficticia de cada servicio (por `nombreComercial`) y RNT de la semilla de ms-auth (`RNT-00000` y `RNT-00001`, por `prestadorId`) | M009 |
| DEC-150 | Detalle público | `GET /servicios/{id}` suma `direccion`, `rnt` y `oferta`; no expone `estadoPublicacion` ni la revisión. A2 lee `oferta` de esta respuesta | `RespuestaServicio` |
| DEC-151 | Errores | `PRESTADOR_NO_HABILITADO` (403) y `SERVICIO_NO_ENCONTRADO` (404); `ESTADO_CAMBIADO` lleva el `estadoActual` real del servicio (avisos no cambia). Motivos nuevos: `DUPLICADO`, `VALOR_MINIMO`, `VALOR_MAXIMO`, `CANTIDAD_MINIMA`, `CANTIDAD_MAXIMA`. Campos anidados con punto e índice | `CodigoError`, `MotivoInvalido` |

**Cambios de esquema:**

| Migración | Qué hace |
|---|---|
| `esquema/M008__registro-servicios.js` (catalog_db) | Validador v3 de `servicios` y v2 de `cuentas` (cada uno con su copia); rellena `fechaRegistro` (desde el `_id`) y la oferta de Tour y Alojamiento (la de D5); índices `admin_estado_fecha` y `prestador_fecha`. Aborta sin tocar nada si un servicio no tiene `precioBase`, y antes del `collMod` si algún documento no cumple el v3 |
| `semilla/M009__semilla-direccion-rnt.js` (catalog_db; solo `MONGO_SEMILLA=true`) | Dirección y RNT de los 14 servicios de la semilla |
| `V17__backfill_rnt_prestadores.sql` (ms-auth, `db/migration`) | Un evento `CuentaActualizada` con RNT por prestador y `version_cuenta + 1` |

**Respuesta que consume A2** (`GET /api/v1/catalogo/servicios/{id}`, sin cambios en lo que ms-booking ya lee): `"oferta": {"diasOperacion": ["Lunes", …], "salidas": ["08:00", "14:00"]}` en Tour, `{"diasOperacion": [...]}` en Alojamiento y `null` en Gastronomía y Alquiler. La capacidad: `detalleCategoria.aforoMaxGrupo` (por salida) y `detalleCategoria.unidades[].numHabitaciones` (por unidad y noche).

**Estado de los RF de la iteración:**

| RF | Estado | Qué falta |
|---|---|---|
| RF09 (registrar servicios) | **Parcial (backend)** | B1: WF-08 y WF-18 (pantallas del Prestador) |
| RF10 (campos por categoría) | **Parcial (backend)** | B1: WF-08 con el editor de unidades y la oferta |
| RF37 (precio de cada servicio) | **Parcial (backend)** | B1: el precio «desde» en el registro y en «Mis servicios» (el detalle y las tarjetas ya lo muestran) |
| RF21 (aprobar o rechazar servicios) | **Parcial (backend)** | B2: WF-19 (pantalla del Administrador) |

Avance: sigue en **26 de 64**; la iteración C suma RF09, RF10, RF37 y RF21 al cerrar B2 (26 → 30; meta 39, DEC-54). RF27 y RF58 avanzan (el prestador ya los declara en el backend) y siguen en la iteración E.

**Hallazgos de esta iteración:**

| # | Hallazgo | Estado |
|---|---|---|
| HC2 | RNT y dirección en WF-06 | **Resuelto en el backend** (DEC-126, DEC-146, DEC-147, DEC-150): réplica del RNT por el evento, como `prestadorActivo`, y `direccion` en `servicios`. **Cerrado en B1** (DEC-170): WF-06 los muestra |
| HC1 (parte de C) | RF09, RF10, RF37 y RF21 no tenían CU especificado | **Resuelto para estos RF** con los dos CU de la iteración. HC1 sigue abierto para los demás |
| N21 | RF21 remite a RF08 (errata: RF09) y usa «Pendiente de aprobación» | Registrado en «Diferencias con el SRS» |
| O8 | Pruebas existentes modificadas. **ms-auth:** `CuentaAdministradaTest`, `AdministrarCuentasServiceTest` y los fakes `CuentasAdminEnMemoria` y `OutboxEnMemoria` (el prestador y su evento llevan el RNT); `CambiarRolServiceTest`, `RelevarCuentasServiceTest`, `CambioRolTest` y `RelevoCuentasIntegracionTest` (el evento suma `rnt`, nulo fuera del Prestador); `MigracionesAdministrarCuentasIntegracionTest` y `UsuarioPersistenceAdapterTest` (la lista llega a V17); `MigracionesCambioRolIntegracionTest` (mide V15 y V16 hasta V16: V17 encola a los prestadores); `PerfilApiIntegracionTest` (el token del prestador usa su versión real: V17 la subió). **ms-catalog:** `ActualizarCuentaServiceTest` (DEC-142 b: la visibilidad se fija dos veces); `ServicioApiIntegracionTest` (las claves nuevas del detalle); `FavoritoApiIntegracionTest` y `MasFiltrosEquivalenciaTest` (sus documentos cumplen el validador v3); `MigracionesMongoTest` (M008 y M009; las copias vigentes son las de M008; `fechaRegistro` se deriva del `_id`, que es aleatorio). Las pruebas de avisos no cambiaron (ajuste 6) | Observación |

**Fuera de alcance de A1:** las pantallas (B1, B2), la materialización del inventario y la enmienda de ADR-007 (A2), editar o reenviar un servicio (iteración D), imágenes (iteración E), RF19.

## Inventario bajo demanda — decisiones (iteración C, sesión A2; RF10, RF11, RF20)

Sesión A2 de la iteración C (DEC-134): ms-booking materializa la oferta del catálogo en `inventario_cupo` (DEC-135) y se retira el generador de inventario demo (D5). CU: [«Realizar reserva» e incluido «Verificar disponibilidad»](../casos-de-uso/CU-realizar-reserva.md). Arquitectura: [enmienda A2 de ADR-007](../sad/adr/ADR-007-reservas-y-catalogo.md). Contrato: [`sad/api/ms-booking.md`](../sad/api/ms-booking.md) («Disponibilidad»). No cambia el frontend ni ms-catalog.

**Decisiones de la sesión A2 (DEC-152 a DEC-160; propuestas por arquitectura y aprobadas con el plan):**

| # | Tema | Decisión | Cambio |
|---|---|---|---|
| DEC-152 | Dónde se materializa | En la disponibilidad, el resumen de las tarjetas y reservar. Siempre la ventana completa (hoy a hoy + 59), nunca fechas pasadas, y solo si la marca no cubre la última fecha pedida. Reservar usa la oferta que ya leyó (sin otra llamada), después de `planificar` (así Gastronomía y Alquiler fallan antes) y antes de la transacción. **Justificación:** sin el resumen, un servicio recién aprobado aparecería «Sin fechas» en las tarjetas; la ventana completa cuesta una llamada por servicio y por día | `MaterializarInventarioService`, `DisponibilidadService`, `ReservarServicioService` |
| DEC-153 | Horizonte | 60 días (`MaterializacionOferta.DIAS_HORIZONTE`). No es una regla nueva: DEC-136 ya fijó el horizonte en la ventana de ms-booking, y WF-10 usa la misma. `RangoFechas.MAXIMO_DIAS` (largo de una consulta) no cambia. El SRS no fija una anticipación máxima | `MaterializacionOferta` |
| DEC-154 | Registro de lo materializado | Tabla de control `oferta_materializada` (V5): «materializado hasta F». Hasta F, un día sin filas no opera; después, falta materializar. Un 404 no avanza la marca; otra categoría o sin oferta, sí (sin filas). **Justificación:** sin marca, cada resumen haría hasta 10 llamadas y un día sin filas sería ambiguo; frente a una caché en memoria, es durable y la comparten las réplicas (RNF03) | V5, `OfertaMaterializadaPort`, `OfertaMaterializadaJdbcAdapter` |
| DEC-155 | Catálogo 404 o caído | Disponibilidad: rango cubierto → 200 sin llamar; sin cubrir y caído → 503 `SERVICIO_NO_DISPONIBLE`; 404 → lo que ya había. Resumen: corta en el primer fallo y tiene un **plazo total de 2,5 s** (`samaria360.disponibilidad.plazo-materializacion-resumen`; ejecutor acotado de 4 hilos y 16 en espera): si se agota o el ejecutor está lleno, 503. Reservar sin cambios. Nunca se borran filas. **Justificación:** una llamada puede tardar ≈ 4 s y el gateway corta a los 5 s; un «Sin fechas» falso confunde más que el «—» de las tarjetas | `ResumenDisponibilidadConPlazo`, `application.yml` |
| DEC-156 | Generador D5 | Se retira (`AsegurarInventarioDemoService`, su puerto, su programador, `CatalogoPort.listarPublicados`, `PaginaCatalogo`, `INVENTARIO_DEMO_HABILITADO`). Con la oferta que M008 rellenó en la semilla, las filas son las mismas (lo prueban `MaterializacionOfertaTest` y `MaterializacionIntegracionTest`). Un volumen existente conserva sus filas | Código, Compose, `.env.example`, README |
| DEC-157 | Lectura de la oferta | Lector tolerante: sin `oferta`, nada que materializar. Los días (`Lunes`…`Domingo`) se traducen a `DayOfWeek` en el adaptador; un día o una hora fuera del contrato es una respuesta inesperada. Un aforo o unas habitaciones ≤ 0 no producen filas | `CatalogoHttpAdapter`, `OfertaOperativa` |
| DEC-158 | Concurrencia | Fuera de toda transacción de reserva, en una sentencia en lote; orden total (fecha, unidad, hora); `ON CONFLICT DO NOTHING`; la marca avanza después, con `GREATEST`. Si algo falla entre las dos, la siguiente petición repite sin efectos | `MaterializacionOferta.ORDEN_INSERCION` |
| DEC-159 | ADR-007 | Enmienda A2 (no es un ADR nuevo): la disponibilidad y el resumen dependen del catálogo solo cuando falta materializar | ADR-007 |
| DEC-160 | CU | Se especifican «Realizar reserva» y su incluido «Verificar disponibilidad» con los nombres del Entregable 1 (DEC-59), trazados a RF10 (oferta), RF11, RF20 y RF37. «Reservar servicio» queda como nombre histórico de D1 a D10. «Cancelar reserva» (RF18, RF44) sigue sin archivo; RF32 es del subsistema «Gestionar servicios» | `CU-realizar-reserva.md`, matriz (WF-06, WF-10, endpoints) |

**Cambios de esquema:**

| Migración | Qué hace |
|---|---|
| `V5__oferta_materializada.sql` (ms-booking, `db/migration`) | Tabla `oferta_materializada` (`id_servicio_mongo varchar(24)` como en `inventario_cupo`, `materializada_hasta`, `fecha_actualizacion`); PK por servicio. La próxima migración de ms-booking es V6 |

**Estado de los RF de la iteración:** A2 no cambia el conteo (sigue en **26 de 64**). RF09, RF10, RF37 y RF21 siguen en **Parcial (backend)**: faltan B1 (WF-08 y WF-18, RNT y dirección en WF-06) y B2 (WF-19). RF11 y RF20 siguen implementados; ahora respetan la oferta de cada servicio.

**Hallazgos y observaciones de A2:**

| # | Hallazgo | Estado |
|---|---|---|
| HC1 (parte de A2) | El CU «Realizar reserva» no tenía documento (solo el diagrama del Entregable 1 y la matriz) | **Resuelto para «Realizar reserva» y «Verificar disponibilidad»** con `CU-realizar-reserva.md`. «Cancelar reserva» sigue sin archivo (HC1 abierto) |
| O10 | Pruebas existentes modificadas en ms-booking: `ReservarServicioServiceTest` (la secuencia de eventos suma la marca entre el catálogo y la transacción; constructor), `DisponibilidadServiceTest`, `CancelarReservaServiceTest` y `CompletarReservasServiceTest` (constructor), `PoliticaReservaTest` (el tour sin precio sale de `Ofertas`), `CatalogoHttpAdapterIntegracionTest` (casos de la oferta) y los dobles `CatalogoFalso`, `InventarioEnMemoria` (evento `inventario.asegurar`), `CatalogoSimulado` y `Ofertas` (sin la búsqueda de D5; con oferta opcional). Se eliminaron `AsegurarInventarioDemoServiceTest` (5) y `GeneradorInventarioIntegracionTest` (1), reemplazadas por las de equivalencia. Conteo: 167 → 205 | Observación |
| O11 | Un resumen anónimo con ids que tienen forma de ObjectId pero no existen llama al catálogo cada vez (el 404 no deja marca para que un servicio publicado después sí se materialice). El 404 es rápido y no abre el circuito | Riesgo aceptado; revisar con las pruebas de carga (RNF03, ADR-007) |
| O12 | En las pruebas, `registry.circuitBreaker("catalogo")` antes del primer `run()` crea el circuito con la configuración por defecto (el `Customizer` se aplica al crearlo con `run()`). `CatalogoHttpAdapterIntegracionTest` lo creaba en su `@BeforeEach` (corrida sola, su prueba del circuito abierto fallaba) y `ReservaApiIntegracionTest`, en su `@AfterEach`. En producción no ocurre | **Resuelto:** las tres clases lo reinician solo si existe (`find("catalogo").ifPresent(CircuitBreaker::reset)`); `CatalogoHttpAdapterIntegracionTest` pasa sola (10 de 10) |
| O13 | Comentarios que todavía citaban D5: `InventarioPort.asegurar`, `FilaInventarioNueva`, `UnidadReservable` y el mensaje de `docs/demo/reservas/concurrencia.sh` («inventario demo de 60 días») | **Resuelto:** citan la materialización (DEC-135, DEC-158) |
| O14 | WF-10 muestra «el sistema de reservas no responde» (`disponibilidadErrores.noDisponible`) también cuando el 503 viene de ms-catalog | **Resuelto en B1** (DEC-171) |
| N7 | `modelo-logico.dbml` suma `Oferta_Materializada`, pero `modelo-logico.png` no se regeneró | Pendiente de N7 (diagramas sin versionar) |

**Fuera de alcance de A2:** las pantallas (B1, B2), reconciliar las filas ya materializadas con una oferta nueva (RF19, DEC-34, DEC-130) y las notificaciones.

## Registrar servicio comercial — frontend del Prestador (iteración C, sesión B1; RF09, RF10, RF37)

Sesión B1 de la iteración C (DEC-134): las pantallas del Prestador, WF-08 «Registrar servicio comercial» y WF-18 «Mis servicios» con su detalle propio; la dirección, el RNT y la oferta en WF-06 (HC2); el mensaje del 503 que viene de ms-catalog (O14); y los documentos: el CU [«Cancelar reserva»](../casos-de-uso/CU-cancelar-reserva.md) (HC1) y el nombre de [«Registrar servicio comercial»](../casos-de-uso/CU-registrar-servicio.md) (N22). **No toca el backend.** Fuentes de los nombres: `DocumentoEntregable2.md`, figura 4 («Registrar servicio comercial», «Aprobar o rechazar servicio») y `DocumentoEntregable1.pdf`, página 9 («Gestionar Reservas»: «Cancelar reserva» «include» «Validar antelación 24h»; el Entregable 2 no trae ese diagrama).

**Decisiones de la sesión B1 (DEC-161 a DEC-173; propuestas por arquitectura y aprobadas con el plan):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-161 | Rutas y navegación | `/prestador/servicios` (WF-18), `/prestador/servicios/nuevo` (WF-08) y `/prestador/servicios/:id` (detalle propio), diferidas y dentro de `RutaProtegida rol="Prestador"`: un visitante va al login y otro rol ve «Sin permiso» sin descargar la página ni llamar a la API. El menú del Prestador suma «Mis servicios» y «Registrar servicio»; el login sigue llevando a `/`. **Justificación:** el mismo patrón de DEC-51 y DEC-92; cierra N4 para el Prestador | `app/App.tsx`, `shared/components/MenuUsuario.tsx` |
| DEC-162 | Prestador no habilitado | Se informa **solo al recibir** el 403 `PRESTADOR_NO_HABILITADO`, con un mensaje propio y sin perder lo escrito; no hay aviso previo. **Justificación:** `/me` no trae el RNT, un Prestador que no está Activo no puede iniciar sesión (RF15, D3) y el servidor es la única fuente de la regla de DEC-139 | `RegistrarServicioPage.tsx` |
| DEC-163 | Estructura de WF-08 | Una sola página con secciones en el orden del `.puml` (datos, contacto y ubicación, precio, campos de la categoría, oferta, fotos, accesibilidad, veracidad), sin pasos. El borrador guarda el detalle y la oferta de cada categoría: al volver a una categoría se recupera lo escrito, pero `construirCuerpo` solo envía la elegida. En Alojamiento, el precio «desde» es un `<output>` de solo lectura con el menor precio por noche (DEC-127). **Justificación:** WF-08 es una página; con pasos, un error del servidor quedaría en un paso oculto | `borradorServicio.ts`, `RegistrarServicioPage.tsx` |
| DEC-164 | Editores de unidades y oferta | Unidades: de 1 a 20 filas («Quitar» no está con una sola, «Agregar unidad» se detiene en 20); al quitar una fila se limpian los errores de las unidades (los índices cambian) y el foco va a la fila anterior. Oferta: 7 casillas y «Todos los días»; en Tour, de 1 a 12 salidas `HH:mm` (`<input type="time">`) que se **ordenan antes de validar y enviar**, así el índice de un error del servidor es el de la fila; un duplicado se marca `DUPLICADO`, no se borra | `componentes/EditorUnidades.tsx`, `componentes/EditorOferta.tsx` |
| DEC-165 | Pin y dirección | `SelectorUbicacion` con Leaflet directo y la base común de los mapas (`shared/mapa/mapaBase.ts`, extraída de `MapaOferta` sin cambiar su comportamiento): centro en Santa Marta, sin pin inicial; un clic pone el pin y el pin se arrastra. «Latitud» y «Longitud» siempre visibles y sincronizadas son la alternativa sin ratón, junto con «Usar el centro de Santa Marta». El cuerpo lleva `{longitud, latitud}`. Sin geocodificación (DEC-132) | `componentes/SelectorUbicacion.tsx`, `shared/mapa/mapaBase.ts` |
| DEC-166 | Validación en el cliente | Obligatorios, formatos básicos (correo con la expresión del contrato, `HH:mm`, número mayor que 0, enteros, coordenadas en rango) y longitudes de texto con contador, contadas como ms-catalog. Los rangos numéricos superiores y las demás reglas quedan **solo en el servidor**. La ruta del contrato (`detalleCategoria.unidades[1].precioNoche`) da el `id` del control, así el error del 400 queda junto a su control; un error sin control da un mensaje general. Solo se aceptan los motivos conocidos (`prestador.errores.<motivo>`); nunca se muestra un texto del servidor. **Justificación:** una sola fuente de verdad, sin duplicar los límites de DEC-138 que pueden cambiar | `rutasCampos.ts`, `borradorServicio.ts`, `componentes/CampoServicio.tsx` |
| DEC-167 | Elementos del PNG sin contrato | «Cobra por», «El precio incluye impuestos», «Fotos» y la casilla de veracidad: deshabilitados (DEC-22). La alerta con RUT, sanidad, cámara de comercio y «2 días hábiles» y la nota del correo: se omiten. «Gestor del municipio» pasa a «Administrador» (RF21). El RNT aparece como «Se toma de tu registro de prestador», **sin el valor**: ningún endpoint se lo da al Prestador antes del registro (decisión del usuario; queda como pendiente de contrato) | `RegistrarServicioPage.tsx` |
| DEC-168 | Después del 201 | Lleva al detalle propio `/prestador/servicios/{id}` con «Enviado a aprobación» y «Pendiente de aprobación», no a la lista (difiere de `WF-08-registrar-servicio.puml`:100). **Justificación:** el Prestador ve lo que guardó el servidor (precio derivado, RNT) | `DetalleMiServicioPage.tsx` |
| DEC-169 | WF-18 | `?estado=` (sin él, «Todos») y `?pagina=` desde 1 en la URL (la API recibe la página − 1); un valor no válido se normaliza con `replace` y una página fuera de rango va a la última. Tabla con nombre, categoría, estado en texto (N21), fecha de registro y acciones («Ver detalle» y, solo en los Publicados, «Ver ficha pública»); el motivo del rechazo en la fila; región `aria-live` con el total; vacío con «Registrar nuevo servicio»; 503 o red con «Reintentar». Detalle: estado, fechas de registro y revisión, motivo, datos enviados y «Editar y reenviar» deshabilitado; un 404 se muestra como no encontrado | `MisServiciosPage.tsx`, `DetalleMiServicioPage.tsx`, `criteriosMisServicios.ts`, `useMisServicios.ts` |
| DEC-170 | WF-06 | «Dirección» y «Registro RNT» muestran el valor, o «—» con «No disponible» si el servicio no lo trae: **cierra HC2**. La oferta se muestra en «Horarios de atención» («Días de operación» y, en Tour, «Salidas»); si es null, nada. La ubicación del subtítulo sigue deshabilitada (el contrato no tiene municipio) | `features/catalogo/detalle/` |
| DEC-171 | O14 | `servicioDelProblema(error)` lee `servicio` del ProblemDetail. Con `servicio: ms-catalog`, la disponibilidad de WF-10 dice que no responde el **catálogo** y el error al confirmar, que no se pudieron confirmar los datos del servicio en el catálogo; con `ms-booking` o sin `servicio`, los textos de siempre. Las tarjetas siguen con «—» (nunca nombraron el sistema de reservas) | `shared/api/httpClient.ts`, `features/reservas/useDisponibilidad.ts`, `ReservaPage.tsx` |
| DEC-172 | CU «Cancelar reserva» | Se especifica con los nombres del Entregable 1, p. 9: actor Turista, «include» «Validar antelación 24h» (RF44) y «Notificar cambios de reserva» (fuera de alcance); flujos alternos fuera de plazo (409), ya cancelada (200 idempotente), ajena (404), completada (409, también en carrera con RF52) y sin sesión (401); criterios enlazados a las pruebas existentes. No cambia código. **Cierra HC1 para RF18 y RF44** | `CU-cancelar-reserva.md` |
| DEC-173 | N22 | El CU se llama «Registrar servicio comercial» (Entregable 2, figura 4) en sus títulos y menciones de `docs/casos-de-uso/`, `docs/wireframes/`, esta trazabilidad y `CLAUDE.md`. **El archivo conserva el nombre** `CU-registrar-servicio.md`, porque lo citan comentarios del backend, que B1 no toca. «Aprobar o rechazar servicio» ya coincidía con la figura | `CU-registrar-servicio.md` y referencias |

**Mejoras de diseño (WF-08):** los campos «Latitud» y «Longitud» y el botón «Usar el centro de Santa Marta» (alternativa al pin sin ratón, DEC-165) y «Todos los días» en la oferta (DEC-164).

**Estado de los RF de la iteración:**

| RF | Estado | Qué falta |
|---|---|---|
| RF09 (registrar servicios) | **Completo en pantalla** (WF-08, WF-18) | El conteo sube al cerrar B2 (DEC-134) |
| RF10 (campos por categoría) | **Completo en pantalla** (editor de unidades y oferta) | Ídem |
| RF37 (precio de cada servicio) | **Completo en pantalla** (precio «desde» en el registro y en el detalle propio) | Ídem |
| RF21 (aprobar o rechazar servicios) | **Parcial (backend)** | B2: WF-19 |

Avance: sigue en **26 de 64**; sube a 30 al cerrar B2 (meta 39, DEC-54).

**Hallazgos y observaciones de B1:**

| # | Hallazgo | Estado |
|---|---|---|
| HC1 (parte de B1) | «Cancelar reserva» (RF18, RF44) estaba implementado sin documento de CU | **Resuelto para RF18 y RF44** con `CU-cancelar-reserva.md` (DEC-172). Sigue abierto para RF01, RF02, RF15, RF55, RF16, RF23, RF38, RF12 y RF52 (ver la fila de HC1 en «Hallazgos») |
| HC2 | RNT y dirección en WF-06 | **Cerrado** (DEC-170) |
| N4 | El Prestador y el Gestor no tenían pantallas propias: su menú solo tenía «Cerrar sesión» (DEC-123) | **Resuelto para el Prestador** (DEC-161). El Gestor sigue sin pantallas: son de la iteración de atractivos |
| N22 | El CU se llamaba «Registrar servicio»; la figura 4 del Entregable 2 dice «Registrar servicio comercial» (DEC-59) | **Resuelto** (DEC-173). Quedan las menciones de `sad/api/ms-catalog.md`, ADR-008 y los comentarios Java (ver «Pendientes») |
| O14 | El 503 de ms-catalog se atribuía al sistema de reservas | **Resuelto** (DEC-171) |
| O15 | Pruebas existentes modificadas: `sesion.test.tsx` («para otro rol no aparece «Mis reservas»»): el menú del Prestador ya no es solo «Cerrar sesión», sino «Mis servicios», «Registrar servicio» y «Cerrar sesión» (DEC-161); sigue verificando que no aparece «Mis reservas» y el foco en la primera opción. El Leaflet simulado (`test/leafletSimulado.ts`) admite eventos con datos (`latlng`) y marcadores arrastrables; el resto de las pruebas existentes no cambió (solo se sumaron casos). Conteo del frontend: de 526 en 50 archivos a 582 en 55 | Registrado |
| O16 | Hueco de contrato: el RNT del Prestador no está disponible antes de registrar un servicio (`/me` no lo trae) | Pendiente (contrato; ver «Pendientes») |
| O17 | **Recorrido visual** con Edge sin ventana (CDP), Vite contra el backend de Compose y el Prestador de la semilla: menú, WF-18 (1366 y 360 px, ES y EN), WF-08 vacío, con los errores del cliente, Tour completo con 201 real y Alojamiento con 2 unidades y un 400 real anidado (`detalleCategoria.unidades[1].capacidadMaxHuespedes: VALOR_MAXIMO`), detalle propio y WF-06 con dirección, RNT y oferta; sin errores en la consola. Defectos corregidos: bordes de la tabla de WF-18 descuadrados (la celda de acciones con `display: grid`), enlaces con forma de botón subrayados, controles desalineados en las columnas de dos campos y la tabla con desplazamiento horizontal a 360 px (ahora una tarjeta por fila, como `.tabla-cuentas`). Queda en `catalog_db` un servicio Pendiente de prueba («Recorrido visual B1 …») del Prestador de la semilla. O14 no se vio en pantalla (exige detener ms-catalog); lo cubren las pruebas | Hecho |
| N24 | `npm audit` (frontend, tras `npm ci` del `package-lock.json` existente, sin dependencias nuevas de B1) reporta **1 vulnerabilidad de severidad alta**: `undici@8.11.2` (rango vulnerable 8.0.0 a 8.12.1; corregido en 8.12.2), dependencia transitiva de `jsdom@30.1.2`, que es una dependencia de desarrollo usada solo por Vitest. No entra en el *bundle* de producción. Avisos: GHSA-4qv8-2p9j-2685, GHSA-f72v-6w45-q472, GHSA-xjj6-f33q-929c, GHSA-4w7w-5xrc-9h26, GHSA-2m5v-pch6-74hx, GHSA-qrxm-fqhp-jwvm, GHSA-9fm3-97px-rjf6 y GHSA-55wc-3pg8-jfw6 (denegación de servicio, omisión de SRI, envenenamiento de caché y validación de certificados TLS). `npm audit` indica que hay corrección disponible | Pendiente: **tarea aparte** (actualizar el lockfile y volver a correr `npm test` y `npm run build`); B1 no lo toca |

**Fuera de alcance de B1:** WF-19 (B2), editar y reenviar un servicio rechazado (iteración D), las fotos (RF48, iteración E), modificar fechas y cupos (RF19) y las notificaciones.

## Aprobar o rechazar servicio — frontend del Administrador (iteración C, sesión B2; RF21)

Sesión B2, la última de la iteración C (DEC-134). Comprende:
- la pantalla del Administrador WF-19 «Aprobar servicios» (cola y revisión);
- los componentes comunes del detalle de un servicio;
- los documentos (N22 en el SAD).

**No toca el backend.** CU [«Aprobar o rechazar servicio»](../casos-de-uso/CU-aprobar-rechazar-servicio.md): incluye «Consultar listado pendiente» y lo extiende «Justificar rechazo de servicio» (Entregable 2, figura 4).

**Decisiones de la sesión B2 (DEC-174 a DEC-182; propuestas por arquitectura y aprobadas con el plan):**

| # | Tema | Decisión | Dónde |
|---|---|---|---|
| DEC-174 | Rutas y navegación | `/admin/servicios` (cola) y `/admin/servicios/:id` (revisión), con carga diferida y dentro de `RutaProtegida rol="Administrador"`. Un visitante va al login. Otro rol (Turista, Prestador, Gestor) ve «Sin permiso» y no se llama a la API. Son **pantallas propias**, no una pestaña de WF-12: WF-19 es un wireframe aparte, con sus migas. El menú del Administrador suma «Aprobar servicios» después de «Administrar cuentas». **Justificación:** es el patrón de DEC-51 y DEC-161. Cierra N4 para el Administrador en servicios | `app/App.tsx`, `shared/components/MenuUsuario.tsx` |
| DEC-175 | Cola («Consultar listado pendiente») | Columnas de `WF-19-aprobar-servicios.puml`:16: nombre, categoría, RNT del prestador, prestador activo («No (quedará oculto aunque se apruebe)») y fecha de registro, más «Revisar». En la URL van `?estado=` y `?pagina=`. El estado vale `Pendiente` por defecto, que no se escribe, y **no hay «Todos»**, como en el `.puml`:12 y el contrato. La página empieza en 1 (la API recibe la página − 1). Un valor no válido se normaliza con `replace`, y una página fuera de rango lleva a la última (DEC-121). El estado se muestra en texto con las claves de B1 («Pendiente de aprobación», N21). El orden es el del servidor (DEC-144). La región `aria-live` está siempre presente. El vacío tiene un mensaje por estado; un error de red o un 503 muestra «Reintentar» | `features/admin/servicios/AprobarServiciosPage.tsx`, `criteriosServiciosAdmin.ts` |
| DEC-176 | Revisión | Muestra el estado y el registro. **Prestador:** id, RNT, «Prestador activo» y el estado de la cuenta según la proyección, con la insignia de WF-12, o «—» si no hay fila. **Datos enviados** por categoría: unidades, servicios incluidos, oferta, accesibilidad, horario, contacto, dirección y coordenadas. Incluye un **mapa de solo lectura**: Leaflet directo, pin fijo, atribución de OpenStreetMap y zoom máximo 19. Los nombres del prestador y del revisor no están en `catalog_db` (DEC-144): se muestran los ids, sin inventar un nombre. Para no duplicar código, el detalle propio de B1 pasa a `features/servicios/`: `DatosServicio`, `InsigniaEstadoServicio`, la forma de gestión y sus estilos. Lo usan WF-18 y WF-19, y `serviciosPrestadorApi.ts` reexporta los tipos. Va en `features/` y no en `shared/` porque depende de `catalogo` y `reservas` | `features/servicios/`, `features/admin/servicios/RevisarServicioPage.tsx` |
| DEC-177 | Decisión y «Justificar rechazo de servicio» | **Aprobar** envía `{decision: "Aprobar"}` **sin diálogo de confirmación**. El botón dice «Aprobar → Publicado» (`.puml`:44). La aprobación es **definitiva en C**: `Publicado` no tiene transición de salida (DEC-124), y despublicar no está en el SRS. Se acepta sin confirmación por coherencia con WF-12, donde «Aprobar → Activo» tampoco la pide, y porque WF-19 no la dibuja. **Rechazar** abre `DialogoRechazo`, el de RF28, ahora con título y aviso propios. La justificación es obligatoria, de 1 a 500 caracteres sin contar los espacios de los extremos. Hay contador, el foco va al campo y «Confirmar rechazo» está deshabilitado con el campo vacío. El cuerpo lleva el texto recortado. **Doble envío:** se bloquea con `aria-disabled` y una marca síncrona, así sale un solo POST. **Tras el 200:** se vuelve a la cola, que se recarga y anuncia «Se aprobó «…»: ahora está Publicado» o «Se rechazó «…»: ahora está Rechazado». El foco va al título de resultados | `RevisarServicioPage.tsx`, `componentes/DialogoRechazo.tsx` |
| DEC-178 | Conflictos y errores | **409 `ESTADO_CAMBIADO`:** vuelve a la cola, que se recarga y avisa «ya fue revisado por otro administrador (ahora está {estadoActual})» (DEC-52). **404 `SERVICIO_NO_ENCONTRADO`**, al abrir o al decidir: vuelve a la cola con el aviso. **400 `campos.justificacion`:** el error va junto al campo y el diálogo sigue abierto. **401:** el cliente ya cerró la sesión, y el login dice por qué. **403 `ACCESO_DENEGADO`:** «Sin permiso», sin cerrar la sesión. **Red o 503 de ms-catalog:** mensaje en la revisión o en el diálogo («El catálogo no está disponible…»), sin salir de la pantalla | `RevisarServicioPage.tsx` |
| DEC-179 | Estados finales | Un servicio `Publicado` o `Rechazado` **no muestra** las acciones de decisión, porque no aplican (DEC-124). DEC-22 es para lo que está fuera de alcance, y esto no lo está; WF-12 hace lo mismo y muestra «Aprobar» solo a un Pendiente. En su lugar aparece la sección «Revisión»: fecha, revisor (id) y, si se rechazó, el motivo | `RevisarServicioPage.tsx` |
| DEC-180 | Prestador no activo | Con `prestadorActivo = false` aparece un aviso que no bloquea: «si apruebas el servicio, quedará Publicado pero oculto en el catálogo público». Aprobar sigue disponible. **Justificación:** la regla está en el CU (FA6) y en DEC-145, así que no se pregunta | `RevisarServicioPage.tsx` |
| DEC-181 | Diferencias con WF-19 | Registradas en `wireframes/README.md`; el `.puml` no cambia | `docs/wireframes/README.md` |
| DEC-182 | N22 en el SAD | «Registrar servicio comercial» en `sad/api/ms-catalog.md` (enlaces y título de la sección) y en la enmienda de la iteración C de ADR-008. También se corrige la cita del título de la sección en `CU-registrar-servicio.md`. Quedan los comentarios Java (ver «Pendientes») | `docs/sad/`, `docs/casos-de-uso/CU-registrar-servicio.md` |

**Mejoras de diseño (WF-19):**
- el foco va al título de la revisión cuando carga y al título de resultados al volver a la cola;
- las coordenadas aparecen también en texto, junto al mapa;
- «Volver» conserva el filtro y la página de la cola desde la que se abrió la revisión.

**Estado de los RF de la iteración:**

| RF | Estado | Qué falta |
|---|---|---|
| RF09 (registrar servicios) | **Implementado** (backend en A1; WF-08 y WF-18 en B1) | — |
| RF10 (campos por categoría) | **Implementado** (unidades y oferta) | — |
| RF37 (precio de cada servicio) | **Implementado** (precio «desde») | — |
| RF21 (aprobar o rechazar servicios) | **Implementado** (backend en A1; WF-19 en B2) | — |

Avance: de **26 a 30 de 64** (meta 39, DEC-54). La iteración C queda cerrada.

**Hallazgos y observaciones de B2:**

| # | Hallazgo | Estado |
|---|---|---|
| N4 | El Gestor y el Administrador no tenían pantallas para los servicios | **Resuelto para el Administrador** (DEC-174). El Gestor sigue sin pantallas (iteración de atractivos) |
| N22 | Menciones de «Registrar servicio» en el SAD | **Resuelto en `docs/`** (DEC-182). Quedan los comentarios Java (ver «Pendientes») |
| HC1 | RF sin CU | **Sin cambios.** B2 no encontró otro RF sin CU. Sigue abierto para RF01, RF02, RF15, RF55, RF16, RF23, RF38, RF12 y RF52 |
| O18 | Credenciales en los recorridos: el inicio de sesión con usuarios de la semilla se hace **solo en el navegador**. Nunca con curl ni con otro comando que deje la contraseña en la línea de comandos, el historial, los registros o el cierre. Sin navegador, la comprobación por HTTP se limita a la SPA y a las rutas, sin iniciar sesión | Regla del equipo (B2) |
| O19 | Defecto de B1: el detalle propio (WF-18) llamaba a `condicionesAccesibilidad.map` sin guarda y fallaba si llegaba `null`. **Causa verificada:** con el contrato actual no ocurre. ms-catalog convierte un valor ausente en una lista vacía (`ServicioComercial.java`:75) y siempre serializa una lista (`RespuestaServicio.accesibilidad`). La guarda (`?? []`) queda como defensa ante un dato fuera de contrato, y el tipo lo admite | **Resuelto** en `DatosServicio` (común a WF-18 y WF-19), con pruebas en los dos detalles |
| O20 | Pruebas existentes modificadas:<br>- `sesion.test.tsx`: el menú del Administrador suma «Aprobar servicios» entre «Administrar cuentas» y «Preguntas frecuentes», y se verifican los cuatro `href`;<br>- `misServicios.test.tsx`: un caso nuevo para O19; los demás no cambian;<br>- `reserva.test.tsx`: la última aserción de «al mover la entrada después de la salida…» va dentro de `waitFor` (O22).<br>`DialogoRechazo` admite título y aviso opcionales, con los valores de antes por defecto, así que las pruebas de WF-12 no cambian. Conteo del frontend: de 582 pruebas en 55 archivos a 634 en 59. ms-booking no cambia: 205 | Registrado |
| O21 | **Recorrido visual de WF-19** con Edge **con ventana**, manejado por CDP desde el *stack* de Compose. El Administrador de la semilla inició sesión **él mismo en la ventana** (O18): la contraseña no pasó por la terminal. Se recorrieron:<br>- el menú del avatar («Aprobar servicios» entre «Administrar cuentas» y «Preguntas frecuentes»);<br>- la cola (1366 px; 360 px en inglés: una tarjeta por fila, sin desplazamiento horizontal) y el filtro «Rechazado»;<br>- la revisión de un Tour (mapa con el pin y la atribución, prestador, datos enviados), una Gastronomía en inglés y un Tour a 360 px;<br>- el diálogo de rechazo (foco en el campo, contador, «Confirmar rechazo» deshabilitado con el campo vacío);<br>- el estado final: solo «Volver», con la sección «Revisión».<br>**Defectos corregidos** en `admin.css`: el `select` del filtro sin estilo y a todo el ancho; los enlaces «Revisar» con el azul del navegador; «Volver» (enlace con forma de botón) subrayado.<br>**Datos cambiados en `catalog_db`:** el servicio «Recorrido visual B1 02:45:59» (`6ae404e86a17b2ffd9239dd3`) pasó a **Rechazado**, con la justificación «Servicio de prueba del recorrido visual de B1; no corresponde a una oferta real.». No se creó «Recorrido visual B2 …».<br>Dato de la semilla, no de la pantalla: el pin de «Cocina Caribe El Rodadero» cae en el mar (M009) | Hecho (salvo el servicio B2, ver «Pendientes») |
| O22 | Pruebas intermitentes con la suite cargada:<br>- `reserva.test.tsx` › «WF-10, alojamiento: unidades, cupos y resumen» › «al mover la entrada después de la salida, la salida se ajusta y se anuncia». **Causa encontrada**, reproducida con dos suites en paralelo: el botón describía «Verifica la disponibilidad para continuar.» en lugar de «Cambiaste las fechas…». La prueba esperaba solo el campo «Entrada», que se pinta antes de que llegue la disponibilidad inicial, y hasta entonces el motivo es «verificar» (`ReservaPage.tsx`:191). Es una carrera de la prueba, no del producto. **Corregida:** la última aserción va dentro de `waitFor`;<br>- `registrarServicio.test.tsx` › «los elementos de WF-08 sin contrato aparecen deshabilitados (DEC-167) y el RNT no muestra un valor»: falló una vez en `npm test` completo, justo después de reconstruir el contenedor del frontend. **Sin causa conocida:** no se guardó el error y no se reprodujo en 6 corridas completas, cada una con otra suite en paralelo.<br>Después de la corrección, las 6 corridas con carga y la corrida final pasan (634 de 634 en 59 archivos) | WF-10: **resuelto**. WF-08: registrado; si vuelve a fallar, guardar el error (la salida completa de `npm test`) y corregirla |

**Fuera de alcance de B2:**
- editar y reenviar un servicio rechazado (iteración D);
- despublicar (no está en el SRS);
- notificar al prestador (RF13, RF30);
- el nombre del prestador y el del revisor, que viven en auth_db (DEC-144).
