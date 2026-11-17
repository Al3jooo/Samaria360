# Pruebas de los RNF de operación

Pruebas reproducibles de RNF02 (API y carga de página), RNF03 y la tolerancia a fallos de RNF01. Usan solo endpoints públicos, sin token, y
no piden teselas de OpenStreetMap. No agregan dependencias al proyecto: k6 corre desde su imagen oficial de Docker.

## RNF02 y RNF03: carga con k6 (`rnf02-rnf03.js`)

**Criterios de aceptación (decisión del equipo; el SRS no fija percentil ni carga):** con 500 usuarios virtuales
sostenidos 5 minutos, el **p95 de cada endpoint por debajo de 2 s** y **menos del 1 % de errores**.

Cada usuario virtual repite el recorrido de un turista anónimo por el sistema completo del plan del 100 %
(DEC-325), con 1 a 3 s de pausa entre acciones:

1. carga los avisos del banner (`GET /api/v1/catalogo/avisos`, WF-14) y busca con criterios variados de WF-05
   (`GET /api/v1/catalogo/oferta`, con orden por calificación y filtro de accesibilidad);
2. pide la disponibilidad de las tarjetas de la página (`GET /api/v1/reservas/disponibilidad/resumen`);
3. abre la ficha de un servicio con sus calificaciones (`GET /api/v1/catalogo/servicios/{id}` y
   `GET /api/v1/analytics/calificaciones`, WF-06, ms-analytics);
4. consulta la disponibilidad de un tour o un alojamiento (`GET /api/v1/reservas/disponibilidad`, WF-10);
5. abre la ficha de un atractivo con su afluencia, sus alternativos y sus calificaciones
   (`GET /api/v1/catalogo/atractivos/{id}`, `…/afluencia`, RF39 y RF51).

Quedan fuera de la carga, por diseño: el asistente (exige sesión, tiene un tope de 20 mensajes por turista y día y
llamaría a Gemini, un servicio externo con cuota) y las pantallas con sesión (el inicio de sesión de la semilla no se
automatiza, O18). Cada ficha abierta **cuenta como una consulta** en los tableros (RF14, RF31; DEC-307): después de
una corrida, los tableros del entorno probado muestran esas consultas.

Perfil `carga` (por defecto): rampa de 0 a 500 usuarios en 2 min, 5 min sostenidos y 30 s de bajada. Perfil `base`:
1 usuario durante 1 minuto (la referencia sin carga). El `setup` toma los ids de la semilla y materializa el
inventario una vez, para no medir el primer INSERT de cada servicio.

Con el sistema arriba (`docker compose up -d --build`), desde la raíz del repositorio. k6 entra por nginx
(`frontend:8080`) dentro de la red de Compose, el mismo camino del navegador (nginx → api-gateway → servicio):

```bash
docker run --rm -i --network sm_tours_aplication_default -e PERFIL=base -v "$PWD/infra/carga:/carga" grafana/k6:1.3.0 run /carga/rnf02-rnf03.js
```

```bash
docker run --rm -i --network sm_tours_aplication_default -v "$PWD/infra/carga:/carga" grafana/k6:1.3.0 run --summary-export /carga/resumen.json /carga/rnf02-rnf03.js
```

En Git Bash de Windows, anteponer `MSYS_NO_PATHCONV=1` y usar `$(pwd -W)` en lugar de `$PWD`. El nombre de la red
sale del nombre de la carpeta del proyecto (`docker network ls`). k6 termina con código distinto de 0 si no se cumple
algún umbral.

**Límites de la evidencia:** k6 y el sistema comparten la máquina; una sola instancia por servicio con los límites de
memoria de `docker-compose.yml`. Es la línea base del prototipo, no una medición de producción.

Perfil `demo` (500 usuarios, 1 min de rampa, 2 min sostenidos): para la demo pública, **solo con aviso al equipo**.
Desde fuera de la red de Compose, con `-e BASE=https://…` y sin `--network`.

### Resultados con el sistema completo (2026-11-15, plan del 100 %)

Recorrido de 8 a 9 peticiones por iteración (el doble que el anterior), con ms-analytics, ms-chatbot y RabbitMQ en
marcha. Pico de memoria del sistema durante la carga: unos 2,8 GB (`docker stats`; ms-auth 426 MB, ms-booking
408 MB, mongo 388 MB, api-gateway 356 MB).

| Corrida | Peticiones | Errores | p95 por endpoint |
|---|---|---|---|
| Local, nginx con una conexión nueva por petición | 203 012 | **11,97 %** (503 de nginx: `connect() failed (99: Address not available)`) | 35 a 40 ms |
| Local, nginx con conexiones persistentes al gateway (DEC-325) | 207 702 (454/s) | **0 %** | avisos 5,8 · oferta 7,8 · resumen 6,3 · detalle 6,1 · calificaciones 8,7 · disponibilidad 6,0 · atractivo 5,8 · afluencia 7,0 ms |
| Demo pública (perfil `demo`, 2026-11-16), primera corrida | 59 474 | 68,2 % (`connect: connection refused` desde el primer segundo, con 10 usuarios) | 560 a 861 ms (solo las que respondieron) |
| Demo pública (perfil `demo`, 2026-11-16), segunda corrida | 83 111 (383/s) | **0,01 %** (11) | 206 a 261 ms |

**La primera corrida en la demo** se descarta como medición: los rechazos empezaron con 10 usuarios, cuando la VM
estaba casi ociosa (CPU máxima del 60 % en el gateway, 2,2 GB en uso) y sin reinicios ni errores en Caddy. Fue un
corte transitorio en la red de salida del equipo que ejecutaba k6: a continuación, 200 conexiones paralelas desde
Docker y la segunda corrida completa pasaron sin problema. En la demo el p95 incluye la ida y vuelta por internet; la
VM (2 vCPU, 12 GB) usó como máximo 2,2 GB durante la carga. Después de las corridas se restauró la colección
`consultas` de analytics_db en la VM con un respaldo previo, para que los tableros (RF14, RF31) no queden con las
consultas de k6.

**Defecto encontrado (DEC-325).** nginx abría una conexión TCP nueva hacia el gateway en cada petición. A unas
440 peticiones/s, los sockets en `TIME_WAIT` agotaban los puertos efímeros del contenedor (32768 a 60999, 60 s cada
uno) y nginx respondía 503 al 12 %. Con 4 peticiones por iteración (la corrida del 62 %, ~260/s) no se alcanzaba ese
límite. Ahora `frontend/nginx/default.conf` usa un `upstream` con `keepalive` y `server … resolve`, que conserva la
re-resolución DNS de DEC-260: comprobado recreando el gateway con otra IP (nginx lo encontró en menos de 12 s).

### Resultados anteriores (2026-11-13, sistema del 62 %)

| Corrida | Peticiones | Errores | p95 oferta / detalle / disponibilidad / resumen |
|---|---|---|---|
| Local, gateway con el bulkhead por defecto | 117 841 | **2,16 %** (503 del circuito abierto) | 10,6 / 9,4 / 9,2 / 9,2 ms |
| Local, sin el bulkhead (DEC-277) | 118 605 | **0 %** | 8,3 / 7,3 / 6,9 / 7,1 ms |
| Demo pública (perfil `demo`), sin la corrección desplegada | 50 433 | 3,25 % (solo oferta y detalle) | 138 / 136 / 144 / 120 ms |
| Demo pública (perfil `demo`), con DEC-277 desplegado (2026-11-14) | 51 047 | **0 %** | 110 / 114 / 111 / 107 ms |

En la demo el p95 incluye la ida y vuelta por internet desde el equipo que ejecuta k6 (mediana ~43 ms).

## RNF02: carga de página con Lighthouse (`rnf02-carga-pagina.sh`)

Primera carga (sin caché) de 8 pantallas públicas en un móvil simulado (CPU ×4) con red 4G: 9 Mbps de bajada, 3 Mbps
de subida y 70 ms de latencia («condiciones normales», decisión del equipo). Mediana de 3 corridas por página;
criterio: LCP (contenido principal visible) e interactivo (TTI) por debajo de 2 s. Bloquea las teselas de
OpenStreetMap. Lighthouse corre con `npx` (versión fija), con Chrome o Edge:

```bash
bash infra/carga/rnf02-carga-pagina.sh
```

```bash
CHROME_PATH="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" bash infra/carga/rnf02-carga-pagina.sh
```

Resultado (2026-11-15, local, sistema completo): **9 de 9 por debajo de 2 s** (con `/recuperar`, WF-27).

| Página | LCP mediano (máx.) | TTI | Peso |
|---|---|---|---|
| `/` | 1,54 s (1,70) | 1,67 s | 810 KB |
| `/explorar?texto=playa` | 1,59 s (1,82) | 1,61 s | 402 KB |
| `/servicios/:id` | 1,81 s (**2,15**) | 1,57 s | 431 KB |
| `/servicios/:id/reservar` | 1,57 s (1,80) | 1,47 s | 322 KB |
| `/atractivos/:id` | 1,86 s (1,96) | 1,79 s | 545 KB |
| `/agenda` | 1,39 s (1,39) | 1,23 s | 302 KB |
| `/preguntas-frecuentes` | 1,61 s (1,68) | 1,43 s | 299 KB |
| `/login` | 1,24 s (1,36) | 1,22 s | 294 KB |
| `/recuperar` | 1,10 s (1,23) | 1,22 s | 294 KB |

Las fichas siguen con el menor margen: ahora cargan además las calificaciones (y, en el atractivo, la afluencia y
los alternativos); una de las tres corridas de `/servicios/:id` pasó de 2 s.

**Demo pública con las correcciones de QA (2026-11-17, después de #78; DEC-330):** 9 de 9 por debajo de 2 s.

| Página | LCP mediano (máx.) | TTI | Peso |
|---|---|---|---|
| `/` | 1,48 s (1,51) | 1,42 s | 669 KB |
| `/explorar?texto=playa` | 1,39 s (1,50) | 1,45 s | 351 KB |
| `/servicios/:id` | 1,59 s (1,64) | 1,47 s | 386 KB |
| `/servicios/:id/reservar` | 1,70 s (1,81) | 1,70 s | 277 KB |
| `/atractivos/:id` | **1,79 s** (1,82) | 1,54 s | 565 KB |
| `/agenda` | 1,37 s (1,54) | 1,26 s | 260 KB |
| `/preguntas-frecuentes` | 1,44 s (1,49) | 1,14 s | 259 KB |
| `/login` | 1,49 s (1,64) | 1,21 s | 256 KB |
| `/recuperar` | 1,16 s (1,20) | 1,16 s | 256 KB |

- La ficha del atractivo, que antes quedaba en el límite (1,98 s), queda con margen.
- **Comparación controlada:** con la misma API de la demo y corridas alternadas (5 por versión), el build de antes
  de #78 y el de después dieron LCP medianos de 1,96 → 1,88 s (servicio), 2,17 → 2,11 s (atractivo) y 1,70 → 1,57 s
  (explorar). La precarga de fuentes sola no cambia el resultado (con y sin ella, 2,20 y 2,22 s en el servicio).
- **Validez de la medición:** el freno de CPU ×4 se aplica sobre la CPU del equipo que mide. Tres corridas previas
  del mismo día dieron de 5 a 6 páginas por encima de 2 s porque otra aplicación ocupaba la CPU (el
  `environment.benchmarkIndex` de Lighthouse bajó de ~3000 a ~1200 y el trabajo del hilo principal se duplicó) y,
  en la primera, los servicios recién arrancados. Se toma la corrida con `benchmarkIndex` de 2400 a 3360 en todas
  las páginas; conviene revisar ese valor en los JSON antes de comparar resultados.

**Demo pública, antes de las correcciones de QA (2026-11-16, `bash infra/carga/rnf02-carga-pagina.sh https://samaria360.duckdns.org`):** con HTTPS,
Caddy y la latencia real por internet. 7 de 9 por debajo de 2 s en la primera pasada; las dos que fallaron se
repitieron con 3 corridas más:

| Página | LCP mediano (máx.) | TTI | Peso |
|---|---|---|---|
| `/` | 1,68 s (1,77) | 1,88 s | 668 KB |
| `/explorar?texto=playa` | 1,79 s (1,80) | 1,97 s | 349 KB |
| `/servicios/:id` | 1,91 s (1,91) | 1,82 s | 385 KB |
| `/servicios/:id/reservar` | 1,97 s (1,99) | 1,86 s | 276 KB |
| `/atractivos/:id` | 2,34 s (2,38); repetida: **1,98 s** (2,04) | 2,24 s; repetida: 1,79 s | 564 KB |
| `/agenda` | 1,53 s (1,60) | 1,41 s | 260 KB |
| `/preguntas-frecuentes` | 1,62 s (2,53) | 1,59 s | 259 KB |
| `/login` | 1,39 s (1,55) | 1,36 s | 256 KB |
| `/recuperar` | 3,52 s (3,75); repetida: **1,32 s** (1,37) | 3,64 s; repetida: 1,43 s | 256 KB |

- **`/recuperar`:** en la primera pasada el propio documento HTML tardó de 0,75 a 2,67 s en llegar, cuando en
  `/login` (el mismo `index.html`) tardó 0,26 s. Fue una variación de red de ese momento: repetida, 1,32 s.
- **`/atractivos/:id` queda en el límite** en la demo: LCP de 1,91 a 2,38 s según la corrida (mediana 1,98 s al
  repetir). Es la ficha más pesada (fotos, afluencia, alternativos y calificaciones). Para tener margen haría falta
  aligerarla, por ejemplo con imágenes más livianas o diferidas.
- La resiliencia (RNF01) no se corre en la demo: detendría servicios de la demo pública; el mecanismo se probó en
  local con la misma imagen de Compose.

Resultado anterior (2026-11-14, local, sistema del 62 %): 8 de 8 por debajo de 2 s. LCP mediano de 1,21 s (`/login`) a 1,64 s
(`/servicios/:id/reservar`); TTI máximo 1,61 s. Las fichas de detalle tienen el menor margen (una corrida aislada de
`/atractivos/:id` llegó a 2,02 s). La primera carga de `/explorar` transfiere 380 KB, de ellos 253 KB de JavaScript.

## RNF01: tolerancia a fallos (`resiliencia.sh`)

No mide la disponibilidad mensual (99 %): eso exige operación real y monitoreo. Comprueba los mecanismos que la
sostienen, deteniendo un componente a la vez:

- **ms-catalog:** el gateway responde 503 `SERVICIO_NO_DISPONIBLE`; ms-booking sigue sirviendo la disponibilidad ya
  materializada; ms-auth, ms-analytics y el frontend no se afectan;
- **ms-analytics:** las calificaciones dan 503, pero la ficha, la afluencia y la búsqueda siguen;
- **ms-chatbot:** el asistente da 503 y el resto sigue;
- **RabbitMQ:** la lectura sigue, los cinco microservicios siguen sanos (el bróker no cuenta para su salud; los
  eventos esperan en los *outbox*, ADR-011) y, al volver, cada cola recupera su consumidor sin reiniciar nada.

Cada componente, al volver, se recupera sin reiniciar el gateway ni los demás servicios.

```bash
bash infra/carga/resiliencia.sh
```

Resultado (2026-11-15, sistema completo): **superada**, 33 de 33 comprobaciones. Con un servicio caído, las primeras
peticiones tardan unos 4 s en recibir el 503 (el gateway intenta resolver y conectar); en cuanto el circuit breaker
se abre, el 503 sale en 5 a 7 ms. Al volver, los servicios quedan sanos en 10 a 24 s.
