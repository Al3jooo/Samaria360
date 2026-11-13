# Decisiones de «Registrarse» y «Reservar servicio» (antes del plan del 60 %)

Parte de la [matriz de trazabilidad](../trazabilidad.md): decisiones (DEC), hallazgos y verificación de cada sesión, separadas del archivo principal en la revisión del 2026-11-13. Los números DEC son únicos en todo el proyecto.

## Registrarse — decisiones (RF01, RF02)

Inconsistencias entre el SRS, los wireframes y el modelo, decididas por el equipo antes de implementar el CU Registrarse. La respuesta al correo duplicado está en [ADR-006](../sad/adr/ADR-006-registro-sin-enumeracion.md).

| # | Tema | Decisión | Migración |
|---|---|---|---|
| D1 | Categorías de interés | RF01 dice «tres categorías de interés» y RF53 «actualizar sus tres categorías», pero WF-02 muestra tres casillas con dos marcadas. **Interpretación:** las tres son el **vocabulario cerrado** (Naturaleza, Cultura, Aventura) y el turista elige **al menos una**, sin repetir; repetidas son un error (400 `DUPLICADO`), no se unifican. Se valida con un enum en el dominio (`CategoriaInteres`) y con un CHECK en la base. | V4 |
| D2 | Razón social y correo del prestador | RF02 y WF-03 no piden razón social, que el modelo tenía obligatoria: pasa a ser **opcional** y el formulario queda como WF-03. **Un solo correo** en el registro, con dos significados: `usuario.correo` es la **credencial de acceso** (normalizada, única, la usa el login); `prestador.correo_corporativo` es el **contacto público** (RF43). En el registro (RF02) las dos columnas se llenan con el mismo valor normalizado; cambiar el correo de contacto (RF43) **no** cambia el de acceso. El nombre comercial **no** va en `prestador`: según RF09 es del servicio (ms-catalog). Verificado: `nombre_representante_legal` y `telefono` existen en V1 y son obligatorias. | V5 |
| D3 | Prestador pendiente o rechazado en el login | **Sustituye a la decisión anterior** («un prestador Pendiente puede iniciar sesión; la validación solo afecta el registro de servicios, RF09»), cuya prueba `unPrestadorPendienteDeValidacionPuedeIniciarSesion` se reemplazó a propósito. **Por qué:** RF02 dice que la cuenta queda «Pendiente de Validación hasta que el Administrador verifique sus credenciales», y RF28 habla de cambiar el estado de la cuenta a Activo o Rechazado. Pendiente → 403 `CUENTA_PENDIENTE_VALIDACION`; Rechazado → 403 `CUENTA_RECHAZADA`. Se evalúa **después** de la contraseña: sin ella nadie averigua si un correo es de un prestador pendiente. Contraseña incorrecta: un intento fallido más, como siempre; contraseña correcta con cuenta Pendiente o Rechazada: no es intento fallido, el conteo vuelve a 0 y no se registra un acceso (el acceso solo se registra al emitir el token). `estado_validacion` y `estado_cuenta` (RF22) son estados distintos y no se mezclan: la cuenta Inactiva también se evalúa después de la contraseña (ajuste aprobado), y antes de ella solo queda el bloqueo (423). | V6 (semilla: `prestador@` pasa a validado, porque tiene servicios publicados y según RF09 y RF21 eso exige estar Activo) |
| D4 | Correo ya registrado | Respuesta **uniforme** (202, mismo cuerpo y encabezados), BCrypt siempre, sin inicio de sesión automático; aviso al dueño por `NotificadorCuentaPort`; RNT duplicado → 409 `RNT_YA_REGISTRADO` (dato público). Ver ADR-006. | — |
| D5 | País de origen | RF01 «país de origen» se guarda como **código ISO 3166-1 alfa-2** (p. ej. «CO») en `turista.pais_origen VARCHAR(2)` con CHECK de formato. Una sola fuente: el dominio (`CodigoPais`, con `Locale.getISOCountries` del JDK) y `GET /api/v1/auth/paises`; el frontend solo pone los nombres con `Intl.DisplayNames` y no tiene listas de países. | V7 (semilla: «Colombia» → «CO»), V8 |
| D6 | Aceptación de políticas del prestador | WF-03 tiene la casilla, pero `prestador` no tenía columna. RF02 no lo menciona: es un **requisito derivado** de la restricción normativa de E1 (§3.2.7.4) y de WF-03, sin número de RF. Se agrega `prestador.acepto_politicas` NOT NULL **sin valor por defecto**; las filas de la semilla quedan en false, que es lo cierto. Misma regla y mismo código (`POLITICAS_NO_ACEPTADAS`) para turista y prestador. No interviene en el login. **Fecha de aceptación:** no hay columna propia; la aceptación es obligatoria y se guarda en la misma transacción que crea el usuario, así que `usuario.fecha_registro` registra cuándo se aceptó. Esa columna es DATE: registra el día, no la hora. | V9 |

**Otros hallazgos del CU:**
- **Catálogo de roles (V10).** Solo lo cargaba la semilla V2; sin semilla la tabla `rol` quedaba vacía y el registro fallaría por `fk_usuario_rol`. V10 inserta los cuatro roles con `ON CONFLICT DO NOTHING` y **los mismos id que V2** (1 Turista, 2 Prestador, 3 Gestor, 4 Administrador). El registro busca el rol **por nombre** (`RolJpaRepository.findByNombre`, nombre único), así que no depende del id.
- **«Aventura» no tiene equivalente en el catálogo.** Los atractivos usan Naturaleza y Cultura, y los servicios Alojamiento, Gastronomía, Tour y AlquilerEquipo. Importará cuando el chatbot (RF04) o RF51 usen las preferencias del turista para recomendar.
- **Correo normalizado.** Sin espacios y en minúsculas, igual en el registro y en el login: «Ana@X.com» y «ana@x.com» son la misma cuenta.

## Reservar servicio — decisiones (RF11, RF18, RF20, RF32, RF37, RF44)

Decisiones del equipo para el CU «Reservar servicio» (ms-booking, `reservas_db`; arquitectura en [ADR-007](../sad/adr/ADR-007-reservas-y-catalogo.md); contrato en [`sad/api/ms-booking.md`](../sad/api/ms-booking.md)). **Nombres de los CU (DEC-160):** «Reservar servicio» es el nombre histórico de estas decisiones; en el modelo de casos de uso del Entregable 1 son «Realizar reserva» (con el incluido «Verificar disponibilidad»), especificado en [`CU-realizar-reserva.md`](../casos-de-uso/CU-realizar-reserva.md), y «Cancelar reserva», sin archivo (HC1).

| # | Tema | Decisión | Cambio |
|---|---|---|---|
| D1 | Unidades de un alojamiento | **Un alojamiento tiene una o más unidades** (tipos de habitación), cada una con `idUnidad` (ObjectId, estable aunque se renombre), `nombre`, `tipoHabitacion`, `numHabitaciones`, `capacidadMaxHuespedes` y `precioNoche`. Los servicios incluidos siguen siendo del alojamiento. **Una reserva es de una sola unidad**: para dos tipos de habitación, dos reservas. **Precio «desde» (RF37):** `precioBase` de un alojamiento es el menor `precioNoche` de sus unidades; se calcula al guardar (el dominio lo deriva y el validador lo exige con `$expr`), así que la búsqueda, el orden por precio y las tarjetas no cambian. **Filtros:** huéspedes, tipo de habitación y precio máximo los debe cumplir una misma unidad (`$elemMatch`), porque se reserva una sola. **Motivo del cambio:** la primera propuesta era una unidad por servicio (manda RF10), pero obligaba a simplificar WF-10 y a partir un hotel real en varios servicios; el equipo decidió seguir el wireframe. | Esquema v2 de `servicios` (migración M001); semilla de la «Suite familiar» (M002) |
| D2 | Categorías reservables | **Tour y Alojamiento.** Alojamiento: rango semiabierto [entrada, salida), máximo 14 noches, `cantidad` = habitaciones de la unidad, personas ≤ habitaciones × capacidad de la unidad. Tour: un horario, personas ≤ `aforoMaxGrupo`. Gastronomía y alquiler de equipos: `CATEGORIA_NO_RESERVABLE`. | `inventario_cupo` con clave (servicio, unidad, fecha, hora) `UNIQUE NULLS NOT DISTINCT` y CHECK de forma por categoría |
| D3 | Concurrencia | **UPDATE condicional + CHECK**, un día por sentencia, en orden de fecha y hora, en una transacción. Antes se comprueba que existan todas las filas (`FECHA_NO_DISPONIBLE`); si alguna no alcanza, `CUPO_AGOTADO` y no se descuenta nada. La cancelación también es un UPDATE condicional (solo si sigue Confirmada): dos cancelaciones a la vez liberan una sola vez. | V1 (`ck_inventario_cupo_disponibles`) |
| D4 | Datos del catálogo | Llamada síncrona directa al detalle público de ms-catalog, con circuit breaker, **antes** de abrir la transacción; el precio lo calcula el servidor (ADR-007). | — |
| D5 | Inventario | **Generador de inventario demo** (`samaria360.inventario-demo.habilitado`, activo en Docker Compose): 60 días, tours a las 08:00 y 14:00, una fila por unidad y noche; solo `ON CONFLICT DO NOTHING`. Sustituto temporal de RF19 (ver «Pendientes»). **Reemplazada por DEC-156** (iteración C, A2): la oferta del catálogo se materializa bajo demanda y el generador se retiró. | — |
| D6 | Idempotencia | `Idempotency-Key` obligatoria (UUID), única por turista, con el hash SHA-256 del pedido normalizado. El reenvío responde **201** con la misma reserva e `Idempotent-Replayed: true`; la misma clave con otro pedido, 422. Solo se guardan los intentos confirmados. Si dos pedidos con la misma clave compiten, el segundo lee la reserva ganadora (nunca un 500). | V1 (`uq_reserva_turista_clave`) |
| D7 | Mensajería | **Sin broker de mensajería.** ms-booking no publica eventos (RF13, RF30 y RF63 siguen con ms-notificaciones; ver «Pendientes»). Desde «Administrar cuentas» ([ADR-008](../sad/adr/ADR-008-sincronizacion-estado-cuentas.md)) **recibe** el estado de las cuentas: el relevo del outbox de ms-auth lo entrega por HTTP a `PUT /internal/v1/cuentas/{id}`, autenticado con un JWT de servicio. Es una entrega punto a punto con reintentos, no un bus de eventos. Reservar sigue sin publicar nada y el inventario solo depende de `reservas_db`. | V4 (tabla `cuenta`, ver «Administrar cuentas») |
| D8 | Estados y errores | Confirmada, Cancelada y Completada (sin «Pendiente»: RF20 confirma automáticamente). Códigos en `sad/api/ms-booking.md`; `OFERTA_NO_DISPONIBLE` es 409 al reservar (404 en el detalle del catálogo) y `UNIDAD_NO_DISPONIBLE`, 409. | V1 (`ck_reserva_estado`) |
| D9 | Fechas, horas y precio | Hora de Santa Marta (`America/Bogota`). Check-in 15:00 y check-out 12:00 (configurables). **RF44:** se puede cancelar si faltan 24 h o más (límite incluido); en alojamiento, la fecha programada es el check-in. Precio: alojamiento por noche y habitación de la unidad; tour por persona; COP. La reserva guarda `nombre_servicio`, `nombre_unidad`, `id_unidad` e `id_prestador` (RF32; consultar nunca llama al catálogo); su id es un uuid generado en el dominio. | V1 |
| D10 | Alcance | Solo backend, con el contrato (`sad/api/ms-booking.md`) y una demo reproducible (`docs/demo/reservas/`). WF-10 llega en la iteración siguiente. | — |

**Solicitud de cambio al SRS.** «RF10 (Alojamiento) debe admitir varias unidades, cada una con tipo, cantidad, capacidad y precio.» Hoy RF10 describe un solo tipo de habitación, número de habitaciones y capacidad por alojamiento.

**Fuente de verdad de la capacidad.**

| Dato | Base | Dueño | Significado |
|---|---|---|---|
| `unidades[].numHabitaciones`, `unidades[].capacidadMaxHuespedes` y `aforoMaxGrupo` | catalog_db | ms-catalog (RF10) | Capacidad configurada y límite por reserva |
| `unidades[].precioNoche` y `precioBase` (tour) | catalog_db | ms-catalog | Precio vigente; la reserva lo congela (RF32) |
| `inventario_cupo.cupos_totales` | reservas_db | ms-booking | Capacidad operativa de cada noche y unidad, o de cada horario; la crea el generador y la modificará RF19 |
| `inventario_cupo.cupos_disponibles` | reservas_db | ms-booking | Lo que queda: solo cambia al reservar o cancelar |

**Fuera de alcance de este CU:** RF12 del lado del prestador (el del turista ya está: «Mis reservas»), RF19 (el prestador modifica los cupos), RF13, RF30 y RF63 (auditoría y notificaciones). Los pagos y «Notificarme si se libera» de WF-10 no tienen requisito (diferencia 2 de la v2).

**Erratas del SRS** (referencias cruzadas a otro requisito):

| Requisito | Cita | Debería citar |
|---|---|---|
| RF21 | «RF08» | RF09 |
| RF29 | «RF16» | RF17 |
| RF29 | «RF12» | RF13 |
| RF30 | «RF19» | RF20 |
| RF44 | «RF17» | RF18 |
| RF52 | «RF16» | RF17 |
| RF61 | «RF15» | RF16 |

**Migraciones de catalog_db.** Hasta ahora catalog_db solo tenía los scripts de `infra/mongo/init`, que corren con el volumen vacío. Desde este cambio, esos scripts son la **línea base congelada** (formato v1) y el servicio de un solo uso `mongo-migraciones` aplica `infra/mongo/migraciones` (registradas en la colección `migraciones`), igual en un volumen nuevo que en uno existente. Cada migración lleva su propia copia del validador y es idempotente. M001 se detiene sin tocar nada si encuentra un alojamiento sin `precioBase` (no inventa precios).
