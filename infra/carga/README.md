# Pruebas de los RNF de operación

Pruebas reproducibles de RNF02 (API y carga de página), RNF03 y la tolerancia a fallos de RNF01. Usan solo endpoints públicos, sin token, y
no piden teselas de OpenStreetMap. No agregan dependencias al proyecto: k6 corre desde su imagen oficial de Docker.

## RNF02 y RNF03: carga con k6 (`rnf02-rnf03.js`)

**Criterios de aceptación (decisión del equipo; el SRS no fija percentil ni carga):** con 500 usuarios virtuales
sostenidos 5 minutos, el **p95 de cada endpoint por debajo de 2 s** y **menos del 1 % de errores**.

Cada usuario virtual repite el recorrido de un turista anónimo, con 1 a 3 s de pausa entre acciones:

1. busca con criterios variados de WF-05 (`GET /api/v1/catalogo/oferta`);
2. pide la disponibilidad de las tarjetas de la página (`GET /api/v1/reservas/disponibilidad/resumen`);
3. abre un detalle (`GET /api/v1/catalogo/servicios/{id}`, WF-06);
4. consulta la disponibilidad de un tour o un alojamiento (`GET /api/v1/reservas/disponibilidad`, WF-10).

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

### Resultados (2026-11-13)

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

Resultado (2026-11-14, local): 8 de 8 por debajo de 2 s. LCP mediano de 1,21 s (`/login`) a 1,64 s
(`/servicios/:id/reservar`); TTI máximo 1,61 s. Las fichas de detalle tienen el menor margen (una corrida aislada de
`/atractivos/:id` llegó a 2,02 s). La primera carga de `/explorar` transfiere 380 KB, de ellos 253 KB de JavaScript.

## RNF01: tolerancia a fallos (`resiliencia.sh`)

No mide la disponibilidad mensual (99 %): eso exige operación real y monitoreo. Comprueba los mecanismos que la
sostienen: detiene ms-catalog, verifica el 503 `SERVICIO_NO_DISPONIBLE` del gateway, que ms-booking siga sirviendo la
disponibilidad ya materializada y que ms-auth y el frontend no se afecten; luego lo vuelve a levantar y verifica que la
búsqueda se recupere sin reiniciar el gateway.

```bash
bash infra/carga/resiliencia.sh
```
