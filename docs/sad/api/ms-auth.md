# API de ms-auth

Contrato de ms-auth para el frontend y para las pruebas. Todas las rutas van por el API Gateway (`/api/v1/auth/**`); el servicio escucha en el 8081 solo dentro de la red de Docker. Este documento nace con el CU «Administrar cuentas» (RF28, RF22, RF62; WF-12) y fija los endpoints del **Administrador**, el token y el endpoint interno. Desde la revisión del 2026-11-12 también fija el inicio de sesión, `/me`, el registro, los países y el JWKS (RF15, RF01, RF02), que antes solo estaban en `docs/trazabilidad.md` («Endpoints de ms-auth»). Decisiones en `docs/trazabilidad.md` («Administrar cuentas — decisiones»), [ADR-008](../adr/ADR-008-sincronizacion-estado-cuentas.md) y el [CU](../../casos-de-uso/CU-administrar-cuentas.md). La iteración B1 agrega el **perfil del turista** (RF53; [CU](../../casos-de-uso/CU-actualizar-perfil.md), «Turista (B1) — decisiones»), y la B2, el **cambio de rol** (RF45; [CU](../../casos-de-uso/CU-cambiar-rol-cuenta.md), «Cambiar rol de cuenta — decisiones»).

## Convenciones

- **Autenticación:** `Authorization: Bearer <JWT de ms-auth>`. Los endpoints de `/admin/**` exigen el rol **Administrador**; `/perfil`, el rol **Turista**; `/prestador/perfil`, el rol **Prestador**; `/me`, cualquier rol.
- **Errores:** ProblemDetail (RFC 9457) con `codigo` estable, como ms-catalog y ms-booking. `DATOS_INVALIDOS` lleva `campos`: un código por campo con el nombre JSON, nunca textos (`REQUERIDO`, `FORMATO`, `LONGITUD_MINIMA`, `LONGITUD_MAXIMA`, `NO_PERMITIDO`, `DUPLICADO`).
- **Fechas:** `aaaa-mm-dd` (el día de registro; el modelo no guarda la hora) e instantes ISO 8601 en UTC.
- **Páginas de 10** (DEC-41), con `pagina` desde 0, como «Mis reservas»: `{elementos, pagina, tamano: 10, totalElementos, totalPaginas}`.

## Token de sesión (cambios de ADR-008)

`POST /api/v1/auth/login` emite un JWT RS256 con:

| Claim | Valor |
|---|---|
| `iss` | `samaria360-auth` |
| `sub` | `id_usuario` (UUID) |
| `rol` | `Turista`, `Prestador`, `Gestor` o `Administrador` |
| `ver` | `usuario.version_cuenta` al iniciar sesión (DEC-29) |
| `iat`, `exp` | `exp - iat` = **15 minutos** (antes, 60) |

`version_cuenta` sube en 1 con cada cambio de estado o de rol de la cuenta (RF45), y V15 la subió una vez para los Gestores y Administradores existentes (DEC-108). Cada servicio rechaza con **401 `SESION_INVALIDADA`** un token con `ver` menor que la versión conocida; un token sin `ver` cuenta como 0. ms-catalog y ms-booking rechazan también un token de Gestor o Administrador sin fila en su proyección (DEC-110). ms-auth compara contra su propia tabla en **todos** sus endpoints autenticados (`/me`, `/perfil`, `/prestador/perfil` y `/admin/**`). La respuesta de `/login` no cambia (`expiraEn` es ahora 15 minutos después).

## Inicio de sesión (RF15)

CU [«Iniciar sesión»](../../casos-de-uso/CU-iniciar-sesion.md). Público.

`POST /api/v1/auth/login`

```json
{ "correo": "turista@samaria360.test", "contrasena": "…" }
```

- **200** `{token, tipo: "Bearer", expiraEn, idUsuario, rol}`; `expiraEn` es un instante ISO 8601 (15 minutos después de emitirlo, DEC-29).
- **Orden de las verificaciones** (decisión D3): el correo se compara sin mayúsculas y sin espacios en los extremos.
  1. Cuenta bloqueada por intentos → **423 `CUENTA_BLOQUEADA`** con `bloqueadoHasta`. Es lo único que se responde antes de verificar la contraseña (riesgo residual aceptado en [ADR-006](../adr/ADR-006-registro-sin-enumeracion.md)).
  2. Correo inexistente o contraseña incorrecta → **401 `CREDENCIALES_INVALIDAS`**, con el mismo mensaje en los dos casos; con un correo inexistente se compara igual un hash BCrypt ficticio, para que el tiempo no revele la cuenta. Una contraseña incorrecta suma un intento, sea cual sea el estado de la cuenta; el tercer intento consecutivo bloquea la cuenta 15 minutos y responde 423.
  3. Con la contraseña correcta: **403 `CUENTA_INACTIVA`** si `estado_cuenta` no es Activo (RF22, RF62); **403 `CUENTA_PENDIENTE_VALIDACION`** o **`CUENTA_RECHAZADA`** si es un prestador sin validar (RF02, RF28). No se emite token y el conteo vuelve a 0.
  4. Token emitido: el conteo vuelve a 0 y se registra el acceso.
- **400 `DATOS_INVALIDOS`** con `campos` si falta el correo (o no tiene forma de correo) o la contraseña.

## Usuario autenticado

`GET /api/v1/auth/me` · cualquier rol → **200** `{idUsuario, correo, rol, estadoCuenta, nombreVisible}`. `nombreVisible` alimenta el avatar: el primer nombre y el primer apellido si la cuenta tiene fila en `turista` (también un Gestor o un Administrador con rol base Turista, RF45), el representante legal si es un prestador y, si no tiene ninguna de las dos (el personal de la semilla), la parte del correo antes de la `@`.
- **403 `CUENTA_INACTIVA`** si la cuenta dejó de estar Activa con el token vigente; **401 `SESION_INVALIDADA`** si el token es de antes de un cambio de la cuenta.

## Registro (RF01, RF02)

CU [«Registrarse»](../../casos-de-uso/CU-registrarse.md); [ADR-006](../adr/ADR-006-registro-sin-enumeracion.md). Públicos. **Respuesta uniforme:** **202 `{"estado": "RECIBIDO"}`** exista o no el correo, con BCrypt siempre y sin inicio de sesión automático. Si el correo ya existe no se crea nada y `NotificadorCuentaLogAdapter` deja un log con el id (el aviso por correo llega con RF34).

`POST /api/v1/auth/registro/turista`

```json
{
  "primerNombre": "Ana", "segundoNombre": null, "primerApellido": "Pérez", "segundoApellido": null,
  "correo": "ana@correo.com", "contrasena": "…", "paisOrigen": "CO",
  "categoriasInteres": ["Naturaleza", "Cultura"], "aceptoPoliticas": true
}
```

- Nombres y apellidos de 1 a 50 (los segundos, opcionales); correo de hasta 100 con forma de correo; contraseña de 8 caracteres como mínimo y 72 bytes UTF-8 como máximo (límite de BCrypt); `paisOrigen` de `GET /paises`; `categoriasInteres` de 1 a 3, sin repetir, de `Naturaleza`, `Cultura` y `Aventura` (decisión D1). La cuenta queda **Activa**.

`POST /api/v1/auth/registro/prestador`

```json
{
  "nombreRepresentanteLegal": "Luis Gómez", "razonSocial": null, "rnt": "12345",
  "correoCorporativo": "contacto@empresa.com", "telefono": "3001234567", "contrasena": "…", "aceptoPoliticas": true
}
```

- Representante de 1 a 150; razón social opcional, hasta 150 (D2); RNT de 1 a 50; teléfono de 1 a 20; el correo corporativo es también la credencial (D2). La cuenta queda **Pendiente de Validación** (RF02) y no puede iniciar sesión hasta que el Administrador la valide (RF28).
- **409 `RNT_YA_REGISTRADO`** si el RNT ya existe.

**Errores comunes:** **400 `DATOS_INVALIDOS`** con un código por campo (`REQUERIDO`, `FORMATO`, `LONGITUD_MINIMA`, `LONGITUD_MAXIMA`, `NO_PERMITIDO`, `DUPLICADO`), todos a la vez; con los datos válidos y `aceptoPoliticas` distinto de `true`, **400 `POLITICAS_NO_ACEPTADAS`** (D6).

## Países y clave pública

- `GET /api/v1/auth/paises` · público → **200** con la lista de códigos ISO 3166-1 alfa-2 admitidos (`["AR", "BR", "CO", …]`) y `Cache-Control: max-age=86400, public`. El frontend traduce los nombres (D5).
- `GET /api/v1/auth/.well-known/jwks.json` · público → el JWKS con la clave pública RS256. Lo usan ms-catalog y ms-booking para validar el JWT ([ADR-005](../adr/ADR-005-api-gateway.md)); el gateway no lo valida.

## Estados de la cuenta

| Estado | Quién | Significado |
|---|---|---|
| `Pendiente` | Prestador | «Pendiente de Validación» (RF02); no inicia sesión |
| `Activo` | Prestador y turista | Validado (prestador) y con la cuenta activa |
| `Rechazado` | Prestador | Rechazado con justificación (RF28); no inicia sesión |
| `Inactivo` | Prestador | Validado, pero con la cuenta inactiva (RF22); sus servicios no se ven |
| `Desactivado` | Turista | Desactivado temporalmente (RF62); no inicia sesión |

El prestador tiene dos columnas en `auth_db` (`estado_validacion` y `estado_cuenta`); el estado que viaja es el **derivado** (DEC-43), con la precedencia: Pendiente, Rechazado, Activo, Inactivo.

| Transición | Origen → destino |
|---|---|
| Aprobar | Pendiente → Activo |
| Rechazar | Pendiente → Rechazado (con justificación) |
| Inactivar | Activo → Inactivo |
| Desactivar | Activo → Desactivado (turista) |
| Reactivar | Desactivado → Activo (turista) |

Cualquier otra responde **409 `ESTADO_CAMBIADO`** (DEC-39), con `estadoActual`. El prestador Rechazado o Inactivo no se reactiva (DEC-37).

## Prestadores

### Listar

`GET /api/v1/auth/admin/prestadores?estado=&q=&pagina=0`

- `estado` (opcional): `Pendiente`, `Activo`, `Rechazado` o `Inactivo`. Otro valor: 400 `estado: NO_PERMITIDO`.
- `q` (opcional, hasta 100 caracteres): busca, sin distinguir mayúsculas, el texto contenido en el nombre del representante legal, el correo corporativo o el RNT. Los comodines de `LIKE` se escapan: «50%» busca esos caracteres. Más de 100: `q: LONGITUD_MAXIMA`.
- Orden: fecha de registro ascendente y `idUsuario` de desempate. La pestaña «Prestadores por validar» pide `estado=Pendiente`.

```json
{
  "elementos": [
    { "idUsuario": "0000…0006", "representanteLegal": "Marta Rojas", "rnt": "RNT-00002",
      "correoCorporativo": "contacto@kayaktaganga.test", "fechaRegistro": "2026-10-19", "estado": "Pendiente" }
  ],
  "pagina": 0, "tamano": 10, "totalElementos": 2, "totalPaginas": 1
}
```

### Detalle

`GET /api/v1/auth/admin/prestadores/{id}` → `200`:

```json
{
  "idUsuario": "0000…0006", "representanteLegal": "Marta Rojas", "razonSocial": null,
  "rnt": "RNT-00002", "correoCorporativo": "contacto@kayaktaganga.test", "telefono": "+57 3000000002",
  "aceptoPoliticas": true, "fechaRegistro": "2026-10-19", "estado": "Pendiente",
  "motivoRechazo": null, "revisadoEn": null
}
```

`razonSocial` es opcional (decisión D2 de «Registrarse»). `fechaRegistro` es el día del registro y de la aceptación de políticas; no hay hora (D6). `motivoRechazo` y `revisadoEn` los llena la validación. **404 `CUENTA_NO_ENCONTRADA`** si no existe o no es un prestador.

### Validar (RF28)

`POST /api/v1/auth/admin/prestadores/{id}/validacion`

```json
{ "decision": "Aprobar" }
{ "decision": "Rechazar", "justificacion": "El RNT no está vigente" }
```

- `decision`: `Aprobar` o `Rechazar`.
- `justificacion`: obligatoria al rechazar, de 1 a 500 caracteres sin contar los espacios de los extremos (DEC-42); al aprobar se ignora.
- **200**: el detalle del prestador con el estado nuevo. Guarda `revisadoEn`, quién revisó y, si se rechazó, el motivo.
- **400 `DATOS_INVALIDOS`**: `decision: REQUERIDO` o `FORMATO`; `justificacion: REQUERIDO` (vacía o en blanco) o `LONGITUD_MAXIMA`. No cambia nada.
- **404** `CUENTA_NO_ENCONTRADA`. **409** `ESTADO_CAMBIADO` (el prestador ya no está Pendiente).

### Inactivar (RF22)

`POST /api/v1/auth/admin/prestadores/{id}/inactivacion` · sin cuerpo (sin motivo, DEC-38) → **200** con el detalle (`estado: Inactivo`). **404**, **409** (no está Activo; también si ya está Inactivo). No hay reactivación (DEC-37).

## Turistas

`GET /api/v1/auth/admin/turistas?estado=&q=&pagina=0`

- `estado` (opcional): `Activo` o `Desactivado`. `q`: nombre (primer nombre y primer apellido) o correo, con las mismas reglas que en los prestadores.
- Un turista con `estado_cuenta = Inactivo` (dato heredado de antes de esta iteración; Inactivo es el estado de RF22) se lista como `Desactivado`.

```json
{
  "elementos": [
    { "idUsuario": "0000…0008", "nombre": "Marta Lee", "correo": "turista2@samaria360.test",
      "pais": "US", "fechaRegistro": "2026-10-21", "estado": "Activo" }
  ],
  "pagina": 0, "tamano": 10, "totalElementos": 1, "totalPaginas": 1
}
```

`pais` es el código ISO 3166-1 alfa-2 (decisión D5 de «Registrarse»); el frontend pone el nombre.

- `POST /api/v1/auth/admin/turistas/{id}/desactivacion` (RF62) → **200** con el turista (`estado: Desactivado`). **409** si no está Activo.
- `POST /api/v1/auth/admin/turistas/{id}/reactivacion` (RF62) → **200** con el turista (`estado: Activo`). **409** si no está Desactivado.
- Sin cuerpo (sin motivo, DEC-38). **404** `CUENTA_NO_ENCONTRADA` si no existe o no es un turista.

Un turista desactivado recibe **403 `CUENTA_INACTIVA`** al iniciar sesión (con la contraseña correcta) y **401 `SESION_INVALIDADA`** con un token de antes de la desactivación. Un prestador Rechazado recibe 403 `CUENTA_RECHAZADA`; uno Inactivo, 403 `CUENTA_INACTIVA`.

## Personal y cambio de rol (RF45)

Solo el rol **Administrador** (`/admin/**`). Asignar, modificar o revocar el rol de Gestor o Administrador ([CU](../../casos-de-uso/CU-cambiar-rol-cuenta.md); DEC-103 a DEC-106).

### Listar el personal

`GET /api/v1/auth/admin/personal?rol=&q=&pagina=0`

- `rol` (opcional): `Gestor` o `Administrador`; sin él, los dos. Otro valor: 400 `rol: NO_PERMITIDO`.
- `q` (opcional, hasta 100 caracteres): busca el texto contenido en el nombre (primer nombre y primer apellido del turista) o en el correo, con las mismas reglas que los demás listados.
- Orden: fecha de registro ascendente y `idUsuario` de desempate. Páginas de 10.

```json
{
  "elementos": [
    { "idUsuario": "0000…0003", "nombre": null, "correo": "gestor@samaria360.test", "rol": "Gestor",
      "fechaRegistro": "2026-10-19", "revocable": false }
  ],
  "pagina": 0, "tamano": 10, "totalElementos": 2, "totalPaginas": 1
}
```

- `nombre` es el del turista; es null si la cuenta no tiene fila en `turista` (las de la semilla).
- `revocable` dice si la tiene: solo esas cuentas vuelven a Turista.
- Para **asignar** un rol se elige un turista del listado de turistas (`GET /admin/turistas`).

### Cambiar el rol

`POST /api/v1/auth/admin/cuentas/{id}/cambio-rol`

```json
{ "rolNuevo": "Gestor", "rolActual": "Turista" }
```

- `rolNuevo`: `Turista`, `Gestor` o `Administrador`, distinto de `rolActual`. `rolActual` es el rol que vio el Administrador (control de concurrencia, como `estadoActual`).
- **Asignar:** de Turista a Gestor o Administrador. **Modificar:** de Gestor a Administrador o al revés. **Revocar:** a Turista.
- En una transacción: `usuario.id_rol`, la fila de `gestor` o `administrador` (la de `turista` se conserva), `version_cuenta + 1` y el evento `CuentaActualizada` con el rol nuevo y `estadoCuenta: Activo`.
- **200**: el elemento del listado del personal, con el rol nuevo (`rol: Turista` si se revocó).
- El token anterior de la cuenta recibe **401 `SESION_INVALIDADA`**. Al volver a iniciar sesión, el token trae el rol nuevo.

| Error | Código | Cuándo |
|---|---|---|
| 400 | `DATOS_INVALIDOS` | `campos.rolNuevo` o `campos.rolActual`: `REQUERIDO` (falta) o `NO_PERMITIDO` (desconocido, `Prestador` o `rolNuevo` igual a `rolActual`). Un cuerpo que no es JSON, sin `campos` |
| 404 | `CUENTA_NO_ENCONTRADA` | El id no existe |
| 409 | `CAMBIO_ROL_PROPIO` | La cuenta es la del Administrador que actúa |
| 409 | `CUENTA_NO_ELEGIBLE` | Lleva `motivo`: `PRESTADOR` (el Prestador no cambia de rol), `DESACTIVADA` (la cuenta no está Activa) o `SIN_ROL_BASE` (revocar una cuenta sin fila en `turista`) |
| 409 | `ROL_CAMBIADO` | El rol actual no es `rolActual`, o la cuenta cambió mientras tanto; lleva `rolActual` |
| 409 | `ULTIMO_ADMINISTRADOR` | El cambio dejaría el sistema sin Administradores activos (también con dos cambios simultáneos, DEC-106) |

Orden de evaluación: 400, 404 y, sobre la cuenta leída, `CAMBIO_ROL_PROPIO`, `CUENTA_NO_ELEGIBLE` (`PRESTADOR`), `ROL_CAMBIADO`, `CUENTA_NO_ELEGIBLE` (`DESACTIVADA`, `SIN_ROL_BASE`) y, al final, `ULTIMO_ADMINISTRADOR`.

Las rutas de turistas siguen filtrando por el rol actual: un Gestor o un Administrador con fila en `turista` no aparece en `GET /admin/turistas`, y desactivarlo o reactivarlo por esa ruta da 404 `CUENTA_NO_ENCONTRADA`.

## Perfil del turista (RF53)

Solo el rol **Turista** (otro rol → 403 `ACCESO_DENEGADO`); el turista es siempre el del claim `sub`. Se editan los nombres, los apellidos, el país y las categorías de interés. **El correo y la contraseña no se editan** (DEC-79). No sube `version_cuenta` ni escribe en el outbox.

`GET /api/v1/auth/perfil` → **200**

```json
{ "correo": "turista@samaria360.test", "primerNombre": "Ana", "segundoNombre": null,
  "primerApellido": "Pérez", "segundoApellido": null, "paisOrigen": "CO",
  "categoriasInteres": ["Naturaleza", "Cultura"] }
```

`PUT /api/v1/auth/perfil` · el mismo cuerpo sin `correo` → **200** con el perfil resultante (DEC-84).

- **Reemplaza el perfil completo**, no es parcial: un `segundoNombre` o `segundoApellido` ausente o vacío queda en null.
- Un `correo` en el cuerpo se ignora, como cualquier campo desconocido, y no cambia.
- **Reglas (las del registro, RF01; DEC-85):**
  - `primerNombre` y `primerApellido` obligatorios; los cuatro, de hasta 50 caracteres (puntos de código), recortados;
  - `paisOrigen`: código ISO 3166-1 alfa-2 de `GET /paises`, sin distinguir mayúsculas;
  - `categoriasInteres`: de 1 a 3, distintas, de `Naturaleza`, `Cultura` y `Aventura`. Se responden en ese orden.
- Las categorías se **reemplazan como conjunto** en la misma transacción que los datos: si algo falla, no cambia nada.
- **400** `DATOS_INVALIDOS` con `campos` (`REQUERIDO`, `FORMATO`, `LONGITUD_MAXIMA`, `NO_PERMITIDO`, `DUPLICADO`); un cuerpo que no es JSON, sin `campos`. **401** `NO_AUTENTICADO` (también si el token es válido, pero no hay fila en `turista`) o `SESION_INVALIDADA`. **403** `ACCESO_DENEGADO`.

## Perfil comercial del Prestador (RF43)

CU [«Actualizar datos comerciales»](../../casos-de-uso/CU-actualizar-datos-comerciales.md), parte de la cuenta (iteración D, DEC-188). Solo el rol **Prestador** (otro rol → 403 `ACCESO_DENEGADO`); el prestador es siempre el del claim `sub`. La parte que ve el Turista (nombre y contacto de cada servicio) está en [`ms-catalog.md`](ms-catalog.md) («Editar un servicio»).

`GET /api/v1/auth/prestador/perfil` → **200**

```json
{ "correo": "prestador@samaria360.test", "representanteLegal": "Carlos Gómez",
  "razonSocial": "Tours Sierra Nevada S.A.S.", "rnt": "RNT-00000",
  "correoCorporativo": "contacto@tours-sierra.test", "telefono": "+57 3000000000" }
```

- `correo` (la credencial), `representanteLegal`, `razonSocial` (puede ser null) y `rnt` van en **solo lectura**. El RNT cierra O16: el Prestador lo ve antes de registrar un servicio.

`PUT /api/v1/auth/prestador/perfil` · `{correoCorporativo, telefono}` → **200** con el perfil resultante.

- `correoCorporativo`: obligatorio, con la forma de un correo, hasta 100 caracteres; se guarda recortado y en minúsculas. Es el **contacto**, no la credencial (decisión D2): `usuario.correo` no cambia y el inicio de sesión sigue con el correo de acceso. Sin unicidad: no revela cuentas (ADR-006).
- `telefono`: obligatorio, hasta 20 caracteres, recortado (RF02 no fija el formato).
- Los demás campos del cuerpo se ignoran. No sube `version_cuenta`, no escribe en el outbox y no tiene control de concurrencia (como el perfil del turista, DEC-79 y DEC-84).
- **400** `DATOS_INVALIDOS` con `campos.correoCorporativo` (`REQUERIDO`, `FORMATO`, `LONGITUD_MAXIMA`) o `campos.telefono` (`REQUERIDO`, `LONGITUD_MAXIMA`). **401** `NO_AUTENTICADO` o `SESION_INVALIDADA`. **403** `ACCESO_DENEGADO`.

## Códigos de error

| Código | HTTP | Cuándo |
|---|---|---|
| `DATOS_INVALIDOS` | 400 | `campos` como se indica arriba. Un cuerpo que no es JSON también, sin `campos` |
| `POLITICAS_NO_ACEPTADAS` | 400 | Registro con datos válidos sin aceptar las políticas (D6) |
| `CREDENCIALES_INVALIDAS` | 401 | Login con un correo inexistente o una contraseña incorrecta (el mismo mensaje) |
| `NO_AUTENTICADO` | 401 | Sin token, vencido, inválido o de servicio (`rol = SERVICIO`) |
| `CUENTA_INACTIVA` | 403 | Login con la contraseña correcta o `/me`, con la cuenta no Activa (RF22, RF62) |
| `CUENTA_PENDIENTE_VALIDACION`, `CUENTA_RECHAZADA` | 403 | Login de un prestador sin validar o rechazado, con la contraseña correcta (D3) |
| `RNT_YA_REGISTRADO` | 409 | Registro de un prestador con un RNT que ya existe |
| `CUENTA_BLOQUEADA` | 423 | Login de una cuenta bloqueada por intentos; lleva `bloqueadoHasta` (RF15) |
| `SESION_INVALIDADA` | 401 | `ver` menor que `version_cuenta`: la cuenta cambió después de iniciar sesión |
| `ACCESO_DENEGADO` | 403 | El rol no es Administrador (`/admin/**`), no es Turista (`/perfil`) o no es Prestador (`/prestador/perfil`) |
| `CUENTA_NO_ENCONTRADA` | 404 | No existe, o es de otro rol (en el cambio de rol: no existe) |
| `ESTADO_CAMBIADO` | 409 | La cuenta no está en el estado de origen de la transición; lleva `estadoActual` (DEC-39) |
| `ROL_CAMBIADO` | 409 | La cuenta ya no tiene el rol desde el que se pidió el cambio; lleva `rolActual` (DEC-104) |
| `CUENTA_NO_ELEGIBLE` | 409 | La cuenta no admite el cambio de rol; lleva `motivo` (DEC-105) |
| `CAMBIO_ROL_PROPIO` | 409 | El Administrador intentó cambiar su propio rol (DEC-82) |
| `ULTIMO_ADMINISTRADOR` | 409 | El cambio dejaría el sistema sin Administradores activos (DEC-82) |
| `ERROR_INTERNO` | 500 | Cualquier otro error (si falla el evento, el cambio tampoco queda) |

## Endpoint interno de los consumidores

No es de ms-auth: lo exponen **ms-catalog y ms-booking** y lo llama el relevo de ms-auth. Se documenta aquí porque su cliente es ms-auth. **No pasa por el gateway** (no enruta `/internal/**`).

`PUT /internal/v1/cuentas/{id}` · `Authorization: Bearer <JWT de servicio>`

```json
{ "rol": "Turista", "estadoCuenta": "Desactivado", "version": 5 }
{ "rol": "Administrador", "estadoCuenta": "Activo", "version": 2 }
{ "rol": "Prestador", "estadoCuenta": "Activo", "version": 3, "rnt": "RNT-00000" }
```

- **JWT de servicio:** RS256 con el mismo JWKS; `sub = ms-auth`, `rol = SERVICIO`, `aud` = `ms-catalog` o `ms-booking`, `exp` de 5 minutos.
- `rol`: `Turista`, `Prestador`, `Gestor` o `Administrador`. `estadoCuenta`: `Pendiente`, `Activo`, `Rechazado`, `Inactivo` o `Desactivado`. `version`: entero de 1 en adelante.
- `rnt` (desde la iteración C, DEC-146): solo en la cuenta de un **Prestador**, que siempre lo tiene (`prestador.rnt`, NOT NULL). Viaja en la carga del outbox y el relevo lo envía solo si existe. ms-catalog lo guarda en su proyección para copiarlo en los servicios que se registren (DEC-126, HC2); **ms-booking lo ignora** (Jackson no rechaza propiedades desconocidas). V17 encoló un evento con el RNT por cada prestador y subió su `version_cuenta` (DEC-147): el token de un prestador anterior a V17 recibe un 401 `SESION_INVALIDADA` una vez.
- **204** se aplique o se ignore: *upsert* idempotente que solo avanza si `version` es mayor que la guardada (tolera duplicados y desorden). El evento de una cuenta desconocida crea la fila; así recibe el destino a cada Gestor y Administrador (V15) y cada cambio de rol (RF45).
- **400** `DATOS_INVALIDOS` con `campos` (`REQUERIDO`, `NO_PERMITIDO`, `VALOR_MINIMO`). **401** sin token o con una `aud` distinta del destino. **403** con un token de usuario.
