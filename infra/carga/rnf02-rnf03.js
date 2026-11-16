// Prueba de carga de RNF02 (respuesta en menos de 2 s) y RNF03 (500 usuarios concurrentes).
// Criterios de aceptación (decisión del equipo; el SRS no fija percentil ni carga): p95 < 2 s en cada endpoint
// y menos del 1 % de errores, con 500 usuarios virtuales sostenidos 5 minutos.
//
// Solo endpoints públicos, sin token y sin teselas de OpenStreetMap. Cada usuario virtual repite el recorrido de
// un turista anónimo con el sistema completo del plan del 100 % (DEC-325): carga los avisos del banner (WF-14), busca
// (WF-05), pide la disponibilidad de las tarjetas (resumen), abre la ficha de un servicio con sus calificaciones
// (WF-06, ms-analytics), consulta la disponibilidad de un tour o un alojamiento (WF-10) y abre la ficha de un
// atractivo con su afluencia y sus alternativos (RF39, RF51), con 1 a 3 s de pausa entre acciones.
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
  // Corrida corta contra la demo pública: mismos 500 usuarios, menos tiempo sostenido.
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
const ENDPOINTS = ['avisos', 'oferta', 'resumen', 'detalle', 'calificaciones', 'disponibilidad', 'atractivo',
  'afluencia'];
export const options = {
  scenarios: { turista: escenarios[PERFIL] },
  thresholds: Object.assign(
    { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] },
    ...ENDPOINTS.map((e) => ({ [`http_req_duration{endpoint:${e}}`]: UMBRAL }))),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Criterios de búsqueda variados, los mismos que ofrece WF-05 (texto, categoría, ubicación, orden, precio,
// calificación y accesibilidad).
const BUSQUEDAS = [
  'tamano=10',
  'texto=playa&tamano=10',
  'texto=sierra&tamano=10',
  'categoria=Tour&tamano=10',
  'categoria=Alojamiento&huespedes=2&tamano=10',
  'categoria=Gastronomia&tamano=10',
  'lat=11.2408&lng=-74.1990&distanciaKm=20&orden=Distancia&tamano=10',
  'precioMax=200000&orden=PrecioAscendente&tamano=10',
  'orden=Calificacion&tamano=10',
  'accesibilidad=MovilidadReducida&tamano=10',
  'tamano=10&pagina=1',
];

function porTipo(cuerpo, tipo) {
  return (cuerpo.elementos || []).filter((e) => e.tipo === tipo).map((e) => e.id);
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

function get(ruta, endpoint) {
  const r = http.get(`${BASE}${ruta}`, { tags: { endpoint } });
  check(r, { [`${endpoint} 200`]: (x) => x.status === 200 });
  return r;
}

// Se ejecuta una vez: toma los ids reales de la semilla y calienta la materialización del inventario,
// para que la medición no incluya el primer INSERT de cada servicio (es una sola vez por ventana de 60 días).
export function setup() {
  const paginas = [0, 1].map((p) => http.get(`${BASE}/api/v1/catalogo/oferta?tamano=10&pagina=${p}`).json());
  const servicios = paginas.flatMap((c) => porTipo(c, 'Servicio'));
  const atractivos = paginas.flatMap((c) => porTipo(c, 'Atractivo'));
  const reservables = ['Tour', 'Alojamiento']
    .flatMap((c) => porTipo(http.get(`${BASE}/api/v1/catalogo/oferta?categoria=${c}&tamano=10`).json(), 'Servicio'));
  if (servicios.length === 0 || reservables.length === 0 || atractivos.length === 0) {
    throw new Error(`Sin datos en ${BASE}: ¿la semilla está cargada (MONGO_SEMILLA=true)?`);
  }
  for (const id of reservables) {
    http.get(`${BASE}/api/v1/reservas/disponibilidad?servicio=${id}&desde=${fechaBogota(0)}&hasta=${fechaBogota(30)}`);
  }
  return { servicios, atractivos, reservables };
}

export default function (datos) {
  let pagina = [];
  group('buscar oferta', () => {
    get('/api/v1/catalogo/avisos', 'avisos');
    const r = get(`/api/v1/catalogo/oferta?${elegir(BUSQUEDAS)}`, 'oferta');
    if (r.status === 200) pagina = porTipo(r.json(), 'Servicio');
    if (pagina.length > 0) {
      get(`/api/v1/reservas/disponibilidad/resumen?servicios=${pagina.join(',')}`, 'resumen');
    }
  });
  pausa();

  group('consultar ficha de un servicio', () => {
    const id = elegir(datos.servicios);
    get(`/api/v1/catalogo/servicios/${id}`, 'detalle');
    get(`/api/v1/analytics/calificaciones?tipo=servicio&id=${id}`, 'calificaciones');
  });
  pausa();

  group('consultar disponibilidad', () => {
    get(`/api/v1/reservas/disponibilidad?servicio=${elegir(datos.reservables)}`
      + `&desde=${fechaBogota(1)}&hasta=${fechaBogota(14)}`, 'disponibilidad');
  });
  pausa();

  group('consultar ficha de un atractivo', () => {
    const id = elegir(datos.atractivos);
    get(`/api/v1/catalogo/atractivos/${id}`, 'atractivo');
    get(`/api/v1/catalogo/atractivos/${id}/afluencia`, 'afluencia');
    get(`/api/v1/analytics/calificaciones?tipo=atractivo&id=${id}`, 'calificaciones');
  });
  pausa();
}
