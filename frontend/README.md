# Samaria360 — Frontend

React + Vite + TypeScript. Prototipo arquitectónico ejecutable de la fase de Construcción (RUP); el alcance de la iteración está en el [README de la raíz](../README.md) y cada pantalla tiene su sección más abajo. Las pantallas siguen la estructura de los wireframes de `docs/wireframes/`; la trazabilidad está en `docs/trazabilidad.md`.

## Comandos

```bash
npm install
npm run dev      # http://localhost:5173 — /api se redirige al API Gateway (http://localhost:8080)
npm test         # Vitest + Testing Library (incluye la guardia de contraste)
npm run build    # tsc -b + build de producción en dist/
npm run lint     # ESLint (eslint.config.js)
```

Para usar la app de punta a punta: `docker compose up -d` en la raíz y `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` en `ms-auth/`.

## Estructura

| Carpeta | Contenido |
|---|---|
| `src/app/` | Rutas, diseños de la plataforma y de acceso (encabezado y pie), ruta protegida, **`estilos.css` (tokens)** |
| `src/features/<caso de uso>/` | Páginas, estado y llamadas de cada caso de uso: `auth/`, `catalogo/` (con `detalle/`, WF-06), `reservas/` (WF-10 y WF-11), `contenido/` (WF-13 y WF-15 públicas y el banner de WF-14) y `admin/` (WF-12 y la gestión de WF-13 y WF-14) |
| `src/shared/api/` | Cliente HTTP (`fetch` + token Bearer solo en las llamadas autenticadas, DEC-47) y almacenamiento del token y su vencimiento |
| `src/shared/components/` | Componentes reutilizables (entre ellos `Paginacion`, `BotonNoDisponible` y `SinPermiso`), iconos e ilustraciones |
| `src/i18n/` | Textos en `es.json` y `en.json` (RF03, RNF08) |

## Dependencias

Versiones instaladas (`package-lock.json`) y licencias leídas de `node_modules`. Toda dependencia nueva se agrega aquí con su licencia y su razón.

| Dependencia | Versión | Licencia | Para qué |
|---|---|---|---|
| `react`, `react-dom` | 19.3.0 | MIT | Interfaz |
| `react-router-dom` | 7.18.4 | MIT | Rutas; los filtros de WF-05 viven en la URL |
| `i18next`, `react-i18next` | 26.4.3, 17.0.16 | MIT | Textos ES/EN y plurales (RF03, RNF08) |
| `@fontsource-variable/fraunces`, `@fontsource-variable/figtree` | 5.3.0 | OFL-1.1 | Tipografías empaquetadas (sin cargarlas desde internet) |
| `leaflet` | 1.9.4 | BSD-2-Clause | Mapa de WF-05 (`MapaOferta.tsx`), usado directamente |
| **Desarrollo** | | | |
| `vite`, `@vitejs/plugin-react` | 8.3.4, 6.1.2 | MIT | Servidor de desarrollo, proxy `/api` y build |
| `typescript` | 6.0.3 | Apache-2.0 | Tipos (`tsc -b` en el build) |
| `vitest`, `jsdom` | 5.0.3, 30.1.2 | MIT | Pruebas |
| `@testing-library/react`, `/dom`, `/jest-dom`, `/user-event` | 16.3.3, 10.4.2, 7.0.1, 14.6.7 | MIT | Pruebas por roles y nombres accesibles |
| `@types/react`, `@types/react-dom`, `@types/node`, `@types/leaflet` | 19.3.0, 19.3.0, 24.19.2, 1.9.22 | MIT | Tipos |
| `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `globals` | 10.14.0, 10.0.1, 8.72.1, 7.1.1, 17.14.0 | MIT | Análisis estático (`npm run lint`, `eslint.config.js`): errores comunes y reglas de los hooks; corre en el CI |

**Excluida: `react-leaflet`.** Su licencia es Hippocratic-2.1, que restringe usos por criterios éticos y no es una licencia de código abierto aprobada por la OSI; se descartó para no condicionar el uso del software de la Alcaldía. El mapa usa Leaflet directamente: se crea en un `useEffect`, se actualiza por capas (`layerGroup`) y la limpieza llama a `map.remove()`.

**Carga diferida.** `/explorar` se carga con `React.lazy` (`src/app/App.tsx`), así que Leaflet y su CSS solo se descargan al entrar ahí. En el build, Leaflet queda en su propio archivo (`leaflet-[hash].js` y `.css`, grupo `codeSplitting` en `vite.config.ts`). Mientras llega la página se ve el estado de carga del catálogo; si la descarga falla, el límite de errores (`shared/components/LimiteErrores.tsx`) muestra «No pudimos cargar esta sección. Revisa tu conexión.» con «Reintentar», que recarga la página. Como `leaflet.css` llega después del CSS principal, los ajustes de `catalogo.css` sobre clases de Leaflet llevan el prefijo `.mapa-oferta` para no depender del orden.

**Teselas de OpenStreetMap: el único recurso externo.** Se piden a `tile.openstreetmap.org` según su [política de uso](https://operations.osmfoundation.org/policies/tiles/):
- la atribución «© OpenStreetMap contributors» está siempre visible;
- el zoom máximo es 19;
- no hay descarga masiva, prefetching ni caché offline: solo se piden las teselas del área visible;
- se envía el `Referer` por defecto del navegador.

Es adecuado para el prototipo; para un despliegue con tráfico real hace falta un proveedor de teselas propio o contratado. En las pruebas, Leaflet se reemplaza por `src/test/leafletSimulado.ts` (jsdom no dibuja mapas), así que no se pide ninguna tesela.

## Reservas (WF-06, WF-10 y WF-11)

El backend es ms-booking; el contrato está en [`docs/sad/api/ms-booking.md`](../docs/sad/api/ms-booking.md). Las tres
pantallas se cargan de forma diferida (cada una en su archivo, como `/explorar`).

| Ruta | Pantalla | Acceso |
|---|---|---|
| `/servicios/:id` | WF-06 mínimo (`features/catalogo/detalle/DetalleServicioPage.tsx`) | Pública |
| `/servicios/:id/reservar?entrada=&salida=&personas=&unidad=&habitaciones=` (alojamiento) o `?fecha=&hora=&personas=` (tour) | WF-10 (`features/reservas/ReservaPage.tsx`) | Pública; confirmar exige sesión de Turista |
| `/mis-reservas?desde=&hasta=&estado=&pagina=` | WF-11 (`features/reservas/MisReservasPage.tsx`) | Privada (`RutaProtegida`), solo Turista |

Reglas:

- **Selección en la URL:** WF-10 guarda la selección en la consulta. Si trae fechas válidas, la disponibilidad se consulta
  al cargar; así, «Confirmar» sin sesión lleva al login y el login vuelve a la misma selección.
- **`Idempotency-Key`** (`reservas/intentoReserva.ts`):
  - se guarda en `sessionStorage` (`samaria360.intentoReserva`), asociada al turista y al cuerpo exacto del pedido;
  - la misma selección reintentada (después de un error de red, un 503 o una recarga) sale con la misma clave;
  - otra selección o un precio nuevo, con otra;
  - una respuesta definitiva (201 o 4xx) borra el registro;
  - si `sessionStorage` falla, el registro vive en memoria mientras la página esté abierta.
- **Dinero en centavos enteros** (`reservas/dinero.ts`):
  - los precios del catálogo (número o texto decimal) se aceptan con 2 decimales como máximo, comprobados sobre su texto;
  - el total se multiplica en enteros;
  - `precioTotalEsperado` se envía como número decimal exacto (`"precioTotalEsperado":1560003.00`, sin comillas), armado en
    el cuerpo canónico (`cuerpoReserva`) y enviado tal cual (`cuerpoTexto` de `solicitar`);
  - un precio con otro formato deja el total sin calcular y «Confirmar» bloqueado con el motivo.
- **Precio cambiado:** con 409 `PRECIO_CAMBIADO`, se muestran el precio anterior y el nuevo (`precioTotal`) y «Aceptar el
  nuevo precio y confirmar» envía el nuevo total (con otra clave).
- **Unidades:** los nombres y precios salen del detalle de ms-catalog; la disponibilidad, de `/api/v1/reservas/disponibilidad`
  (sin nombres ni precios). El cupo de una unidad es el mínimo de las noches. Con 409 `UNIDAD_NO_DISPONIBLE`, se vuelven a
  cargar el detalle y la disponibilidad.
- **Fechas en hora de Santa Marta** (`reservas/fechasBogota.ts`): `hoyBogota()` no depende de la zona del navegador; las
  fechas del calendario se suman en UTC.
- **Tarjetas:** `useDisponibilidadResumen` pide `/api/v1/reservas/disponibilidad/resumen` una vez por página, solo para Tour y
  Alojamiento, de hoy a hoy + 6. Los textos son «Disponible esta semana», «Agotado los próximos 7 días» y «Sin fechas»;
  mientras carga, si falla o en las demás categorías, «—».
- **Cancelar (WF-11):** `<dialog>` nativo con `showModal()`. `cancelableHasta` lo calcula ms-booking; pasado ese momento, el
  botón queda con `aria-disabled` y el motivo. En las pruebas, `src/test/setup.ts` agrega un polyfill mínimo de
  `showModal()` y `close()` (jsdom no los tiene).

## Sesión y Administrar cuentas (WF-12)

**Sesión (DEC-47 a DEC-50, `docs/trazabilidad.md`).** El token vence a los 15 minutos (DEC-29).
- Las funciones de API de endpoints públicos pasan `publica: true` a `solicitar`: salen sin `Authorization` y su respuesta
  nunca cierra la sesión. Son el login, el registro, los países, la oferta, los filtros, el detalle y la disponibilidad.
- Una petición autenticada con 401, o con 403 `CUENTA_INACTIVA`, cierra la sesión. Un 403 por rol (`ACCESO_DENEGADO`) no
  la cierra.
- El login guarda `expiraEn` en `sessionStorage` (`samaria360.expiraEn`). `SesionProvider` cierra la sesión al llegar a esa
  hora, con un temporizador y una revisión al volver a la pestaña; antes de cerrar, vuelve a leer el valor guardado. Si el
  reloj del equipo va adelantado, la sesión se cierra antes; si va atrasado, la cierra el 401.
- Avisos: «Tu sesión expiró», «Tu sesión se cerró porque cambió el estado o los permisos de tu cuenta»
  (`SESION_INVALIDADA`) y «Tu cuenta no está activa». En una ruta privada, el login muestra el aviso.

**WF-12** (`features/admin/`; contrato en [`docs/sad/api/ms-auth.md`](../docs/sad/api/ms-auth.md)):

| Ruta | Pantalla | Acceso |
|---|---|---|
| `/admin/cuentas?pestana=&estado=&rol=&q=&pagina=` | WF-12 (`AdministrarCuentasPage.tsx`), diferida | Privada, solo el Administrador (`RutaProtegida rol="Administrador"`); otro rol ve «Sin permiso» |

- La página de la URL empieza en 1 y la de la API, en 0 (páginas de 10).
- La justificación del rechazo se cuenta y se envía sin los espacios de los extremos (1 a 500, `justificacion.ts`).
- Después de validar o inactivar se recarga la página actual. Después de desactivar o reactivar, la fila se reemplaza en su
  sitio. Un 409 o un 404 avisan en la región de estado y recargan la lista.
- Cambiar rol (RF45, DEC-115 a DEC-123): pestaña «Personal» (`pestana=personal`, filtro `rol`) y «Asignar rol» en
  «Turistas». Modificar reemplaza la fila en su sitio; asignar y revocar recargan la página actual. Si la página llega
  vacía más allá de la última, se pasa a la última que existe (DEC-121).

## Contenido público (WF-13, WF-14 y WF-15)

Contrato en [`docs/sad/api/ms-catalog.md`](../docs/sad/api/ms-catalog.md); decisiones DEC-70 a DEC-78 en `docs/trazabilidad.md`.

| Ruta | Pantalla | Acceso |
|---|---|---|
| `/preguntas-frecuentes` | WF-13 público (`contenido/PreguntasFrecuentesPage.tsx`), diferida | Pública; `GET /faq` sin token |
| `/terminos` (`#privacidad` = sección 4) | WF-15 (`contenido/terminos/TerminosPage.tsx`), diferida; el texto, en `terminos/textoTerminos.ts` | Pública, sin backend |
| `/admin/preguntas-frecuentes` | Gestión de WF-13 (`admin/AdministrarPreguntasPage.tsx`), diferida | Solo el Administrador |
| `/admin/avisos?pagina=` | Gestión de WF-14 (`admin/AdministrarAvisosPage.tsx`), diferida | Solo el Administrador |

- **Banner de avisos** (`contenido/BannerAvisos.tsx`): en `DisenoPrincipal`, debajo del encabezado; no va en las pantallas de acceso. Pide `GET /avisos` sin token; cerrarlo guarda los ids en `sessionStorage` (`samaria360.avisosCerrados`). La gestión lo actualiza con `avisarCambioAvisos()`.
- **RF47** (`contenido/filtroPreguntas.ts`): filtra la lista ya cargada con cada pulsación, sin tildes ni mayúsculas.
- Los contadores de 150, 2000 y 300 cuentan **puntos de código** sin los espacios de los extremos (`admin/textoAcotado.ts`), como ms-catalog.
- `admin` puede importar de `contenido`; `contenido` nunca importa de `admin`.
- En las pruebas, la guardia y `simularFetch` responden una lista vacía solo a `GET /api/v1/catalogo/avisos` exacta (DEC-78).

## Turista (WF-16, WF-17 y cambios en WF-05, WF-06 y WF-11)

Contratos en [`docs/sad/api/ms-auth.md`](../docs/sad/api/ms-auth.md) («Perfil del turista») y
[`docs/sad/api/ms-catalog.md`](../docs/sad/api/ms-catalog.md) («Favoritos» e «Historial de búsquedas»); decisiones DEC-92
a DEC-102 en `docs/trazabilidad.md`.

| Ruta | Pantalla | Acceso |
|---|---|---|
| `/perfil?seccion=` | WF-16 (`perfil/PerfilPage.tsx`), diferida | Privada, solo el Turista (`RutaProtegida rol="Turista"`) |
| `/perfil/favoritos` | WF-17 (`perfil/MisFavoritosPage.tsx`), diferida | Privada, solo el Turista |

- **Perfil (RF53):** el PUT reemplaza el perfil completo; cada pestaña envía su borrador con lo **guardado** de la otra y
  nunca el correo. Los campos, el país y las categorías son los del registro (`SelectorPais`, `CasillasCategorias`,
  `validarPerfil`). Después de guardar, `refrescarUsuario()` de la sesión vuelve a pedir `/me` para el nombre del
  encabezado.
- **Favoritos (RF54):** `favoritos/FavoritosProvider.tsx` guarda la lista de la sesión (una carga, diferida hasta el
  primer corazón o WF-17; si falla, el siguiente montaje reintenta). `BotonFavorito` es el corazón de WF-05 y WF-06; el
  del popup del mapa se arma con nodos en `MapaOferta`. Los anuncios van en `AvisoFavoritos`, una región por pantalla.
- **Historial (RF60):** `ExplorarPage` registra cada resultado nuevo de una búsqueda del turista (`POST /historial`, sin
  coordenadas ni página) y muestra «Últimas búsquedas» (`componentes/UltimasBusquedas.tsx`, `textoBusqueda.ts`). Repetir
  una búsqueda que usaba la ubicación la vuelve a pedir (`state.pedirUbicacion`).
- **Compartir (RF49):** `reservas/compartirReserva.ts` prueba la Web Share API, luego el portapapeles y, si no, devuelve
  `manual` para mostrar el enlace en un campo de solo lectura.
- **Pruebas:** `simularFetch` responde por defecto `GET /favoritos`, `GET /historial` (lista vacía) y `POST /historial`
  (204), con coincidencia exacta (DEC-101).

## Pendientes de pruebas

Las pruebas no hacen peticiones de red reales. Una guardia en `src/test/setup.ts` hace fallar cualquier prueba que
llame a `fetch` sin `simularFetch`.

El límite por defecto de Vitest es de 5 s por prueba. Las que tardaron **más de 2 s** en alguna ejecución tienen
15 s, con un comentario junto al `it` (el tercer argumento). Mediciones: máximo de 12 ejecuciones de `npm test`
con el stack de Docker detenido, en una PC Windows del equipo (octubre de 2026).

| Prueba | Máximo medido | Causa probable |
|---|---|---|
| `explorar.test.tsx` › los filtros arman la consulta a la API… | 4,8 s | Primera del archivo: paga la carga diferida de `/explorar` (Vitest transforma `ExplorarPage` dentro de la prueba). Además: tecleo, dos clics y «Aplicar», que vuelve a montar `FiltrosOferta` y repite la consulta. |
| `inicioSesion.test.tsx` › un login exitoso redirige al inicio… | 4,2 s | Primer render de la app en el archivo, 34 pulsaciones de `usuario.type` y navegación a `/` (monta `InicioPage` con su consulta de destacados). |
| `explorarDiferido.test.tsx` › mientras llega la página… | 3,8 s | Única del archivo: primer render de la app y transformación del módulo diferido. |
| `inicioSesion.test.tsx` › un 423 muestra la hora de desbloqueo… | 3,5 s | 33 pulsaciones de `usuario.type`. |
| `idioma.test.tsx` › traduce los textos y conserva el correo… | 3,2 s | Primer render de la app en el archivo, 23 pulsaciones y cambio de idioma (re-renderiza todo el árbol). |
| `idioma.test.tsx` › el mensaje de error mostrado también se traduce | 3,2 s | 33 pulsaciones, envío con `findBy` y cambio de idioma. |
| `rutaProtegida.test.tsx` › después de iniciar sesión vuelve a la ruta privada | 2,9 s | 34 pulsaciones de `usuario.type` y regreso a la ruta privada. |
| `explorarMapa.test.tsx` › no pide la ubicación al cargar la página | 2,7 s | Primera del archivo: paga la carga diferida de `/explorar`. |
| `inicio.test.tsx` › la búsqueda lleva a /explorar con el texto | 2,6 s | Primera del archivo: primer render de la app y 15 pulsaciones. |
| `inicioSesion.test.tsx` › un 401 muestra el mensaje de credenciales inválidas | 2,5 s | 33 pulsaciones de `usuario.type`. |
| `inicioSesion.test.tsx` › un 400 muestra el mensaje de datos inválidos | 2,5 s | 23 pulsaciones de `usuario.type`. |
| `inicioSesion.test.tsx` › un 403 muestra el mensaje de cuenta inactiva | 2,2 s | 34 pulsaciones de `usuario.type`. |

En la app no hay debounce; el único temporizador es el del vencimiento de la sesión, que solo corre con la sesión iniciada. El costo es del tecleo: cada pulsación de `usuario.type` espera un
`setTimeout(0)` de user-event (`delay: 0` por defecto) y re-renderiza el formulario. A eso se suma el primer render
y la carga diferida de la primera prueba de cada archivo. Las pruebas de `inicioSesion` que no son la primera, con el
mismo tecleo, tardan de 2,0 a 2,5 s: el tecleo pesa unos 2 s, y el primer render del archivo, entre 1 y 2 s.

**Ideas para la rama de optimización (no aplicadas):**
1. `userEvent.setup({ delay: null })` en `src/test/renderApp.tsx`: quita la espera entre pulsaciones.
2. `usuario.paste(...)` (con el campo enfocado) en los campos que la prueba no está evaluando, por ejemplo el correo
   y la contraseña cuando lo que se prueba es la respuesta del servidor. `usuario.type` se deja solo donde importa
   el tecleo: validación mientras se escribe, o conservar el valor al cambiar de idioma.

Después de optimizar, volver a medir y quitar los 15 s de las pruebas que bajen de 2 s.

---

## Guía visual

Los wireframes fijan la **estructura** (qué elementos hay y en qué región). El **diseño** es libre, pero coherente en todas las pantallas: toda pantalla nueva usa los tokens de `src/app/estilos.css` y no define colores, tamaños ni sombras propios.

**Identidad «Santa Marta»:** el mar Caribe (azules verdosos profundos), el atardecer de El Rodadero (coral y sol), la Sierra Nevada (verde y picos nevados), el café de la Sierra y la arena de la playa. Se busca una apariencia cálida y de póster de viaje, con mucho aire y formas redondeadas.

### Color

Siempre a través de los tokens `var(--color-…)`; nunca hex sueltos en los componentes.

| Token | Hex | Úsalo para | No lo uses para |
|---|---|---|---|
| `--color-tinta` | `#14272D` | Texto principal y títulos | — |
| `--color-tinta-suave` | `#4A5E63` | Subtítulos, ayudas, texto legal, correo en el menú | Texto sobre fondos oscuros |
| `--color-arena` | `#FBF6EE` | Fondo de la página | Tarjetas (usa superficie) |
| `--color-arena-200` | `#F3EADB` | Superficies tenues: avisos informativos | Fondo de la página |
| `--color-superficie` | `#FFFFFF` | Tarjetas, menús, campos; texto sobre mar-700, mar-900 y atardecer | — |
| `--color-borde` | `#E2D9CA` | Separadores y contornos decorativos de tarjetas | Borde de campos (no llega a 3:1) |
| `--color-borde-campo` | `#7C8D91` | Borde de campos de formulario | Texto |
| `--color-deshabilitado` | `#5C6D71` | Elementos deshabilitados («No disponible», DEC-22 y DEC-270), placeholder | Elementos activos |
| `--color-mar-900` | `#073B44` | Fondo del pie, avatar, títulos destacados | Botones de acción |
| `--color-mar-700` | `#0B6574` | Enlaces, pestaña e idioma activos, navegación activa, **foco** | Botón principal (es del atardecer) |
| `--color-mar-500` | `#1497A6` | **Solo decorativo**: olas, ilustraciones | Texto o bordes con significado |
| `--color-mar-100` | `#DCF1F2` | Hover de opciones, fondos suaves, halo del foco en campos | Texto |
| `--color-atardecer-600` | `#C2461F` | **Botón principal (CTA)**: una acción principal por pantalla; acentos de avisos | Texto largo, enlaces |
| `--color-atardecer-700` | `#A83A17` | Hover y estado activo del CTA | — |
| `--color-atardecer-500` | `#E2603A` | **Solo decorativo**: sol, degradados, anillo del avatar abierto | Texto |
| `--color-sol-400` | `#F4B740` | Anillo del avatar, **foco sobre fondos oscuros**, ilustraciones | Texto sobre fondos claros |
| `--color-sol-100` | `#FDF1D6` | Fondo de los avisos del Administrador, selección de texto | — |
| `--color-cafe-700` | `#6A3E1E` | Texto café sobre sol (avisos del Administrador) | — |
| `--color-sierra-700` | `#2E6A4E` | Ilustraciones; reservado para estados de éxito futuros | — |
| `--color-error` | `#B42318` | Mensajes y bordes de error | Decoración |
| `--color-error-fondo` | `#FDECEA` | Fondo del mensaje de error | — |
| `--color-pie-texto` | `#E8F3F3` | Títulos y texto del pie (sobre mar-900) | Fondos claros |
| `--color-pie-suave` | `#A9CED2` | Enlaces y derechos del pie (sobre mar-900) | Fondos claros |

**Contraste (WCAG 2.x AA).** Texto ≥ 4,5:1; bordes de campos, foco e iconos con significado ≥ 3:1. Los pares en uso están verificados en `src/app/tokens.test.ts`, que lee los tokens reales de `estilos.css`.

| Par | Ratio |
|---|---|
| tinta / arena · superficie | 14,37 · 15,47 |
| tinta-suave / arena · superficie · mar-100 · sol-100 | 6,35 · 6,84 · 5,83 · 6,10 |
| mar-900 / mar-100 · sol-100 (saludo del inicio) | 10,42 · 10,90 |
| atardecer-600 / arena-200 (icono y borde del aviso, no textual) | 4,20 |
| deshabilitado / superficie · arena | 5,41 · 5,03 |
| superficie / atardecer-600 · atardecer-700 (CTA) | 5,01 · 6,40 |
| superficie / mar-700 · mar-900 | 6,71 · 12,22 |
| mar-700 / superficie · arena · mar-100 | 6,71 · 6,24 · 5,73 |
| error / superficie · error-fondo | 6,57 · 5,75 |
| café-700 / sol-100 | 8,07 |
| pie-texto · pie-suave / mar-900 | 10,79 · 7,24 |
| borde-campo / superficie · arena (no textual) | 3,46 · 3,21 |
| sol-400 / mar-900 (foco en el pie) | 6,81 |

**Al agregar un color o un par nuevo:** añádelo como token en `:root` y agrega el par a `PARES` en `tokens.test.ts`. Si la prueba falla, el color no se usa.

### Tipografía

Fuentes empaquetadas con `@fontsource-variable` (licencia OFL-1.1), importadas en `src/main.tsx`; no se cargan desde internet.

| Token | Fuente | Uso |
|---|---|---|
| `--fuente-titulos` | **Fraunces** (serif variable, cálida, de póster de viaje) | h1, h2, logo, saludo, iniciales del avatar, nombre en el menú |
| `--fuente-texto` | **Figtree** (sans geométrica variable) | Todo lo demás: texto, campos, botones, navegación |

| Token | Tamaño | Uso típico |
|---|---|---|
| `--texto-xs` | 0,75 rem | Insignias, texto legal, derechos del pie |
| `--texto-sm` | 0,875 rem | Navegación, etiquetas, avisos, pie |
| `--texto-base` | 1 rem | Texto y botones |
| `--texto-lg` | 1,125 rem | Subtítulos, h2 de secciones pequeñas |
| `--texto-xl` | 1,375 rem | Título de tarjeta, logo |
| `--texto-2xl` | 1,75 rem | h2 de sección |
| `--texto-3xl` | `clamp(2rem, 4vw, 2.75rem)` | h1 de página |

Interlineado: `--interlineado` (1,55) para texto y `--interlineado-titulos` (1,15) para títulos. Pesos: 400 texto, 500–600 etiquetas y botones secundarios, 650 títulos y CTA.

### Espaciado

Escala con base de 4 px. Usa solo estos valores en `margin`, `padding` y `gap`.

| Token | Valor | Uso típico |
|---|---|---|
| `--espacio-1` | 0,25 rem | Ajustes finos, padding de insignias |
| `--espacio-2` | 0,5 rem | Entre icono y texto, entre etiqueta y campo |
| `--espacio-3` | 0,75 rem | Padding de avisos y de opciones de menú |
| `--espacio-4` | 1 rem | Separación entre campos; margen lateral en móvil |
| `--espacio-5` | 1,5 rem | Padding de tarjetas; separación entre bloques |
| `--espacio-6` | 2 rem | Separación entre secciones |
| `--espacio-7` | 3 rem | Padding de banners y del pie |
| `--espacio-8` | 4 rem | Separación entre columnas en escritorio |

### Radios

| Token | Valor | Uso |
|---|---|---|
| `--radio-sm` | 8 px | Campos, avisos, mensajes de error, opciones de menú |
| `--radio-md` | 14 px | Tarjetas y menús |
| `--radio-lg` | 24 px | Paneles ilustrados y banners |
| `--radio-pill` | 999 px | Botones, selector de idioma, insignias |

### Sombras

Con tinte del mar (`rgb(7 59 68 / …)`), nunca gris puro.

| Token | Uso |
|---|---|
| `--sombra-1` | Reposo: botón principal, marca del logo |
| `--sombra-2` | Tarjetas, portada del inicio, banners; hover del CTA |
| `--sombra-3` | Elementos flotantes: menú del avatar |

### Movimiento

- `--transicion` (160 ms, `ease-out`) para hover, foco y cambios de estado. Animaciones de entrada: `aparecer` (menús, mensajes) y `subir` (contenido de página).
- Movimiento sutil y funcional: elevar 1 px el CTA en hover, deslizar el subrayado de la navegación. Nada de animaciones en bucle salvo el indicador de carga.
- `prefers-reduced-motion: reduce` desactiva animaciones y transiciones (regla global en `estilos.css`).

### Foco

- `--foco`: `3px solid var(--color-mar-700)` con `outline-offset: 2px` mediante `:focus-visible`, para todos los elementos interactivos.
- Campos: borde mar-700, halo `mar-100` y `outline` de 2 px en `:focus-within` del contenedor.
- Sobre fondos oscuros (pie), se redefine `--foco` con `--color-sol-400`. Hay que redefinir `--foco` y no solo el color, porque las variables se resuelven donde se declaran.
- Nunca `outline: none` sin un reemplazo visible.

### Iconos e ilustraciones

- **Iconos** (`src/shared/components/Iconos.tsx`): de línea, cuadrícula de 24, trazo 1,75 redondeado y `currentColor`. Siempre `aria-hidden`: el nombre accesible lo da el texto o el `aria-label` del control.
- **Ilustraciones** (`src/shared/components/Ilustraciones.tsx`): SVG propios, sin fotos de terceros. Usan los tokens (`var(--color-…)`), así que siguen la paleta.
  - `MarcaSamaria`: sol poniéndose sobre olas; respaldo del logo (el logo es `LogoSamaria`, la imagen de `_entrada/imagenes/marca/`).
  - `Ola`: borde ondulado (`currentColor`) para pies y banners.
- Todo lo decorativo lleva `aria-hidden="true"` y `focusable="false"`, y no agrega texto. Si un elemento nuevo tiene **función**, se registra en `docs/trazabilidad.md` como «Mejora de diseño».

### Patrones de componente

| Patrón | Clases | Reglas |
|---|---|---|
| Botón principal | `.boton-principal` | Píldora atardecer-600, texto blanco, alto mínimo 48 px. Uno por pantalla. |
| Enlace o navegación | `.navegacion a` | Texto tinta; hover y activo en mar-700 con subrayado deslizante. |
| Campo | `.campo`, `.campo__control` | `<label for>` visible, icono `aria-hidden`, alto 48 px, borde `--color-borde-campo`; error con `aria-invalid`, `aria-describedby` y `.campo__error`. |
| Tarjeta | `.tarjeta`, `.tarjeta__titulo`, `.tarjeta__cuerpo` | Superficie, `--radio-md`, `--sombra-2`, título en Fraunces. |
| Aviso informativo | `.aviso-intentos` | arena-200 con borde izquierdo atardecer-600 e icono. |
| Mensaje de error | `.login__error` | Región `aria-live` siempre presente; el contenido es error sobre error-fondo con borde izquierdo. |
| Fuera de alcance (pantallas nuevas) | `BotonNoDisponible` + `.boton-no-disponible` (`shared/components/`, `estilos.css`) | Estado único de DEC-22: borde discontinuo `--color-borde-campo`, color deshabilitado, `aria-disabled` (sigue enfocable), sin acción y «No disponible» solo para lectores de pantalla. Nunca `disabled`. WF-06 y WF-12. |
| Fuera de alcance (todas las pantallas) | `BotonNoDisponible` (botón con borde discontinuo) y `AccionNoDisponible` (texto con subrayado discontinuo) | `aria-disabled` (sigue siendo enfocable), sin acción y «No disponible» solo para lectores de pantalla (DEC-22, DEC-270). |
| Carga | `.estado-carga` | `role="status"` y `aria-busy`, con indicador giratorio decorativo. |
| Botón secundario | `.boton-secundario` (en `estilos.css`, como `.boton-principal`) | Píldora con borde mar-700 sobre superficie: acciones de apoyo (Reintentar, Quitar filtros). |
| Chip de filtro | `.chip` | mar-900 sobre mar-100, removible; `aria-label` que diga qué filtro quita. |
| Pestañas de acceso | `PestanasAcceso` (`.pestanas`) | Enlaces «Iniciar sesión» / «Registrarse» entre `/login` y `/registro`, con `aria-current="page"` y barra mar-700; no un `tablist`, porque cambian de página. |
| Formulario de registro | `features/auth/registro/` (`Campo`, `.registro__*`) | Mismo patrón que el login (`.campo`, `.tarjeta`); errores por campo con `aria-invalid` y `aria-describedby`, foco en el primer error y resumen en `role="alert"`. Los textos de error salen de los códigos de `campos` (`registro.errores.<campo>.<código>` o `registro.errores.<código>`). |
| Panel desplegable | `.mas-filtros__boton`, `.mas-filtros__panel` | Botón secundario con `aria-expanded` (relleno mar-700 al abrir) y contador superficie sobre atardecer-600; panel con `hidden` cuando está cerrado, foco al primer control al abrir y de vuelta al botón con Escape o «Cerrar». |
| Aviso de sesión | `.aviso-sesion` | tinta sobre sol-100 con borde sol-400, `role="status"` y botón para cerrarlo. |
| Tarjeta de oferta | `.tarjeta-oferta` (`features/catalogo/catalogo.css`) | Imagen con `alt=""` y respaldo por categoría, nombre h3, datos en `<dl>`; «—» con texto «sin datos» para lectores. |
| Insignia de estado | `.insignia-estado--<EstadoOperativo>` | Abierto: mar-700/mar-100 · Cerrado temporalmente: error/error-fondo · En mantenimiento: café-700/sol-100. |
| Estado de búsqueda | `EstadoCatalogo` | Carga, vacío, fuera de rango y errores (503, 400, red); el error solo afecta a su región. |
| Marcador del mapa | `.marcador--servicio`, `--atractivo`, `--ubicacion` | `L.divIcon` con SVG propio que toma el color de la clase: pin atardecer-600 (servicio) o mar-700 (atractivo) con borde blanco; «Tu ubicación» es un círculo sol-400 con borde mar-900. Enfocable, con `title` y `aria-label`. |
| Ventana emergente del mapa | `.popup-oferta`, `__miniatura`, `__nombre`, `__enlace` | Miniatura de 7,5 × 5 rem (120 × 80, `object-fit: cover`, fondo arena-200 mientras carga), nombre en negrita y, en los servicios, «Ver detalle» en mar-700 subrayado. Se arma con nodos y `textContent` al abrirse, nunca con HTML de la API. |
| Resaltado marcador↔tarjeta | `.marcador--resaltado`, `.tarjeta-oferta--resaltada` | Marcador 1,3× y al frente; tarjeta con anillo mar-700 y `--sombra-2`. |
| Selector de vista | `.vista-selector` | `role="group"` con dos botones `aria-pressed` (mar-700); solo por debajo de 64 rem. |
| Ficha de detalle (WF-06) | `.detalle__ficha` (`features/catalogo/detalle/detalle.css`) | Superficie con `--sombra-1`; categoría en café-700 versal, h1 mar-900, precio «desde» en atardecer-700; galería 2:1 desde 64 rem; unidades en tarjetas arena. |
| Grupo con borde | `.reserva__grupo` (`features/reservas/reservas.css`) | `fieldset` con `legend` en Fraunces mar-900: «Buscar disponibilidad», «Elige la habitación», «Horario», filtros de WF-11. |
| Tarjeta de unidad | `.tarjeta-unidad` | Imagen y cuerpo con el radio (la etiqueta es el nombre); elegida: borde mar-700 con anillo mar-100; sin cupo: fondo arena y el motivo en error. |
| Opción de horario | `.opcion-horario` | Radio con «08:00 · quedan N cupos»; elegida: mar-100; agotada: arena y «Agotado» en error. |
| Resumen de reserva | `.resumen-reserva` | Superficie con borde superior atardecer-600 y `--sombra-2`; fijo (`sticky`) desde 64 rem; total en mar-900; aviso de RF44 mar-900 sobre mar-100. |
| Botón principal bloqueado | `.boton-principal[aria-disabled='true']` | tinta-suave sobre arena con borde: sigue enfocable y su motivo va en `aria-describedby`. |
| Aviso de un intento | `.aviso-reserva` | error sobre error-fondo con borde izquierdo; `role="alert"` y recibe el foco. |
| Tarjeta de reserva | `.tarjeta-reserva--<Estado>` | Borde izquierdo mar-700 (Confirmada), error (Cancelada) o café-700 (Completada). |
| Insignia de reserva | `.insignia-reserva--<Estado>` | Icono y texto: Confirmada mar-700/mar-100 · Cancelada error/error-fondo · Completada café-700/sol-100 (los pares de `.insignia-estado`). |
| Diálogo | `.dialogo` (`<dialog>` con `showModal()`, en `estilos.css`) | Superficie con `--sombra-3` y fondo mar-900 al 55 %; el foco inicial va a la opción que no destruye nada (o al campo de texto, en el rechazo de WF-12) y vuelve al botón que lo abrió. WF-11 y WF-12. |
| Diálogo de cambio de rol | `DialogoCambioRol`, `.dialogo-cuentas__roles`, `.dialogo-cuentas__advertencia` (`features/admin/`) | Rol nuevo con radios en un `fieldset` (acento mar-700) al asignar; advertencia de DEC-112 café-700 sobre sol-100 con borde izquierdo. Foco inicial en el radio (asignar) o en «Cancelar» (modificar y revocar). WF-12. |
| Motivo de una acción no disponible | `BotonNoDisponible` con `descripcion` + `.motivo-no-disponible` | Texto tinta-suave, pequeño, junto al botón y enlazado con `aria-describedby` («La cuenta está desactivada», «Sin datos de turista»). WF-12 (DEC-122). |
| Pestañas de una página | `.pestanas-cuentas` (`features/admin/admin.css`) | `tablist` WAI-ARIA: botones `role="tab"` con `aria-selected`, tabindex itinerante y ← → Inicio Fin; pestaña activa en mar-700 con barra inferior. Distintas de `PestanasAcceso`, que cambian de página. |
| Tabla adaptable | `TablaCuentas`, `.tabla-cuentas` | Superficie con encabezado arena. Por debajo de 40 rem, cada fila es una tarjeta y cada celda muestra su columna (`data-etiqueta`). Los roles ARIA explícitos conservan la semántica de tabla. |
| Insignia de cuenta | `.insignia-cuenta--<Estado>` | Pendiente café-700/sol-100 · Activo mar-700/mar-100 · Rechazado error/error-fondo · Inactivo y Desactivado tinta-suave sobre superficie con borde discontinuo. |
| Aviso de la región de estado | `.aviso-cuentas` | mar-900 sobre mar-100 con borde izquierdo mar-700, dentro de una región `aria-live` siempre presente (éxito, 409, 404). |
| Sin permiso | `SinPermiso`, `.sin-permiso` | h1 y mensaje `role="alert"`; no cierra la sesión. |
| Aviso de ubicación | `.aviso-ubicacion` | `role="status"` siempre presente; mar-900 sobre mar-100 con borde izquierdo mar-700 cuando tiene texto. |
| Paginación | `Paginacion`, `.paginacion` (en `estilos.css` desde N12, DEC-77) | Botones con borde de campo sobre superficie; la página actual, mar-700 con texto superficie. WF-05, WF-11, WF-12 y WF-14. |
| Banner de avisos | `BannerAvisos`, `.banner-avisos` (`features/contenido/contenido.css`) | Superficie con borde izquierdo atardecer-600, debajo del encabezado; `section` con título «Avisos del municipio»; avisos apilados, sin rotación; «Cerrar avisos» es un botón secundario. |
| Insignia de aviso | `.insignia-aviso--<Tipo>` | Texto traducido y color: Clima mar-700/mar-100 · Cierre error/error-fondo · Comunicado café-700/sol-100. Banner y tabla de WF-14. |
| Acordeón | `.acordeon-faq` | Tarjetas en superficie; `h2 > button[aria-expanded][aria-controls]` con «+»/«−» en un círculo mar-100; abierto o en hover, mar-700. Se pueden abrir varias. |
| Buscador en vivo | `.preguntas-frecuentes__buscador` + región `status` | Campo `type="search"` con su ayuda; la región anuncia «Se muestran n de N preguntas» o «sin coincidencias» con «Borrar búsqueda». |
| Página de texto legal | `.terminos`, `__nota`, `__indice`, `__seccion` | Nota café-700 sobre sol-100; índice en superficie con enlaces mar-700; cada sección con `scroll-margin-top` para que el encabezado fijo no la tape. |
| Corazón de favoritos | `BotonFavorito`, `.boton-favorito` (`features/favoritos/favoritos.css`); `.popup-oferta__favorito` en el popup | Píldora en superficie con borde de campo e icono atardecer-700; guardado: icono relleno y borde atardecer-700 (la forma cambia, no solo el color). `aria-pressed` y el nombre del elemento en el nombre accesible; sin la lista, borde discontinuo y `aria-disabled`. Con `conTexto` (WF-06), «Guardar en favoritos» visible. |
| Anuncio de favoritos | `AvisoFavoritos`, `.aviso-favoritos` | Región `role="status"` siempre presente; mar-700 (éxito) o error. Una por pantalla. |
| Últimas búsquedas | `.ultimas-busquedas` (`features/catalogo/catalogo.css`) | Título tinta-suave en versalitas y enlaces con el estilo de `.chip`; la fecha va como descripción accesible. |
| Tarjeta de favorito | `.tarjeta-favorito`, `.insignia-favorito--disponible` / `--no-disponible` (`features/perfil/perfil.css`) | Imagen 3:2, nombre, categoría e insignia con icono y texto (mar-900/mar-100 o error/error-fondo); acciones a la derecha desde 40 rem. |
| Pestañas del perfil | `.pestanas-perfil` (`features/perfil/perfil.css`) | El mismo patrón `tablist` que `.pestanas-cuentas`, sin depender de `admin.css`. |
| Formulario en línea | `.formulario-aviso` | Superficie con borde superior atardecer-600; contador a la derecha, en error al pasarse; acción principal `aria-disabled` en arena con tinta-suave. |
| Formulario por secciones (WF-08) | `.prestador__seccion`, `CampoServicio` (`features/prestador/prestador.css`) | Una `.tarjeta` por sección con h2 en Fraunces mar-900; el `id` de cada control sale de la ruta del contrato (`servicio-detalleCategoria-unidades-1-precioNoche`), y su error y su contador (tinta-suave, en error al pasarse) van en `aria-describedby`. Lo que no tiene contrato: `.prestador__no-disponible`, borde discontinuo y «—» (DEC-22). |
| Editor de filas | `.prestador__filas`, `.prestador__unidad`, `.prestador__fila--salida` | Lista ordenada de `fieldset` (fondo arena, borde, `legend` mar-900 «Unidad N») o de filas en línea (salidas); «Quitar» deshabilitado con una sola fila y «Agregar» con el tope; al quitar, el foco va a la fila anterior. |
| Selector de ubicación | `SelectorUbicacion`, `.prestador__mapa` (sobre `.mapa-oferta`) | Mapa de 18 rem con la base de `shared/mapa/mapaBase.ts` (teselas y atribución de OSM, zoom máximo 19) y el pin `.marcador--servicio` arrastrable; «Latitud» y «Longitud» visibles debajo, sincronizadas, y «Usar el centro de Santa Marta» como alternativa sin ratón. |
| Tabla de «Mis servicios» | `.prestador__tabla` | Encabezado arena; por debajo de 40 rem, una tarjeta por fila y cada celda con su columna (`data-etiqueta`), con roles ARIA explícitos como `.tabla-cuentas`. |
| Insignia de servicio | `.insignia-servicio--<estado>` | Texto siempre: Pendiente de aprobación café-700/sol-100 · Publicado mar-900/mar-100 · Rechazado error/error-fondo. WF-18. |

### Maquetación y responsivo

- Contenido centrado con `--ancho-maximo` (76 rem) y márgenes laterales de `--espacio-4`.
- Funciona desde **360 px** sin scroll horizontal. En menos de 640 px (`40rem`), la navegación pasa a una segunda fila desplazable.
- Pantallas de acceso (WF-01, `app/DisenoAcceso.tsx`): encabezado reducido (logo y ES / EN) y pie reducido en una fila. Login sin imagen: subtítulo, pestañas e introducción a la izquierda (máx. 44 rem); la tarjeta, centrada (máx. 34 rem). El `h1` es solo para lectores de pantalla.
- «Más filtros» (WF-05): el botón va en la fila de filtros y el panel ocupa una fila completa debajo (borde izquierdo mar-700 sobre arena), así «Aplicar filtros» queda después del panel abierto. Por debajo de 640 px, categorías y campos en una sola columna.
- WF-05: desde 1024 px (`64rem`), mapa y lista lado a lado; por debajo, el selector «Lista / Mapa» muestra uno a la vez. El mapa ocupa el 100 % de su columna, sin ancho fijo.

### Lista de verificación para cada pantalla nueva

1. Solo tokens: ningún hex, tamaño o sombra sueltos.
2. Los pares de color nuevos están en `tokens.test.ts` y pasan AA.
3. Todo control tiene nombre accesible; todo campo, su `<label>`; los errores se anuncian con `aria-live`.
4. Recorrido completo con teclado y foco visible en cada elemento.
5. Decoración con `aria-hidden`; sin fotos sin licencia.
6. Sin scroll horizontal a 360 px; animaciones respetan `prefers-reduced-motion`.
7. Los elementos del wireframe fuera de alcance usan el estado único de DEC-22 (`BotonNoDisponible`, o «—» con «No disponible» para lectores de pantalla) y figuran como pendientes en `docs/trazabilidad.md`.
