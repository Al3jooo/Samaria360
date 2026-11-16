# Modelo de datos NoSQL (MongoDB)

Validadores de `catalog_db`, `analytics_db` y `chatbot_db` en su **última versión**. Parten de la transcripción fiel del **Documento Entregable 2** (sección "Diagramas de Base de Datos NoSQL", versión del 10/10/2026), con los cambios posteriores que se listan abajo.

**Dónde vive cada versión.** La versión del Entregable 2 (v1) está congelada en `infra/mongo/init/servicios.v1.schema.json` y `atractivos.v1.schema.json`, que usan los scripts de inicialización (línea base). Cada cambio posterior va en una migración de `infra/mongo/migraciones` con **su propia copia** del validador (p. ej. `esquema/M001__servicios.v2.schema.json`); las migraciones nunca leen estos archivos. Las pruebas de ms-catalog (`MigracionesMongoTest`) verifican que, después de aplicar todas las migraciones, el validador de la base sea igual al de esta carpeta.

| Archivo | Colección | Base de datos | Microservicio |
|---|---|---|---|
| `servicios.schema.json` | `servicios` | `catalog_db` | ms-catalog |
| `atractivos.schema.json` | `atractivos` | `catalog_db` | ms-catalog |
| `cuentas.schema.json` | `cuentas` | `catalog_db` | ms-catalog |
| `faq.schema.json` | `faq` | `catalog_db` | ms-catalog |
| `avisos.schema.json` | `avisos` | `catalog_db` | ms-catalog |
| `favoritos.schema.json` | `favoritos` | `catalog_db` | ms-catalog |
| `historial_busqueda.schema.json` | `historial_busqueda` | `catalog_db` | ms-catalog |
| `eventos.schema.json` | `eventos` | `catalog_db` | ms-catalog |
| `outbox_eventos.schema.json` | `outbox_eventos` | `catalog_db` | ms-catalog |
| `ocupaciones.schema.json` | `ocupaciones` | `catalog_db` | ms-catalog |
| `historial_modificaciones.schema.json` | `historial_modificaciones` | `catalog_db` | ms-catalog |
| `analytics_db/cuentas.schema.json` | `cuentas` | `analytics_db` | ms-analytics |
| `analytics_db/reservas.schema.json` | `reservas` | `analytics_db` | ms-analytics |
| `analytics_db/logs_auditoria.schema.json` | `logs_auditoria` | `analytics_db` | ms-analytics |
| `analytics_db/notificaciones.schema.json` | `notificaciones` | `analytics_db` | ms-analytics |
| `analytics_db/correos_pendientes.schema.json` | `correos_pendientes` | `analytics_db` | ms-analytics |
| `analytics_db/eventos_procesados.schema.json` | `eventos_procesados` | `analytics_db` | ms-analytics |
| `analytics_db/calificaciones.schema.json` | `calificaciones` | `analytics_db` | ms-analytics |
| `analytics_db/promedios.schema.json` | `promedios` | `analytics_db` | ms-analytics |
| `analytics_db/reportes.schema.json` | `reportes` | `analytics_db` | ms-analytics |
| `analytics_db/outbox_eventos.schema.json` | `outbox_eventos` | `analytics_db` | ms-analytics |
| `analytics_db/ocupaciones.schema.json` | `ocupaciones` | `analytics_db` | ms-analytics |
| `analytics_db/consultas.schema.json` | `consultas` | `analytics_db` | ms-analytics |
| `chatbot_db/cuentas.schema.json` | `cuentas` | `chatbot_db` | ms-chatbot |
| `chatbot_db/uso_diario.schema.json` | `uso_diario` | `chatbot_db` | ms-chatbot |
| `chatbot_db/itinerarios_sugeridos.schema.json` | `itinerarios_sugeridos` | `chatbot_db` | ms-chatbot |
| `chatbot_db/base_conocimiento_turistico.schema.json` | `base_conocimiento_turistico` | `chatbot_db` | ms-chatbot |

Cada archivo contiene exactamente el objeto que va en `validator` de `db.createCollection` o de `collMod`. `servicios.schema.json` tiene dos claves, `$jsonSchema` y `$expr`: MongoDB exige las dos (un AND implícito).

Con el plan del 100 % (DEC-278) ya están todas las colecciones del Entregable 2: `itinerarios_sugeridos` y `base_conocimiento_turistico` en `chatbot_db` (ms-chatbot, C001), y `calificaciones`, `reportes`, `logs_auditoria` y `notificaciones` en `analytics_db` (ms-analytics, A001 y A002). Cada base tiene sus migraciones, con su propia copia de cada validador: `infra/mongo/migraciones/` (catalog_db, M…), `infra/mongo/analytics/` (A…) e `infra/mongo/chatbot/` (C…). Las aplica `mongo-migraciones`, cada una con el usuario de su servicio (DEC-275).

## Notas para la implementación

- `bsonType: "int"` exige enteros de 32 bits: en mongosh, `NumberInt(10)`; en Java, `Integer`.
- `bsonType: "decimal"` exige Decimal128: en mongosh, `NumberDecimal("120000")`; en Java, `BigDecimal` con `@Field(targetType = FieldType.DECIMAL128)`.
- Las coordenadas van en orden **[longitud, latitud]** y requieren un índice `2dsphere`, que no forma parte del validador.
- El `oneOf` de `servicios` discrimina por `categoria`: cada rama fija un único valor y exige su `detalleCategoria`.
- **Cambio posterior al Entregable 2:** `detalleCategoria.serviciosIncluidos` (Alojamiento) admite solo `WiFi`, `Desayuno`, `Piscina` y `Parqueadero` (`enum`), el vocabulario cerrado de RF10 y WF-08 que usa «Más filtros» (WF-05).
- **Cambio posterior al Entregable 2 — esquema v2 de `servicios` (migración M001, D1 de «Reservar servicio»):** un alojamiento tiene `detalleCategoria.unidades`, al menos una, en lugar de `tipoHabitacion`, `numHabitaciones` y `capacidadMaxHuespedes`. Cada unidad: `idUnidad` (`objectId`, estable aunque se renombre la unidad), `nombre` (1 a 100 caracteres), `tipoHabitacion`, `numHabitaciones` y `capacidadMaxHuespedes` (enteros ≥ 1) y `precioNoche` (decimal > 0). `serviciosIncluidos` sigue siendo del alojamiento. En Alojamiento `precioBase` es obligatorio y es el precio «desde» (RF37): el menor `precioNoche`. `$jsonSchema` no puede exigir que `idUnidad` sea única dentro del arreglo (`uniqueItems` compara la unidad completa) ni que `precioBase` sea el mínimo; lo hace la condición `$expr`.
- **Colección `faq` (M005; RF26, RF47; DEC-57, DEC-61):** es la del Entregable 2, con `titulo` y `cuerpo`, **más estricta**:
  - `fechaActualizacion` es obligatoria: la escribe siempre el servidor (en el Entregable 2 era opcional);
  - `additionalProperties: false`;
  - límites de 1 a 150 (`titulo`) y de 1 a 2000 (`cuerpo`) caracteres, más un `pattern` que exige el texto sin espacios en los extremos (`^\S([\s\S]*\S)?\z`; con `$`, PCRE aceptaría un salto de línea final). El dominio recorta con `strip()` y nunca guarda lo que el `pattern` rechaza. El `\s` de MongoDB solo reconoce los espacios ASCII (espacio, `\t`, `\n`, `\v`, `\f`, `\r`): los espacios Unicode (U+00A0, U+2003…) cuentan como `\S`.
  - `maxLength` cuenta puntos de código, igual que el dominio.
  - Índice `faq_titulo` con colación `es`, nivel 1 (orden alfabético, DEC-63).
- **Colección `avisos` (M005; RF40; DEC-57, DEC-58), ampliación del modelo del Entregable 2:**
  - `mensaje` (1 a 300 caracteres, mismo `pattern`), `tipo` (`Clima`, `Cierre`, `Comunicado`), `activo` (bool) y `fechaPublicacion` (date), todos obligatorios;
  - sin vigencia automática;
  - índices `avisos_activos` (banner) y `avisos_fecha` (listado del Administrador).
  - Falta llevarla al Documento Entregable 2.
- **Colección `favoritos` (M007; RF54; DEC-80, DEC-86):** los campos del Entregable 2 (§2.4) más una ampliación propia:
  - `turistaId` (UUID en minúsculas, el claim `sub`), `entidadId` (el ObjectId del servicio o atractivo, **en texto**, 24 hex) y `tipoEntidad` (`servicio` o `atractivo`); la API usa `Servicio` y `Atractivo` y el adaptador convierte;
  - **ampliación:** `fechaCreacion` (date), obligatoria, para el orden de «Mis favoritos»;
  - `additionalProperties: false`: no se desnormaliza el nombre ni la imagen (se resuelven al listar con un `$in`, que convierte `entidadId` a ObjectId);
  - índices `favoritos_turista_entidad` (único: un favorito por turista, tipo y entidad) y `favoritos_turista_fecha`.
- **Colección `historial_busqueda` (M007; RF60; DEC-56, DEC-88):** los campos del Entregable 2 (§2.4) más una ampliación propia:
  - `turistaId`, `criteriosBusqueda` (string: el JSON canónico de los criterios normalizados de `GET /oferta`, sin coordenadas ni página; máximo 2000) y `fecha` (date, la última vez que se hizo);
  - **ampliación:** `clave` (string, máximo 2000), la forma canónica de los criterios, con índice único `(turistaId, clave)` para la deduplicación atómica;
  - índice `historial_turista_fecha` para las 10 más recientes y la poda.
  - Las dos ampliaciones faltan en el Documento Entregable 2 (punto 9 de «Pendientes para el SAD (modelo NoSQL)» en `docs/trazabilidad.md`).
- **Esquema v3 de `servicios` (M008; iteración C «Registrar y aprobar servicio»; RF09, RF10, RF37, RF21; DEC-126 a DEC-136, DEC-148):**
  - `fechaRegistro` (date) **obligatoria**: la escribe el servidor al registrar (`RelojPort`); los servicios anteriores la toman del `_id` (M008);
  - `precioBase` **obligatorio en las cuatro categorías** y mayor que 0 (DEC-127; antes, solo en Alojamiento);
  - `oferta` (DEC-136): `{diasOperacion, salidas}`, obligatoria en Tour (días y salidas `HH:mm`) y en Alojamiento (solo días), y prohibida en Gastronomía y Alquiler (`not required`). `diasOperacion`: de 1 a 7 de `Lunes`…`Domingo`, sin tildes y sin repetir; `salidas`: de 1 a 12, sin repetir. M008 rellena la de los servicios existentes con lo que ya ofrecía el generador D5 de ms-booking (todos los días; tours a las 08:00 y 14:00);
  - **ampliaciones** opcionales en el validador y obligatorias en el dominio para los registros nuevos: `direccion` (1 a 200), `rnt` (1 a 50, copia del RNT del prestador, HC2), `motivoRechazo` (1 a 500), `revisadoPor` (UUID) y `fechaRevision` (date). **Justificación:** la semilla de `init/` existe aunque `MONGO_SEMILLA=false`; exigir campos que no se pueden derivar dejaría documentos inválidos y rompería el `updateMulti` de RF22. La semilla los recibe en M009;
  - el `$expr` suma una condición: `motivoRechazo` existe si y solo si `estadoPublicacion` es `Rechazado`;
  - más estricto que el v2: `nombreComercial` y `descripcion` con mínimo 1 (y `descripcion` hasta 2000), `aforoMaxTurno`, `aforoMaxGrupo` y `unidadesDisponibles` ≥ 1, `duracionHoras` > 0 y `condicionesAccesibilidad` con el `enum` de RF27 y `uniqueItems` (resuelve el punto 3 de «Pendientes para el SAD (modelo NoSQL)»);
  - índices `admin_estado_fecha` (cola del Administrador, del más antiguo al más reciente) y `prestador_fecha` («Mis servicios»).
  - Falta llevarlo al Documento Entregable 2.
- **Colección `cuentas` v2 (M008; DEC-146):** suma `rnt` (string de 1 a 50), opcional: solo lo tienen las cuentas de rol Prestador, y falta en las que todavía no recibieron un evento posterior a V17 de ms-auth.
- **Esquema v2 de `atractivos` (M012; iteración E «Atractivos y agenda», sesión A1; RF06, RF07, RF24, RF27, RF33, RF58; DEC-207, DEC-208, DEC-214):**
  - `version` (int ≥ 0) y `fechaRegistro` (date) **obligatorias**: M012 las rellena en los atractivos existentes (0 y la fecha del `_id`) antes de instalar el validador;
  - **ampliaciones:** `horarioAtencion` (1 a 500, RF58; cierra HC4) y `fechaActualizacion` (date), opcionales;
  - más estricto que el v1: `gestorId` con forma de UUID; `nombre` de 1 a 120; `descripcion` y `normasAmbientales` de 1 a 1000; `categoria` con `enum` `Naturaleza` o `Cultura` (**decisión del equipo, no del SRS**, DEC-214; en el v1 era texto libre); `limiteCapacidadCarga` de 1 a 100 000; `imagenes` hasta 5; `condicionesAccesibilidad` con el `enum` de RF27 y `uniqueItems`;
  - `descripcion`, `categoria`, `estadoOperativo` e `imagenes` siguen opcionales en el validador y son obligatorios en el dominio para los registros nuevos (como DEC-148);
  - M012 **no normaliza datos**: un atractivo que no cumple el v2 la detiene con la lista de `_id`;
  - `imagenes[]` guarda rutas públicas: `/api/v1/catalogo/imagenes/{id}` (GridFS, bucket `imagenes`, [ADR-009](../adr/ADR-009-imagenes-gridfs.md)) o, en la semilla, `/imagenes/catalogo/*.webp`.
  - Falta llevarlo al Documento Entregable 2 (punto 12 de «Pendientes para el SAD (modelo NoSQL)» en `docs/trazabilidad.md`).
  - **Desviación del Entregable 2 (DEC-240):** el §2.4 define `categoria` como texto libre; desde el v2 es un `enum`.
- **Colección `eventos` (M013; iteración E, sesión A2; RF25; DEC-215, DEC-235 a DEC-242):** parte de DocumentoEntregable2.md, §2.4 (documento del Proyecto, fuera del repositorio). Diferencias con el §2.4:
  - `ubicacion` y `descripcion` **obligatorias** (DEC-235: RF25 captura los cinco campos);
  - **ampliación:** `fechaRegistro` (date) obligatoria, para el panel del Gestor (DEC-238);
  - `additionalProperties: false` y `gestorId` con forma de UUID;
  - `nombre` de 1 a 120, `ubicacion` de 1 a 200 y `descripcion` de 1 a 1000, con el `pattern` de texto sin espacios en los extremos (DEC-236);
  - `$expr`: `fechaInicio <= fechaFin` (DEC-237). «No anterior a hoy» (DEC-242) es solo del dominio: el validador no depende de la fecha actual;
  - `fechaInicio` y `fechaFin` son días a las 00:00 de America/Bogota (DEC-215): la convención la garantiza el dominio, no el validador;
  - sin `version`: los eventos no se editan (DEC-241);
  - índices `eventos_agenda` (`{fechaInicio: 1, _id: 1, fechaFin: 1}`) y `eventos_registro` (`{fechaRegistro: -1, _id: -1}`).
  - Falta llevarlo al Documento Entregable 2 (punto 13 de «Pendientes para el SAD (modelo NoSQL)» en `docs/trazabilidad.md`).

## Plan del 100 % (iteraciones F a K e I)

### catalog_db

- **Colección `outbox_eventos` (M014; iteración G; DEC-293):** los eventos de ms-catalog para RabbitMQ (ADR-011), escritos en la misma transacción que el cambio. Obligatorios: `idPublico` (UUID del evento), `tipo`, `clave` (la de enrutamiento), `datos`, `creadoEn`, `intentos` y `proximoIntento`; `publicadoEn` y `ultimoError` los escribe el relevo. Índices `outbox_pendientes` (`{publicadoEn, proximoIntento, _id}`) y `outbox_id_publico` (único). **Ampliación propia:** no está en el Entregable 2.
- **Esquema v5 de `servicios` y v3 de `atractivos` (M015; iteración H; DEC-302):** `calificacion {promedio, cantidad, version}`, opcional, copiada del evento `PromedioActualizado` de ms-analytics. `promedio` es `double` de 1 a 5, o `null` sin calificaciones visibles; `cantidad` ≥ 0; `version` es la del promedio en ms-analytics. El UPDATE solo avanza con una versión mayor y no toca la `version` de la edición. Índice `servicios_calificacion` (`{"calificacion.promedio": -1, _id: 1}`) para `orden=Calificacion` (RF61). **Desviación del Entregable 2:** allí el promedio vivía solo en el servicio de análisis; se copia para ordenar sin consultar otro servicio (decisión 6).
- **Esquema v4 de `atractivos` y colección `ocupaciones` (M016; iteración J; DEC-304):** `atractivos.ocupacionActual {valor, fecha}` (0 a 1 000 000) es la última lectura del Gestor (RF39) y no sube `version`. Índice `atractivos_categoria` para los alternativos (RF51). `ocupaciones` guarda cada lectura: `atractivoId`, `ocupacion`, `limite` (la capacidad de carga en ese momento), `fecha` y `gestorId`; índice `ocupaciones_atractivo` (`{atractivoId, fecha: -1}`). **Ampliación propia.**
- **Colección `historial_modificaciones` (M017; iteración I; DEC-311):** RF41, últimos 30 días por prestador. Obligatorios: `servicioId`, `prestadorId`, `tipo`, `valorNuevo` y `fecha`; opcionales: `nombreServicio`, `detalle`, `valorAnterior` e `idEvento` (el del `OfertaModificada` de ms-booking). Índices `historial_prestador` (`{prestadorId, fecha: -1, _id: -1}`) e `historial_id_evento` (único y parcial: deduplica los eventos; los cambios de precio de RF32 no lo llevan). **Ampliación propia.**

- **Corrección de datos de semilla (M018; QA-08; DEC-333):** [`M018__horario-tour-minca.js`](../../../infra/mongo/migraciones/semilla/M018__horario-tour-minca.js) corrige `servicios.horarioAtencion` del Tour de Café en Minca de «1:00 p. m.» a «2:00 p. m.», conforme a la oferta reservable de 08:00 y 14:00 que estableció M008. Solo se aplica con semilla: exige el prestador original, el nombre y la categoría Tour, el texto anterior exacto y las salidas `["08:00", "14:00"]`. Incrementa `version` con `NumberInt(1)`; es idempotente y conserva datos personalizados que no coincidan con el filtro. No modifica la oferta, el inventario de reservas, los validadores ni migraciones anteriores. `MigracionesMongoTest` verifica el horario y las salidas en volúmenes nuevos y existentes. La próxima migración de `catalog_db` es **M019**.

### analytics_db (ms-analytics; A001 a A006; DEC-292, DEC-300, DEC-304, DEC-308, DEC-322, DEC-323)

Todas con `additionalProperties: false`. ms-analytics no lee bases ajenas: sus proyecciones salen de los eventos de RabbitMQ (decisión 6).

- **A001 (iteración G):**
  - `cuentas`: proyección de `CuentaActualizada` (`rol`, `estadoCuenta`, `version` y el `pais` del turista), para el validador de `ver` y los turistas por país de RF14;
  - `reservas`: proyección de los eventos de reservas (`idTurista`, `idPrestador`, `idServicio`, `estado`, `actualizadaEn`, más `confirmadaEn` y los datos del servicio);
  - `logs_auditoria` (RF13): `usuarioId`, `accion`, `fecha`, `idEvento` (único) y `detalle` sin texto libre;
  - `notificaciones` (RF63): `usuarioId`, `tipo`, `mensaje`, `leida`, `fecha` e `idEvento`;
  - `correos_pendientes` (RF30): el correo al Prestador, con `intentos`, `proximoIntento` y `enviadoEn`;
  - `eventos_procesados`: idempotencia; el `_id` es el id del evento y se purga por `procesadoEn`.
- **A002 (iteración H):**
  - `calificaciones` (RF17): una por turista y entidad (índice único); `estrellas` de 1 a 5, `comentario` de 0 a 500 y `estado` con el motivo de la moderación (RF29);
  - `promedios`: el promedio visible y su `version`;
  - `reportes` (RF57): `motivo` de la lista del equipo, `descripcion` y `estado` (Pendiente o Revisado);
  - `outbox_eventos`: `PromedioActualizado`, con la misma forma que la de catalog_db.
- **A003 (iteración J):** `ocupaciones`, copia de `OcupacionRegistrada` con `porcentaje` y `nivel` calculados al recibirla, para RF64. Índice único por `idEvento`.
- **A004 (iteración K):** `consultas`, por ficha y día de Bogotá (`tipoEntidad`, `idEntidad`, `dia` y `consultas`, con `nombre` e `idPrestador`), para RF14 y RF31. Índices `consultas_tipo_dia` y `consultas_prestador_dia`.
- **A005 (revisión previa al redespliegue, DEC-322):** validador v2 de `logs_auditoria` con el campo opcional `datos` (los valores del detalle por separado: identificadores, `nombreServicio`, los roles y el `motivo` de RF29), para traducir el detalle en la interfaz. No modifica los datos: las entradas anteriores conservan solo `detalle`.
- **A006 (DEC-323):** validador v2 de `reservas`: `nombreServicio` admite hasta 150 caracteres, el largo del nombre comercial en ms-catalog (con 120, la reserva de un servicio con un nombre más largo iba a la `.dlq`). No modifica los datos.
- **Diferencias con el Entregable 2:** `calificaciones`, `reportes`, `logs_auditoria` y `notificaciones` parten del §2.4, con límites, enums e `idEvento` (idempotencia). Las demás son **ampliaciones propias** que exige la mensajería: proyecciones, outbox, correos pendientes e idempotencia.

### chatbot_db (ms-chatbot; C001; DEC-297)

- `base_conocimiento_turistico` (RF05: `contenido`, `categoria` y `fechaActualizacion`; hasta 500 entradas) e `itinerarios_sugeridos` (RF35: `turistaId`, `nombre`, `items`, `fechaCreacion` y `fechaActualizacion`; hasta 20 por turista) parten del §2.4, con límites y `additionalProperties: false`.
- **Ampliaciones propias:** `uso_diario` (RF59 y decisión 5: `turistaId`, `dia` de Bogotá y `mensajes`; índice único `uso_turista_dia`; 20 por día) y `cuentas` (proyección de `CuentaActualizada` para el validador de `ver`).
- No hay historial de conversación en el servidor, y el contexto que recibe el modelo no lleva datos personales (DEC-297).
