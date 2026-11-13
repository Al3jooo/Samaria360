# ADR-006 — Registro sin enumeración de cuentas

- **Estado:** Aceptada
- **Fecha:** 2026-10-14
- **Fase:** Elaboración (RUP), prototipo arquitectónico ejecutable
- **Componente:** `ms-auth` (CU Registrarse: `POST /api/v1/auth/registro/turista` y `/prestador`)
- **Requisitos:** RF01, RF02, RF15, RF34, RNF05

## Contexto

El CU Registrarse crea cuentas de turista (RF01) y de prestador (RF02). Si el registro respondiera **409 «correo ya registrado»**, cualquiera podría averiguar qué correos tienen cuenta (enumeración). El inicio de sesión ya lo evita a propósito: correo inexistente y contraseña incorrecta dan el mismo 401 y el mismo trabajo (BCrypt con un hash ficticio).

El patrón habitual es responder siempre igual y avisar por correo («ya tienes una cuenta»), pero el prototipo **aún no envía correos**: el envío llega con la recuperación de contraseña (RF34).

## Decisión

**1. Respuesta idéntica.** Correo nuevo o existente, el registro responde lo mismo:
- `202 Accepted`;
- mismo cuerpo, `{"estado":"RECIBIDO"}`;
- mismos encabezados, sin `Location` ni id.

**2. Mismo trabajo.** El hash BCrypt de la contraseña recibida se calcula **siempre**, también si el correo ya existe; si no, el tiempo de respuesta delataría el duplicado.

**3. Orden fijo:**
1. errores 400 de formato (`DATOS_INVALIDOS`, un código por campo) y de políticas (`POLITICAS_NO_ACEPTADAS`). Solo dependen de lo enviado, así que no revelan nada;
2. el hash;
3. el RNT (solo prestador);
4. el correo.

**4. RNT duplicado → `409 RNT_YA_REGISTRADO`.** Se acepta porque el Registro Nacional de Turismo es **público**. La comprobación **no depende del correo**, así que combinar los dos casos no revela nada:
- correo existente con RNT nuevo → 202;
- RNT existente → 409, exista o no el correo.

**5. Aviso al dueño.** Si el correo ya existe, el puerto `NotificadorCuentaPort` avisa a su dueño: resuelve el caso «olvidé que tenía cuenta». Hoy el adaptador (`NotificadorCuentaLogAdapter`) solo escribe un log **con el id del usuario, nunca con la dirección**: el correo en un log también es un dato personal.

**6. Sin inicio de sesión automático.** Al terminar, el frontend vuelve al login con un mensaje cierto en los dos casos y el correo prellenado:
- turista: «Registro recibido. Inicia sesión con tu correo»;
- prestador: «Solicitud recibida; quedará pendiente de validación».

El correo viaja por el **estado del router**, no por la URL, para que no quede en el historial ni en los logs. Un inicio de sesión automático revelaría si el correo era nuevo.

**7. Registros simultáneos.** Con el mismo correo, la restricción única `uq_usuario_correo` hace fallar al segundo; su transacción se revierte entera, sin filas a medias.
- **Reintento:** el decorador transaccional reintenta una vez. El correo ya existe y el registro sigue el camino normal: aviso al dueño y la misma respuesta 202, **nunca un 500**.
- **Cómo se reconoce la violación:** por SQLState `23505` y por el **nombre** de la restricción, que PostgreSQL envía como campo propio del error (`ServerErrorMessage.getConstraint()`). Nunca se compara el texto del mensaje.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| `409 CORREO_YA_REGISTRADO` | Mensaje más claro, pero permite enumerar cuentas; el login ya lo evita. |
| 409 e inicio de sesión automático del turista | Además de enumerar, el token diferenciaría el caso nuevo del existente. |
| Respuesta uniforme con envío de correo | Es el destino, pero no hay SMTP todavía (RF34). |

## Consecuencias y riesgo residual

- **423 del login:** el bloqueo de RF15 (423 `CUENTA_BLOQUEADA`, evaluado antes de la contraseña) todavía revela que una cuenta existe, porque solo se bloquean cuentas reales. Es el único riesgo residual del login: las cuentas inactivas, pendientes o rechazadas solo se informan con la contraseña correcta. Se acepta como riesgo conocido y se mitiga en Construcción con un **límite de tasa en el API Gateway**: implementado el 2026-11-13 (DEC-271; ver «Mitigación implementada»).
- **Caso límite aceptado:** dos registros de prestador simultáneos con el mismo correo **y** el mismo RNT. El segundo choca con la restricción del correo y se reintenta; el reintento ya ve el RNT y responde 409. Según cuál restricción falle primero, podría responder 202 en vez de 409. Se acepta: no revela datos de terceros.
- **RF34**, cuando se construya, deberá responder de forma uniforme («si el correo existe, te enviamos un enlace»).
- **Pendiente:** el envío real del aviso por correo electrónico (SMTP) llega con RF34; el puerto ya existe.

## Mitigación implementada (DEC-271, 2026-11-13)

`LimiteTasaWebFilter` (api-gateway) cuenta, por dirección del cliente y en una ventana fija, los `POST /api/v1/auth/login` (10 por minuto) y los `POST /api/v1/auth/registro/**` (5 cada 10 minutos, turista y prestador juntos). Al pasar el máximo responde **429 `DEMASIADAS_SOLICITUDES`** con `Retry-After`, sin llegar a ms-auth. Los valores están en `application.yml` (`samaria360.limite-tasa.*`).
- **Dirección del cliente:** el último valor de `X-Forwarded-For`, el que agrega nginx; los anteriores los puede inventar el cliente. El gateway solo se publica en 127.0.0.1 (DEC-257), así que el navegador siempre llega por nginx.
- **Alcance:** frena el recorrido de muchas cuentas o la creación masiva desde una misma conexión. No reemplaza el bloqueo por cuenta de RF15, que sigue en ms-auth.
- **Límite conocido:** el contador está en la memoria de una instancia del gateway; con varias réplicas, cada una cuenta por su cuenta. Un despliegue real necesita un contador compartido (por ejemplo, Redis con `RequestRateLimiter`).
- **Pruebas:** `LimiteTasaTest` y `ContadorVentanaFijaTest` (api-gateway); el mensaje del frontend, en `inicioSesion.test.tsx` y `registro.test.tsx`.

## Verificación

- **Pruebas** (`ms-auth`):
  - `RegistrarServiceTest` comprueba que el hash se calcula con correo nuevo y existente, el aviso y el orden.
  - `RegistroApiIntegracionTest` comprueba con PostgreSQL real:
    - mismo código, encabezados (sin `Date`) y cuerpo con un correo nuevo y uno existente;
    - el 409 del RNT;
    - la transacción revertida;
    - dos registros simultáneos con el mismo correo: dos 202 y una sola cuenta;
    - que el log no contiene el correo.
- **Con el sistema desplegado:** `diff` de encabezados y cuerpo, sin `Date`, de `curl -i` contra el gateway (ver `docs/trazabilidad.md`).
