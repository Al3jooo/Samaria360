// Prueba de carga de RNF02 (respuesta en menos de 2 s) y RNF03 (500 usuarios concurrentes).
// Criterios de aceptación (decisión del equipo; el SRS no fija percentil ni carga): p95 < 2 s en cada endpoint
// y menos del 1 % de errores, con 500 usuarios virtuales sostenidos 5 minutos.
//
// Solo endpoints públicos, sin token y sin teselas de OpenStreetMap. Cada usuario virtual repite el recorrido de
// un turista anónimo: busca (WF-05), pide la disponibilidad de las tarjetas (resumen), abre un detalle (WF-06) y
// consulta la disponibilidad de un tour o un alojamiento (WF-10), con 1 a 3 s de pausa entre acciones.
//
// Uso (ver README.md de esta carpeta):
//   docker run --rm -i --network sm_tours_aplication_default -e BASE=http://frontend:8080 \
//     -v "$PWD/infra/carga:/carga" grafana/k6:1.3.0 run /carga/rnf02-rnf03.js
// Variables: BASE (por defecto http://frontend:8080), PERFIL = carga (por defecto) | base (1 usuario, 1 minuto)
// | demo (500 usuarios, 2 minutos sostenidos; contra la demo pública solo con aviso al equipo).
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://frontend:8080';
const PERFIL = __ENV.PERFIL || 'carga';

const escenarios = {
  carga: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: 500 },  // rampa
      { duration: '5m', target: 500 },  // sostenido (RNF03)
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '10s',
  },
  base: { executor: 'constant-vus', vus: 1, duration: '1m' },
  // Corrida corta contra la demo pública (VM de 1 OCPU): mismos 500 usuarios, menos tiempo sostenido.
  demo: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: 500 },
      { duration: '2m', target: 500 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '10s',
  },
};

const UMBRAL = ['p(95)<2000'];
export const options = {
  scenarios: { turista: escenarios[PERFIL] },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:oferta}': UMBRAL,
    'http_req_duration{endpoint:resumen}': UMBRAL,
    'http_req_duration{endpoint:detalle}': UMBRAL,
    'http_req_duration{endpoint:disponibilidad}': UMBRAL,
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Criterios de búsqueda variados, los mismos que ofrece WF-05 (texto, categoría, ubicación, orden, precio).
const BUSQUEDAS = [
  'tamano=10',
  'texto=playa&tamano=10',
  'texto=sierra&tamano=10',
  'categoria=Tour&tamano=10',
  'categoria=Alojamiento&huespedes=2&tamano=10',
  'categoria=Gastronomia&tamano=10',
  'lat=11.2408&lng=-74.1990&distanciaKm=20&orden=Distancia&tamano=10',
  'precioMax=200000&orden=PrecioAscendente&tamano=10',
  'tamano=10&pagina=1',
];

function ids(cuerpo) {
  return (cuerpo.elementos || []).filter((e) => e.tipo === 'Servicio').map((e) => e.id);
}

function fechaBogota(diasDespues) {
  const d = new Date(Date.now() - 5 * 3600 * 1000 + diasDespues * 86400 * 1000);
  return d.toISOString().slice(0, 10);
}

function pausa() {
  sleep(1 + Math.random() * 2);
}

function elegir(lista) {
  return lista[Math.floor(Math.random() * lista.length)];
}

// Se ejecuta una vez: toma los ids reales de la semilla y calienta la materialización del inventario,
// para que la medición no incluya el primer INSERT de cada servicio (es una sola vez por ventana de 60 días).
export function setup() {
  const todos = ids(http.get(`${BASE}/api/v1/catalogo/oferta?tamano=10`).json())
    .concat(ids(http.get(`${BASE}/api/v1/catalogo/oferta?tamano=10&pagina=1`).json()));
  const reservables = ['Tour', 'Alojamiento']
    .flatMap((c) => ids(http.get(`${BASE}/api/v1/catalogo/oferta?categoria=${c}&tamano=10`).json()));
  if (todos.length === 0 || reservables.length === 0) {
    throw new Error(`Sin datos en ${BASE}: ¿la semilla está cargada (MONGO_SEMILLA=true)?`);
  }
  for (const id of reservables) {
    http.get(`${BASE}/api/v1/reservas/disponibilidad?servicio=${id}&desde=${fechaBogota(0)}&hasta=${fechaBogota(30)}`);
  }
  return { todos, reservables };
}

export default function (datos) {
  let pagina = [];
  group('buscar oferta', () => {
    const r = http.get(`${BASE}/api/v1/catalogo/oferta?${elegir(BUSQUEDAS)}`, { tags: { endpoint: 'oferta' } });
    check(r, { 'oferta 200': (x) => x.status === 200 });
    if (r.status === 200) pagina = ids(r.json());
  });
  if (pagina.length > 0) {
    const r = http.get(
      `${BASE}/api/v1/reservas/disponibilidad/resumen?servicios=${pagina.join(',')}`,
      { tags: { endpoint: 'resumen' } });
    check(r, { 'resumen 200': (x) => x.status === 200 });
  }
  pausa();

  group('consultar detalle', () => {
    const r = http.get(`${BASE}/api/v1/catalogo/servicios/${elegir(datos.todos)}`, { tags: { endpoint: 'detalle' } });
    check(r, { 'detalle 200': (x) => x.status === 200 });
  });
  pausa();

  group('consultar disponibilidad', () => {
    const r = http.get(
      `${BASE}/api/v1/reservas/disponibilidad?servicio=${elegir(datos.reservables)}`
        + `&desde=${fechaBogota(1)}&hasta=${fechaBogota(14)}`,
      { tags: { endpoint: 'disponibilidad' } });
    check(r, { 'disponibilidad 200': (x) => x.status === 200 });
  });
  pausa();
}
