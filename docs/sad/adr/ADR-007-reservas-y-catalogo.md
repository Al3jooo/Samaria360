# ADR-007 — Reservas: inventario propio y consulta síncrona al catálogo

- **Estado:** Aceptada
- **Fecha:** 2026-10-15
- **Fase:** Construcción (RUP)
- **Componentes:** `ms-booking` (`reservas_db`) y `ms-catalog` (`GET /api/v1/catalogo/servicios/{id}`)
- **Requisitos:** RF10, RF11, RF18, RF20, RF32, RF37, RF44, RNF01, RNF02, RNF03
- **Enmienda A2 (iteración C):** inventario bajo demanda; registra DEC-152 a DEC-159 (ver al final)
- **Enmienda DEC-278 (2026-11-14):** ms-notificaciones no existe en el Entregable 2 (ver al final)

## Contexto

Para reservar (RF20) hay que saber:
- si la oferta existe y está Publicada, de un prestador activo (RF16, RF22);
- su precio (RF37);
- la capacidad de la **unidad** elegida (RF10; un alojamiento tiene varias unidades, decisión D1 de `docs/trazabilidad.md`).

Esos datos son de ms-catalog (MongoDB). Los cupos de cada día (RF11) no existen en el catálogo. El prototipo no tiene mensajería. Y el principio Zero-Trust del Documento Entregable 2 exige que ninguna petición se acepte solo por venir de la red interna.

## Opciones

| Opción | Descripción |
|---|---|
| **(a)** | ms-booking llama directamente a ms-catalog por HTTP, con circuit breaker. |
| **(b)** | La misma llamada, pero a través del API Gateway. |

## Decisión: (a)

- **Inventario en `reservas_db`, dueño ms-booking.** Una fila por unidad y noche (alojamiento) o por horario (tour). Es la fuente de verdad de lo que queda; la capacidad configurada sigue en el catálogo. Los cupos solo cambian al reservar o cancelar, con UPDATE condicionales sobre una fila a la vez, en orden de fecha.
- **Consulta al endpoint público de detalle** (`GET /api/v1/catalogo/servicios/{id}`), el mismo que usará el frontend. Solo devuelve servicios Publicados de prestadores activos. No hay endpoint interno, ni JWT propagado, ni privilegios por estar en la red de Docker.
- **La llamada se hace antes de abrir la transacción.** Nunca hay filas de inventario bloqueadas durante una llamada HTTP; una prueba de integración lo verifica con `lock_timeout`.
- **Tiempos y reintento:**
  - conexión 500 ms y lectura 1,5 s;
  - **un reintento**, solo ante errores de red o respuestas 5xx; nunca ante 404 ni otros 4xx;
  - el reintento va **dentro** de la llamada protegida por el circuit breaker (Resilience4j): el circuito cuenta una sola llamada lógica.
- **Circuit breaker `catalogo`:** se abre si falla la mitad de las últimas 10 llamadas (con al menos 5) y vuelve a probar a los 10 s. Un 404 es una respuesta válida («no se puede reservar») y no cuenta como fallo.
- **El precio lo calcula el servidor y queda congelado en la reserva** (RF32). Si no coincide con el que vio el turista (`precioTotalEsperado`), 409 `PRECIO_CAMBIADO` con el precio vigente (RF37).
- **Disponibilidad en las tarjetas:** la compone el cliente con el resumen público de ms-booking (`/api/v1/reservas/disponibilidad/resumen`); el catálogo no guarda disponibilidad.

## Consecuencias

- **Acoplamiento temporal:** reservar depende de que ms-catalog responda. Con 99 % de disponibilidad cada uno (RNF01), la de «reservar» queda en ≈ 98 %. Se acepta en esta iteración.
- **Latencia máxima de «reservar» con el catálogo lento:** ≈ 4 s con 1 reintento (dos intentos de hasta 0,5 s de conexión más 1,5 s de lectura). Queda dentro de los 5 s de respuesta del API Gateway. Si ms-catalog no escucha (conexión rechazada), la respuesta 503 llega enseguida; con el circuito abierto, sin llamar al catálogo. Si su contenedor está detenido, el nombre `ms-catalog` no resuelve y el DNS de Docker tarda varios segundos en responderlo: por eso ms-booking usa `dns_opt` (`timeout:1`, `attempts:1`) en Docker Compose, y la respuesta llega en ~1–1,5 s.
- **Lo que no depende del catálogo:** cancelar, consultar las reservas propias y la disponibilidad. Responden con lo guardado en `reservas_db`. *Precisado por la enmienda A2:* la disponibilidad y el resumen llaman al catálogo solo para materializar lo que falte; lo ya materializado no depende de él.
- **Dos datos de capacidad con dueños distintos:** la capacidad configurada (catálogo, RF10) y los cupos de cada día (`inventario_cupo`). Un cambio en el catálogo no reescribe el inventario existente: eso le corresponde a RF19. Desde la enmienda A2, el catálogo también define la **oferta** (días de operación y salidas) y ms-booking la materializa.
- **Unidades eliminadas del catálogo:** sus filas de inventario quedan huérfanas y no se pueden reservar (409 `UNIDAD_NO_DISPONIBLE`); las reservas ya hechas se conservan.
- **Errores:** `SERVICIO_NO_DISPONIBLE` (503, `servicio: ms-catalog`) significa lo mismo que en el gateway: el servicio que no responde. Para que llegue tal cual al cliente, el circuito del gateway en la ruta de reservas cuenta solo 502 y 504; si ms-booking no responde, el fallback del gateway sí responde `servicio: ms-booking`.

## Cuándo revisarla

Cuando exista mensajería (ms-notificaciones) o si las pruebas de carga (RNF02, RNF03) muestran que la llamada síncrona pesa. *(Precisado por la enmienda DEC-278: la mensajería es RabbitMQ.)*

## Enmienda A2 (iteración C): inventario bajo demanda

- **Fecha:** 2026-10-25
- **Decisiones:** DEC-135 (equipo) y DEC-152 a DEC-159 (`docs/trazabilidad.md`, «Inventario bajo demanda — decisiones»)
- **Requisitos:** RF10 (oferta inicial, ampliación DEC-130), RF11, RF20

Esta enmienda no crea un ADR nuevo: la materialización usa la misma consulta síncrona al detalle público y la misma política de resiliencia.

### Contexto

Desde A1, el detalle público de ms-catalog trae la **oferta** de cada Tour y Alojamiento (DEC-136, DEC-150): `diasOperacion` y, en un tour, `salidas`. El generador de inventario demo (D5) la ignoraba (todos los días, 08:00 y 14:00) y solo corría en Docker Compose. Un servicio aprobado después no tenía cupos hasta su siguiente pasada.

### Decisión

1. **El catálogo es dueño de la definición; ms-booking la materializa bajo demanda** (DEC-135) con `INSERT … ON CONFLICT DO NOTHING`. Una fila existente nunca cambia, ni sus cupos (DEC-130: no hay reconciliación; eso es RF19).
2. **Dónde** (DEC-152): la disponibilidad, el resumen de las tarjetas y reservar. Siempre la ventana completa, de hoy a hoy + 59 en Santa Marta (DEC-153); nunca fechas pasadas ni posteriores.
3. **Marca por servicio** (DEC-154, tabla `oferta_materializada`, V5): «materializado hasta F». Una fecha hasta F sin filas es un día que no opera; una posterior, un día que falta materializar. El catálogo se consulta solo cuando la marca no cubre lo pedido: una vez por servicio y por día.
4. **Reservar no suma llamadas:** materializa con la oferta que ya leyó, después de validar y **antes** de abrir la transacción. La regla de este ADR se mantiene: nunca hay una llamada HTTP con filas bloqueadas.
5. **Misma política** de tiempos, reintento y circuit breaker `catalogo`. Un 404 no materializa ni avanza la marca (si el servicio se publica después, se materializa).
6. **Fallos** (DEC-155):
   - disponibilidad: con el rango cubierto, 200 sin llamar al catálogo; sin cubrir y con el catálogo caído, **503 `SERVICIO_NO_DISPONIBLE`** (`servicio: ms-catalog`), nunca un «sin fechas» falso;
   - resumen: corta en el primer fallo, con un **plazo total de 2,5 s** (`samaria360.disponibilidad.plazo-materializacion-resumen`) para quedar bajo los 5 s del gateway: si se agota, 503;
   - reservar: sin cambios (404 → 409 `OFERTA_NO_DISPONIBLE`; caído → 503).
7. **Concurrencia** (DEC-158): orden total de inserción (fecha, unidad, hora); la marca avanza después de las filas con `GREATEST`. Dos materializaciones simultáneas, o una con una reserva, no duplican filas ni pierden cupos.
8. **El generador D5 se retira** (DEC-156) y, con él, la dependencia de `GET /api/v1/catalogo/oferta`. Con la oferta que M008 rellenó en la semilla, las filas son las mismas.

### Consecuencias

- La disponibilidad y el resumen pasan a depender del catálogo **solo** cuando falta materializar. Con la marca al día, siguen respondiendo con `reservas_db`.
- **Riesgo residual:** un id que el catálogo responde 404 no deja marca, así que un resumen anónimo con ids que tienen forma de ObjectId pero no existen llama al catálogo cada vez (hasta 10 por petición). El 404 es rápido y no abre el circuito. Los ids sin forma de ObjectId se rechazan antes (400). Revisarlo con las pruebas de carga (RNF03).
- Las filas ya materializadas no siguen los cambios posteriores de la oferta: eso es RF19 (fuera de alcance, DEC-34).

### Cuándo revisarla

Además de lo anterior: si RF19 entra en alcance (habrá que reconciliar la oferta con las filas existentes) o si las pruebas de carga muestran que el 404 repetido del resumen pesa.

## Enmienda DEC-278 (2026-11-14): ms-notificaciones no existe

El Entregable 2 no tiene ms-notificaciones: la mensajería que esta decisión esperaba para revisarse es RabbitMQ (nivel 3 de [ADR-003](ADR-003-estrategia-conectividad.md)). La consulta síncrona al catálogo para reservar no cambia con esta enmienda.

## Enmienda RF19 (iteración I, DEC-310): el Prestador modifica el inventario

La enmienda A2 decía que las filas existentes de `inventario_cupo` nunca cambian. Con RF19 (decisión 3 del equipo) **cambian solo por el Prestador**, y nunca se borran: `PUT /api/v1/reservas/prestador/servicios/{id}/inventario` fija los cupos totales con un UPDATE condicional que conserva los reservados (no baja de ellos: 409 `CUPOS_RESERVADOS`) y abre una fila cerrada o crea una nueva; `DELETE` marca `cerrado` (V8) solo si no tiene reservas Confirmadas (409 `FECHA_CON_RESERVAS`). La reserva, la disponibilidad y el resumen ignoran las filas cerradas. La plantilla (`PUT …/plantilla`) no toca filas existentes: ms-booking publica `OfertaModificada` (outbox V8) y ms-catalog fija la `oferta` del servicio; la materialización siguiente la lee del detalle del catálogo, así que hay una ventana de consistencia eventual de segundos entre el 202 y la oferta nueva. Antes de cada cambio ms-booking lee el detalle público del catálogo, como al reservar (D4): la dirección de la dependencia síncrona no cambia.
