import { vi } from 'vitest'

export interface SolicitudSimulada {
  url: string
  metodo: string
  cabeceras: Record<string, string>
  cuerpo: unknown
  /** El cuerpo tal como salió (p. ej. para ver un decimal exacto que JSON.parse convierte en número). */
  cuerpoTexto: string | undefined
  /** El cuerpo multipart (registro de atractivos, DEC-230), tal como salió. */
  formulario?: FormData
}

type Manejador = (solicitud: SolicitudSimulada) => Response | Promise<Response>

export const USUARIO_TURISTA = {
  idUsuario: '00000000-0000-4000-8000-000000000001',
  correo: 'turista@samaria360.test',
  rol: 'Turista',
  estadoCuenta: 'Activo',
  nombreVisible: 'Ana Pérez',
}

export const SESION_TURISTA = {
  token: 'token-de-prueba',
  tipo: 'Bearer',
  expiraEn: '2026-10-11T05:00:00Z',
  idUsuario: USUARIO_TURISTA.idUsuario,
  rol: 'Turista',
}

export function respuestaJson(status: number, cuerpo: unknown): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** ProblemDetail (RFC 9457) como los que responde ms-auth. */
export function respuestaProblema(status: number, codigo: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ type: 'about:blank', status, detail: codigo, codigo, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/** Página de error HTML, como la que genera nginx en un 502 o 504 (sin ProblemDetail). */
export function respuestaHtml(status: number): Response {
  return new Response(`<html><body><h1>${status}</h1><hr><center>nginx</center></body></html>`, {
    status,
    headers: { 'Content-Type': 'text/html' },
  })
}

/** Avisos del banner común (RF40, DEC-72): solo `GET /api/v1/catalogo/avisos`, exacta y sin consulta (DEC-78). */
export const RUTA_AVISOS_BANNER = '/api/v1/catalogo/avisos'

export function esAvisosDelBanner(url: string, metodo: string | undefined): boolean {
  return (metodo ?? 'GET') === 'GET' && url === RUTA_AVISOS_BANNER
}

/** Sin avisos activos: el banner no se muestra y la pantalla queda como antes. */
export function respuestaAvisosVacia(): Response {
  return respuestaJson(200, { elementos: [] })
}

/**
 * Llamadas del turista que salen solas en WF-05 y WF-06 (DEC-101, como DEC-78): «MÉTODO ruta», exactas y sin consulta.
 * Si la prueba no las simula, `simularFetch` responde lista vacía (los GET) o 204 (el POST); quedan registradas. Las
 * pruebas que verifican su contenido simulan su propia respuesta.
 */
const RESPUESTAS_TURISTA: Record<string, () => Response> = {
  'GET /api/v1/catalogo/favoritos': respuestaAvisosVacia,
  'GET /api/v1/catalogo/historial': respuestaAvisosVacia,
  'POST /api/v1/catalogo/historial': () => new Response(null, { status: 204 }),
  // Campana de RF63 (DEC-294): sin notificaciones, salvo que la prueba simule las suyas.
  'GET /api/v1/analytics/notificaciones': () =>
    respuestaJson(200, { elementos: [], pagina: 0, tamano: 20, totalElementos: 0, noLeidas: 0 }),
  // Calificaciones de WF-06 (RF17, DEC-300): sin calificaciones y sin poder calificar, salvo que la prueba simule otras.
  'GET /api/v1/analytics/calificaciones': () =>
    respuestaJson(200, { promedio: null, cantidad: 0, elementos: [], pagina: 0, tamano: 10, totalElementos: 0 }),
  'GET /api/v1/analytics/calificaciones/elegibilidad': () =>
    respuestaJson(200, { puedeCalificar: false, motivo: 'SIN_RESERVA_COMPLETADA', propia: null }),
}

export function respuestaTuristaPorDefecto(url: string, metodo: string): (() => Response) | undefined {
  return RESPUESTAS_TURISTA[`${metodo} ${url}`] ?? RESPUESTAS_TURISTA[`${metodo} ${url.split('?')[0]}`]
}

/**
 * Reemplaza fetch por rutas "MÉTODO /ruta" (con o sin la consulta). Devuelve el registro de solicitudes para verificar, por
 * ejemplo, la cabecera Authorization. Si la prueba no simula `GET /api/v1/catalogo/avisos`, responde una lista vacía
 * (DEC-78); tampoco hace falta simular los favoritos ni el historial del turista (DEC-101). Las solicitudes igual
 * quedan registradas.
 */
export function simularFetch(rutas: Record<string, Manejador>) {
  const solicitudes: SolicitudSimulada[] = []
  const fetchSimulado = vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const solicitud: SolicitudSimulada = {
      url: String(entrada),
      metodo: init?.method ?? 'GET',
      cabeceras: { ...(init?.headers as Record<string, string> | undefined) },
      cuerpo: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
      cuerpoTexto: typeof init?.body === 'string' ? init.body : undefined,
      formulario: init?.body instanceof FormData ? init.body : undefined,
    }
    // Multipart con una parte `datos` en JSON (atractivos, DEC-230; servicios, DEC-318): su contenido, en cuerpo.
    const datos = solicitud.formulario?.get('datos')
    if (datos instanceof Blob) {
      solicitud.cuerpoTexto = await new Promise<string>((resolver) => {
        const lector = new FileReader()
        lector.onload = () => resolver(String(lector.result))
        lector.readAsText(datos)
      })
      solicitud.cuerpo = JSON.parse(solicitud.cuerpoTexto)
    }
    solicitudes.push(solicitud)
    // Primero la URL exacta; si no, la ruta sin la consulta (el manejador recibe la URL completa).
    const manejador =
      rutas[`${solicitud.metodo} ${solicitud.url}`] ??
      rutas[`${solicitud.metodo} ${solicitud.url.split('?')[0]}`] ??
      (esAvisosDelBanner(solicitud.url, solicitud.metodo) ? respuestaAvisosVacia : undefined) ??
      respuestaTuristaPorDefecto(solicitud.url, solicitud.metodo)
    if (!manejador) {
      throw new Error(`fetch sin simulación: ${solicitud.metodo} ${solicitud.url}`)
    }
    return manejador(solicitud)
  })
  vi.stubGlobal('fetch', fetchSimulado)
  return { fetchSimulado, solicitudes }
}

// ------------------------------------------------------------------ catálogo (GET /api/v1/catalogo/oferta)

export const HOTEL = {
  tipo: 'Servicio',
  id: 's-hotel',
  nombre: 'Hotel Casa Tayrona',
  categoria: 'Alojamiento',
  descripcionCorta: 'Alojamiento boutique cerca del Parque de los Novios.',
  precio: 150000,
  imagen: '/imagenes/catalogo/hotel-casa-tayrona.webp',
  coordenada: { longitud: -74.2125, latitud: 11.2425 },
  distanciaKm: null,
  accesibilidad: ['MovilidadReducida'],
  estadoOperativo: null,
}

export const TOUR_CAFE = {
  ...HOTEL,
  id: 's-tour-cafe',
  nombre: 'Tour de Café en Minca',
  categoria: 'Tour',
  descripcionCorta: 'Recorrido guiado por fincas cafeteras.',
  precio: 80000,
  accesibilidad: ['Auditiva'],
}

export const CASCADA_CERRADA = {
  tipo: 'Atractivo',
  id: 'a-cascada',
  nombre: 'Cascada El Salto',
  categoria: 'Naturaleza',
  descripcionCorta: 'Sendero hasta una caída de agua cerca de Minca.',
  precio: null,
  imagen: '/imagenes/catalogo/cascada-el-salto.webp',
  coordenada: { longitud: -74.11, latitud: 11.15 },
  distanciaKm: null,
  accesibilidad: [],
  estadoOperativo: 'Cerrado_temporalmente',
}

export function paginaOferta(
  elementos: unknown[],
  { pagina = 0, tamano = 10, totalElementos = elementos.length }: { pagina?: number; tamano?: number; totalElementos?: number } = {},
) {
  return { elementos, pagina, tamano, totalElementos, totalPaginas: Math.ceil(totalElementos / tamano) }
}

/** Consulta (sin el prefijo de la ruta) de la última petición al catálogo. */
export function ultimaConsultaCatalogo(solicitudes: SolicitudSimulada[]): string {
  const catalogo = solicitudes.filter((s) => s.url.startsWith('/api/v1/catalogo/oferta'))
  return catalogo.length === 0 ? '' : (catalogo[catalogo.length - 1].url.split('?')[1] ?? '')
}

// ------------------------------------------------------------------ detalle (GET /api/v1/catalogo/servicios/{id})

export const USUARIO_PRESTADOR = {
  ...USUARIO_TURISTA,
  idUsuario: '00000000-0000-4000-8000-000000000002',
  correo: 'prestador@samaria360.test',
  rol: 'Prestador',
  nombreVisible: 'Casa Tayrona SAS',
}

/** Dos unidades: «Doble» con el precio como número y «Suite familiar» como texto decimal con centavos. */
export const DETALLE_HOTEL = {
  id: 's-hotel',
  idPrestador: '00000000-0000-4000-8000-000000000002',
  nombreComercial: 'Hotel Casa Tayrona',
  categoria: 'Alojamiento',
  descripcion: 'Alojamiento boutique cerca del Parque de los Novios.',
  imagenes: ['/imagenes/catalogo/hotel-casa-tayrona.webp', '/imagenes/catalogo/hotel-casa-tayrona-2.webp'],
  precioBase: 150000,
  moneda: 'COP',
  detalleCategoria: {
    serviciosIncluidos: ['WiFi', 'Desayuno'],
    unidades: [
      {
        idUnidad: 'u-doble',
        nombre: 'Doble',
        tipoHabitacion: 'Doble',
        numHabitaciones: 3,
        capacidadMaxHuespedes: 2,
        precioNoche: 150000,
      },
      {
        idUnidad: 'u-suite',
        nombre: 'Suite familiar',
        tipoHabitacion: 'Suite',
        numHabitaciones: 3,
        capacidadMaxHuespedes: 4,
        precioNoche: '260000.50',
      },
    ],
  },
  contacto: { telefono: '+57 300 000 0101', correo: 'reservas@casatayrona.test' },
  condicionesAccesibilidad: ['MovilidadReducida', 'Visual'],
  horarioAtencion: 'Recepción 24 horas\nCheck-in desde las 3:00 p. m.',
}

export const DETALLE_TOUR = {
  id: 's-tour-cafe',
  idPrestador: '00000000-0000-4000-8000-000000000002',
  nombreComercial: 'Tour de Café en Minca',
  categoria: 'Tour',
  descripcion: 'Recorrido guiado por fincas cafeteras.',
  imagenes: ['/imagenes/catalogo/tour-cafe-minca.webp'],
  precioBase: 80000,
  moneda: 'COP',
  detalleCategoria: { puntoEncuentro: 'Iglesia de Minca', duracionHoras: 4, nivelDificultad: 'Media', aforoMaxGrupo: 12 },
  contacto: { telefono: '+57 300 000 0301', correo: 'tours@cafeminca.test' },
  condicionesAccesibilidad: ['Auditiva'],
  horarioAtencion: 'Salidas diarias a las 8:00 a. m. y 1:00 p. m.',
}

export const DETALLE_RESTAURANTE = {
  id: 's-restaurante',
  idPrestador: '00000000-0000-4000-8000-000000000002',
  nombreComercial: 'Mirador Gourmet',
  categoria: 'Gastronomia',
  descripcion: null,
  imagenes: [],
  precioBase: 45000,
  moneda: 'COP',
  detalleCategoria: { tipoCocina: 'Caribeña', aforoMaxTurno: 40, menuVegetariano: true, rangoPrecioPlato: '$30.000 – $60.000' },
  // Sin contacto, accesibilidad ni horario: sus regiones aparecen deshabilitadas.
  contacto: null,
  condicionesAccesibilidad: [],
  horarioAtencion: null,
}

// ------------------------------------------------------------------ ms-booking (/api/v1/reservas)

const HORARIO_RESERVAS = { horaCheckIn: '15:00:00', horaCheckOut: '12:00:00', plazoCancelacionHoras: 24 }

/** Disponibilidad de un alojamiento: cupos por unidad y noche (`{ 'u-doble': [2, 1], ... }`, desde `desde`). */
export function disponibilidadAlojamiento(idServicio: string, desde: string, cupos: Record<string, number[]>) {
  const sumar = (dias: number) => new Date(Date.parse(`${desde}T00:00:00Z`) + dias * 86_400_000).toISOString().slice(0, 10)
  const unidades = Object.entries(cupos).map(([idUnidad, porNoche]) => ({
    idUnidad,
    noches: porNoche.map((cuposDisponibles, i) => ({ fecha: sumar(i), cuposTotales: 3, cuposDisponibles })),
  }))
  const longitud = Math.max(1, ...Object.values(cupos).map((porNoche) => porNoche.length))
  return {
    idServicio,
    desde,
    hasta: sumar(longitud - 1),
    categoria: unidades.length > 0 ? 'Alojamiento' : null,
    unidades,
    horarios: [],
    ...HORARIO_RESERVAS,
  }
}

/** Disponibilidad de un tour en un día: `{ '08:00:00': 5, '14:00:00': 0 }`. */
export function disponibilidadTour(idServicio: string, fecha: string, cupos: Record<string, number>) {
  const horarios = Object.entries(cupos).map(([hora, cuposDisponibles]) => ({ fecha, hora, cuposTotales: 12, cuposDisponibles }))
  return {
    idServicio,
    desde: fecha,
    hasta: fecha,
    categoria: horarios.length > 0 ? 'Tour' : null,
    unidades: [],
    horarios,
    ...HORARIO_RESERVAS,
  }
}

export function reservaConfirmada(extra: Record<string, unknown> = {}) {
  return {
    idReserva: '3f1c0000-0000-4000-8000-000000000001',
    estado: 'Confirmada',
    idServicio: 's-hotel',
    nombreServicio: 'Hotel Casa Tayrona',
    categoria: 'Alojamiento',
    idUnidad: 'u-suite',
    nombreUnidad: 'Suite familiar',
    fechaEntrada: '2026-12-12',
    fechaSalida: '2026-12-15',
    horaInicio: null,
    fechaInicio: '2026-12-12T20:00:00Z',
    fechaFin: '2026-12-15T17:00:00Z',
    cantidad: 2,
    personas: 5,
    precioUnitario: 260000.5,
    unidadCobro: 'Noche',
    precioTotal: 1560003,
    moneda: 'COP',
    fechaCreacion: '2026-10-16T17:00:00Z',
    fechaCancelacion: null,
    cancelableHasta: '2026-12-11T20:00:00Z',
    ...extra,
  }
}

export function paginaReservas(elementos: unknown[], { pagina = 0, totalElementos = elementos.length } = {}) {
  return { elementos, pagina, tamano: 20, totalElementos, totalPaginas: Math.ceil(totalElementos / 20) }
}

// ------------------------------------------------------------------ ms-auth, Administrar cuentas (/api/v1/auth/admin)

export const USUARIO_ADMIN = {
  idUsuario: '00000000-0000-4000-8000-000000000009',
  correo: 'admin@samaria360.test',
  rol: 'Administrador',
  estadoCuenta: 'Activo',
  nombreVisible: 'Laura Díaz',
}

export const PRESTADOR_PENDIENTE = {
  idUsuario: '00000000-0000-4000-8000-000000000006',
  representanteLegal: 'Ana Pérez',
  rnt: '123456',
  correoCorporativo: 'ana@hotel.co',
  fechaRegistro: '2026-10-18',
  estado: 'Pendiente',
}

export const DETALLE_PENDIENTE = {
  ...PRESTADOR_PENDIENTE,
  razonSocial: null,
  telefono: '+57 300 000 0000',
  aceptoPoliticas: true,
  motivoRechazo: null,
  revisadoEn: null,
}

export const PRESTADOR_ACTIVO = {
  idUsuario: '00000000-0000-4000-8000-000000000010',
  representanteLegal: 'Carlos Ruiz',
  rnt: '654321',
  correoCorporativo: 'carlos@kayak.co',
  fechaRegistro: '2026-10-10',
  estado: 'Activo',
}

export const PRESTADOR_RECHAZADO = {
  idUsuario: '00000000-0000-4000-8000-000000000011',
  representanteLegal: 'Juan Ruiz',
  rnt: '777777',
  correoCorporativo: 'juan@bar.co',
  fechaRegistro: '2026-10-11',
  estado: 'Rechazado',
}

export const PRESTADOR_INACTIVO = {
  idUsuario: '00000000-0000-4000-8000-000000000012',
  representanteLegal: 'Eva Díaz',
  rnt: '888888',
  correoCorporativo: 'eva@kayak.co',
  fechaRegistro: '2026-10-12',
  estado: 'Inactivo',
}

export const TURISTA_ACTIVO = {
  idUsuario: '00000000-0000-4000-8000-000000000013',
  nombre: 'Marta Lee',
  correo: 'marta@mail.com',
  pais: 'US',
  fechaRegistro: '2026-10-19',
  estado: 'Activo',
}

export const TURISTA_DESACTIVADO = {
  idUsuario: '00000000-0000-4000-8000-000000000014',
  nombre: 'Pablo Mora',
  correo: 'pablo@mail.com',
  pais: 'CO',
  fechaRegistro: '2026-10-20',
  estado: 'Desactivado',
}

/** Página de un listado del Administrador (10 por página, DEC-41; `pagina` desde 0). */
export function paginaAdmin(elementos: unknown[], { pagina = 0, totalElementos = elementos.length } = {}) {
  return { elementos, pagina, tamano: 10, totalElementos, totalPaginas: Math.ceil(totalElementos / 10) }
}

// ------------------------------------------------------------------ ms-auth, Cambiar rol de cuenta (RF45, B2)

export const USUARIO_GESTOR = {
  ...USUARIO_ADMIN,
  idUsuario: '00000000-0000-4000-8000-000000000015',
  correo: 'gestor@samaria360.test',
  rol: 'Gestor',
  nombreVisible: 'Sofía Gil',
}

/** Filas de GET /api/v1/auth/admin/personal. Con fila en `turista` (revocable) y de la semilla (sin nombre). */
export const PERSONAL_GESTOR = {
  idUsuario: '00000000-0000-4000-8000-000000000016',
  nombre: 'Luis Gómez',
  correo: 'luis@mail.com',
  rol: 'Gestor',
  fechaRegistro: '2026-10-18',
  revocable: true,
}

export const PERSONAL_GESTOR_SEMILLA = {
  idUsuario: '00000000-0000-4000-8000-000000000003',
  nombre: null,
  correo: 'gestor@samaria360.test',
  rol: 'Gestor',
  fechaRegistro: '2026-10-18',
  revocable: false,
}

export const PERSONAL_ADMIN = {
  idUsuario: '00000000-0000-4000-8000-000000000017',
  nombre: 'Ana Ruiz',
  correo: 'ana@mail.com',
  rol: 'Administrador',
  fechaRegistro: '2026-10-19',
  revocable: true,
}

/** La cuenta del Administrador de la sesión (USUARIO_ADMIN): sin acciones (DEC-116). */
export const PERSONAL_ADMIN_PROPIO = {
  idUsuario: USUARIO_ADMIN.idUsuario,
  nombre: null,
  correo: USUARIO_ADMIN.correo,
  rol: 'Administrador',
  fechaRegistro: '2026-10-18',
  revocable: false,
}

// ------------------------------------------------------------------ ms-catalog, contenido público (/api/v1/catalogo)

/** Preguntas en el orden del servidor: alfabético por título (DEC-63). */
export const PREGUNTA_ACCESIBLES = {
  id: '6a1f00000000000000000001',
  titulo: '¿Cómo encuentro lugares accesibles?',
  cuerpo: 'En la exploración, usa el filtro de accesibilidad: movilidad reducida, visual o auditiva.',
  fechaActualizacion: '2026-10-22T13:00:00Z',
}

export const PREGUNTA_CANCELAR = {
  id: '6a1f00000000000000000002',
  titulo: '¿Hasta cuándo puedo cancelar una reserva?',
  cuerpo: 'Hasta 24 horas antes del inicio, desde «Mis reservas».',
  fechaActualizacion: '2026-10-21T13:00:00Z',
}

export const PREGUNTA_TOUR_CAFE = {
  id: '6a1f00000000000000000003',
  titulo: '¿Qué incluye el Tour de Café?',
  cuerpo: 'Un recorrido guiado por fincas de la Sierra Nevada con degustación.',
  fechaActualizacion: '2026-10-20T13:00:00Z',
}

export const AVISO_CIERRE = {
  id: '6a1e00000000000000000002',
  mensaje: 'El sendero de la Cascada El Salto está cerrado temporalmente por lluvias.',
  tipo: 'Cierre',
  activo: true,
  fechaPublicacion: '2026-10-22T15:00:00Z',
}

export const AVISO_CLIMA = {
  id: '6a1e00000000000000000001',
  mensaje: 'Lluvias fuertes en la Sierra Nevada esta tarde.',
  tipo: 'Clima',
  activo: true,
  fechaPublicacion: '2026-10-21T15:00:00Z',
}

export const AVISO_INACTIVO = {
  id: '6a1e00000000000000000000',
  mensaje: 'Feria de las flores en el centro histórico.',
  tipo: 'Comunicado',
  activo: false,
  fechaPublicacion: '2026-10-01T15:00:00Z',
}

export function listaElementos(elementos: unknown[]) {
  return { elementos }
}

// ------------------------------------------------------------------ turista: perfil, favoritos e historial (B1)

/** GET /api/v1/auth/perfil de USUARIO_TURISTA (RF53). */
export const PERFIL_TURISTA = {
  correo: USUARIO_TURISTA.correo,
  primerNombre: 'Ana',
  segundoNombre: null,
  primerApellido: 'Pérez',
  segundoApellido: null,
  paisOrigen: 'CO',
  categoriasInteres: ['Naturaleza', 'Cultura'],
}

/** Favoritos resueltos (GET /api/v1/catalogo/favoritos, RF54). */
export const FAVORITO_HOTEL = {
  tipo: 'Servicio',
  id: HOTEL.id,
  nombre: HOTEL.nombre,
  categoria: 'Alojamiento',
  imagen: HOTEL.imagen,
  fechaCreacion: '2026-10-23T10:00:00Z',
  disponible: true,
}

export const FAVORITO_CASCADA = {
  tipo: 'Atractivo',
  id: CASCADA_CERRADA.id,
  nombre: CASCADA_CERRADA.nombre,
  categoria: 'Naturaleza',
  imagen: CASCADA_CERRADA.imagen,
  fechaCreacion: '2026-10-22T10:00:00Z',
  disponible: true,
}

export const FAVORITO_TOUR_NO_DISPONIBLE = {
  tipo: 'Servicio',
  id: 's-tour-sierra',
  nombre: 'Tour Sierra Nevada',
  categoria: 'Tour',
  imagen: null,
  fechaCreacion: '2026-10-21T10:00:00Z',
  disponible: false,
}

/** Criterios guardados (GET /api/v1/catalogo/historial, RF60): todos los campos, con null en los que no se usaron. */
export function criteriosHistorial(criterios: Record<string, unknown> = {}) {
  return {
    texto: null,
    categoria: null,
    precioMax: null,
    usarUbicacion: false,
    distanciaKm: null,
    accesibilidad: [],
    orden: null,
    serviciosIncluidos: [],
    huespedes: null,
    tipoHabitacion: null,
    menuVegetariano: null,
    tipoCocina: null,
    nivelDificultad: null,
    duracionMaxHoras: null,
    tipoVehiculoEquipo: null,
    ...criterios,
  }
}

export const BUSQUEDA_PLAYA = {
  id: '6a2000000000000000000001',
  fecha: '2026-10-23T15:00:00Z',
  criterios: criteriosHistorial({ texto: 'playa', categoria: 'Alojamiento', precioMax: 200000, serviciosIncluidos: ['WiFi'] }),
}

export const BUSQUEDA_CERCA = {
  id: '6a2000000000000000000002',
  fecha: '2026-10-22T15:00:00Z',
  criterios: criteriosHistorial({ usarUbicacion: true, distanciaKm: 5, orden: 'Distancia' }),
}

// ------------------------------------------------------------------ servicios del Prestador (iteración C, B1)

/** Forma de gestión de `/api/v1/catalogo/prestador/servicios/{id}` (WF-18), en el estado que pida la prueba. */
export function servicioGestion(extra: Record<string, unknown> = {}) {
  return {
    id: '6a3000000000000000000001',
    idPrestador: USUARIO_PRESTADOR.idUsuario,
    nombreComercial: 'Tour de Café en Minca',
    categoria: 'Tour',
    descripcion: 'Recorrido guiado por fincas cafeteras.',
    direccion: 'Iglesia de Minca, parque principal',
    coordenadas: { longitud: -74.1121, latitud: 11.1432 },
    contacto: { telefono: '+57 300 000 0301', correo: 'tours@cafeminca.test' },
    condicionesAccesibilidad: ['Auditiva'],
    horarioAtencion: null,
    precioBase: 80000,
    moneda: 'COP',
    detalleCategoria: { puntoEncuentro: 'Iglesia de Minca', duracionHoras: 4, nivelDificultad: null, aforoMaxGrupo: 12 },
    oferta: { diasOperacion: ['Lunes', 'Sabado'], salidas: ['07:00', '13:30'] },
    imagenes: [],
    estadoPublicacion: 'Pendiente',
    prestadorActivo: true,
    rnt: 'RNT-00000',
    fechaRegistro: '2026-10-28T15:00:00Z',
    motivoRechazo: null,
    fechaRevision: null,
    // DEC-186: la forma de gestión siempre trae la versión (DEC-205).
    version: 0,
    ...extra,
  }
}

/** Alojamiento propio con dos unidades (la de DETALLE_HOTEL), para el precio por unidad (RF32). */
export function servicioGestionHotel(extra: Record<string, unknown> = {}) {
  return servicioGestion({
    id: '6a3000000000000000000003',
    nombreComercial: 'Hotel Casa Tayrona',
    categoria: 'Alojamiento',
    precioBase: 150000,
    detalleCategoria: DETALLE_HOTEL.detalleCategoria,
    oferta: { diasOperacion: ['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado', 'Domingo'] },
    ...extra,
  })
}

/** Fila del listado de «Mis servicios». */
export function servicioResumen(extra: Record<string, unknown> = {}) {
  return {
    id: '6a3000000000000000000001',
    nombreComercial: 'Tour de Café en Minca',
    categoria: 'Tour',
    estadoPublicacion: 'Pendiente',
    motivoRechazo: null,
    fechaRegistro: '2026-10-28T15:00:00Z',
    ...extra,
  }
}

/** Página de `GET /api/v1/catalogo/prestador/servicios` (`pagina` desde 0, como la API). */
export function paginaServicios(elementos: unknown[], pagina = 0, totalElementos = elementos.length) {
  return { elementos, pagina, tamano: 10, totalElementos, totalPaginas: Math.ceil(totalElementos / 10) }
}

// ------------------------------------------------------------------ servicios del Administrador (iteración C, B2)

/**
 * Forma de gestión de la vista del Administrador (`/api/v1/catalogo/admin/servicios/{id}`, WF-19): la del Prestador
 * más `revisadoPor` y `estadoCuentaPrestador`. Tour Pendiente por defecto; `servicioAdminDe` arma las otras categorías.
 */
export function servicioAdmin(extra: Record<string, unknown> = {}) {
  return { ...servicioGestion(), estadoCuentaPrestador: 'Activo', ...extra }
}

export function servicioAdminDe(categoria: 'Alojamiento' | 'Gastronomia' | 'AlquilerEquipo', extra: Record<string, unknown> = {}) {
  const porCategoria = {
    Alojamiento: {
      nombreComercial: 'Hotel Casa Tayrona',
      precioBase: 150000,
      detalleCategoria: DETALLE_HOTEL.detalleCategoria,
      oferta: { diasOperacion: ['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado', 'Domingo'] },
    },
    Gastronomia: {
      nombreComercial: 'Mirador Gourmet',
      precioBase: 45000,
      detalleCategoria: DETALLE_RESTAURANTE.detalleCategoria,
      oferta: null,
    },
    AlquilerEquipo: {
      nombreComercial: 'Kayaks Taganga',
      precioBase: 60000,
      detalleCategoria: { tipoVehiculoEquipo: 'Kayak doble', unidadesDisponibles: 6, condicionesAlquiler: 'Chaleco incluido' },
      oferta: null,
    },
  }[categoria]
  return servicioAdmin({ categoria, ...porCategoria, ...extra })
}

/** Fila de la cola de revisión (`GET /api/v1/catalogo/admin/servicios`). */
export function servicioCola(extra: Record<string, unknown> = {}) {
  return {
    id: '6a3000000000000000000001',
    nombreComercial: 'Tour de Café en Minca',
    categoria: 'Tour',
    idPrestador: USUARIO_PRESTADOR.idUsuario,
    rnt: 'RNT-00000',
    estadoPublicacion: 'Pendiente',
    prestadorActivo: true,
    fechaRegistro: '2026-10-20T15:00:00Z',
    ...extra,
  }
}

// ------------------------------------------------------------------ oferta del prestador (iteración D, B)

/** `GET /api/v1/auth/prestador/perfil` (RF43, DEC-188): correo de acceso, representante, razón social y RNT, de solo lectura. */
export function perfilPrestador(extra: Record<string, unknown> = {}) {
  return {
    correo: USUARIO_PRESTADOR.correo,
    representanteLegal: 'Carlos Gómez',
    razonSocial: 'Casa Tayrona S.A.S.',
    rnt: 'RNT-00000',
    correoCorporativo: 'contacto@casatayrona.test',
    telefono: '+57 3000000000',
    ...extra,
  }
}

/**
 * Una reserva de `GET /api/v1/reservas/prestador` (RF12, DEC-191): la del turista sin `cancelableHasta` y, como el
 * contrato, sin ningún dato del Turista. Tour Confirmada por defecto.
 */
export function reservaPrestador(extra: Record<string, unknown> = {}) {
  return {
    idReserva: '7b1d6f0e-0000-4000-8000-000000000001',
    estado: 'Confirmada',
    idServicio: '6a3000000000000000000001',
    nombreServicio: 'Tour de Café en Minca',
    categoria: 'Tour',
    idUnidad: null,
    nombreUnidad: null,
    fechaEntrada: '2026-12-13',
    fechaSalida: null,
    horaInicio: '07:00:00',
    fechaInicio: '2026-12-13T12:00:00Z',
    fechaFin: '2026-12-13T16:00:00Z',
    cantidad: 1,
    personas: 3,
    precioUnitario: 80000,
    unidadCobro: 'Persona',
    precioTotal: 240000,
    moneda: 'COP',
    fechaCreacion: '2026-11-02T14:30:00Z',
    fechaCancelacion: null,
    ...extra,
  }
}

/** Página de `GET /api/v1/reservas/prestador` (20 por página, `pagina` desde 0). */
export function paginaReservasPrestador(elementos: unknown[], pagina = 0, totalElementos = elementos.length) {
  return { elementos, pagina, tamano: 20, totalElementos, totalPaginas: Math.ceil(totalElementos / 20) }
}

// ------------------------------------------------------------------ ms-catalog, atractivos (iteración E, B1)

export const ID_ATRACTIVO = '6b1000000000000000000001'

/** Forma de gestión (`GET /api/v1/catalogo/gestor/atractivos/{id}`, DEC-207). */
export function atractivoGestion(cambios: Record<string, unknown> = {}) {
  return {
    id: ID_ATRACTIVO,
    nombre: 'Cascada El Salto',
    categoria: 'Naturaleza',
    descripcion: 'Sendero entre bosque húmedo hasta una caída de agua.',
    coordenadas: { longitud: -74.11, latitud: 11.15 },
    imagenes: ['/api/v1/catalogo/imagenes/6b2000000000000000000001'],
    limiteCapacidadCarga: 80,
    estadoOperativo: 'Abierto',
    normasAmbientales: 'Prohibido hacer fogatas.',
    horarioAtencion: 'Martes a domingo\n8:00 a 16:00',
    condicionesAccesibilidad: ['MovilidadReducida'],
    gestorId: '00000000-0000-4000-8000-000000000003',
    fechaRegistro: '2026-10-30T15:00:00Z',
    fechaActualizacion: null,
    version: 2,
    ...cambios,
  }
}

/** Fila del panel «Atractivos» (WF-24). */
export function elementoAtractivo(cambios: Record<string, unknown> = {}) {
  return {
    id: ID_ATRACTIVO,
    nombre: 'Cascada El Salto',
    categoria: 'Naturaleza',
    estadoOperativo: 'Abierto',
    limiteCapacidadCarga: 80,
    imagen: '/api/v1/catalogo/imagenes/6b2000000000000000000001',
    fechaRegistro: '2026-10-30T15:00:00Z',
    version: 2,
    ...cambios,
  }
}

export function paginaAtractivos(elementos: unknown[], pagina = 0, totalElementos = elementos.length) {
  return { elementos, pagina, tamano: 10, totalElementos, totalPaginas: Math.ceil(totalElementos / 10) }
}

/** Ficha pública (`GET /api/v1/catalogo/atractivos/{id}`, DEC-217): sin el Gestor ni la versión. */
export function atractivoPublico(cambios: Record<string, unknown> = {}) {
  const { gestorId: _gestor, version: _version, fechaRegistro: _registro, fechaActualizacion: _edicion, ...ficha } =
    atractivoGestion()
  return { ...ficha, ...cambios }
}

// ------------------------------------------------------------------ ms-catalog, agenda de eventos (iteración E, B2)

export const ID_EVENTO = '6b3000000000000000000001'

/** Un evento de la agenda pública (`RespuestaEvento`): fechas de día, sin el Gestor ni `fechaRegistro` (DEC-239). */
export function eventoAgenda(cambios: Record<string, unknown> = {}) {
  return {
    id: ID_EVENTO,
    nombre: 'Fiestas del Mar',
    fechaInicio: '2026-11-20',
    fechaFin: '2026-11-22',
    ubicacion: 'Bahía de Santa Marta',
    descripcion: 'Regatas y desfile náutico.\nEntrada libre.',
    ...cambios,
  }
}

/** Un evento del panel del Gestor (`RespuestaEventoGestion`, DEC-238). */
export function eventoGestion(cambios: Record<string, unknown> = {}) {
  return {
    ...eventoAgenda(),
    gestorId: '00000000-0000-4000-8000-000000000015',
    fechaRegistro: '2026-11-05T15:00:00Z',
    ...cambios,
  }
}

export function paginaEventos(elementos: unknown[], pagina = 0, totalElementos = elementos.length) {
  return { elementos, pagina, tamano: 10, totalElementos, totalPaginas: Math.ceil(totalElementos / 10) }
}
