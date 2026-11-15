import { solicitar } from '../../shared/api/httpClient'

/** Contrato de GET /api/v1/catalogo/oferta (ms-catalog: OfertaController y sus DTO), a través del gateway. */

export const CATEGORIAS = ['Alojamiento', 'Gastronomia', 'Tour', 'AlquilerEquipo'] as const
export type Categoria = (typeof CATEGORIAS)[number]

export const CONDICIONES_ACCESIBILIDAD = ['MovilidadReducida', 'Visual', 'Auditiva'] as const
export type CondicionAccesibilidad = (typeof CONDICIONES_ACCESIBILIDAD)[number]

export const ORDENES = ['Distancia', 'PrecioAscendente', 'PrecioDescendente', 'Nombre', 'Calificacion'] as const
export type Orden = (typeof ORDENES)[number]

export type EstadoOperativo = 'Abierto' | 'Cerrado_temporalmente' | 'En_mantenimiento'

/** Vocabulario cerrado de RF10 y WF-08 (el validador de MongoDB lo restringe con el mismo enum). */
export const SERVICIOS_INCLUIDOS = ['WiFi', 'Desayuno', 'Piscina', 'Parqueadero'] as const
export type ServicioIncluido = (typeof SERVICIOS_INCLUIDOS)[number]

/**
 * «Más filtros» de WF-05 (RF10): filtros sobre el detalle de una categoría. Cada uno exige su categoría; la API
 * responde 400 si llega sin ella o con otra, así que nunca se envían sueltos (ver `soloDeCategoria`).
 */
export interface MasFiltros {
  serviciosIncluidos?: ServicioIncluido[]
  huespedes?: number
  tipoHabitacion?: string
  /** Solo `true` filtra («Opción vegetariana»). */
  menuVegetariano?: boolean
  tipoCocina?: string
  nivelDificultad?: string
  duracionMaxHoras?: number
  tipoVehiculoEquipo?: string
}

export const CAMPOS_POR_CATEGORIA = {
  Alojamiento: ['serviciosIncluidos', 'huespedes', 'tipoHabitacion'],
  Gastronomia: ['menuVegetariano', 'tipoCocina'],
  Tour: ['nivelDificultad', 'duracionMaxHoras'],
  AlquilerEquipo: ['tipoVehiculoEquipo'],
} as const satisfies Record<Categoria, readonly (keyof MasFiltros)[]>

/** Cantidad de filtros con valor (cada servicio incluido cuenta uno). */
export function contarMasFiltros(filtros: MasFiltros): number {
  return Object.values(filtros).reduce<number>(
    (total, valor) => total + (Array.isArray(valor) ? valor.length : valor === undefined || valor === false ? 0 : 1),
    0,
  )
}

/** Solo los filtros con valor de la categoría dada; sin categoría, ninguno. */
export function soloDeCategoria(filtros: MasFiltros | undefined, categoria: Categoria | undefined): MasFiltros {
  if (!filtros || !categoria) return {}
  const resultado: MasFiltros = {}
  for (const campo of CAMPOS_POR_CATEGORIA[categoria]) {
    const valor = filtros[campo]
    const conValor = Array.isArray(valor) ? valor.length > 0 : valor !== undefined && valor !== false && valor !== ''
    if (conValor) Object.assign(resultado, { [campo]: valor })
  }
  return resultado
}

export interface ResultadoOferta {
  tipo: 'Servicio' | 'Atractivo'
  id: string
  nombre: string
  /** Categoría del servicio (enum) o texto libre del atractivo. */
  categoria: string | null
  descripcionCorta: string | null
  /** Nulo en los atractivos. */
  precio: number | null
  imagen: string | null
  coordenada: { longitud: number; latitud: number }
  /** Solo si la búsqueda tiene ubicación (RF38). */
  distanciaKm: number | null
  accesibilidad: CondicionAccesibilidad[]
  /** Solo en los atractivos (RF33). */
  estadoOperativo: EstadoOperativo | null
  /** Promedio visible de RF17 (DEC-302); nulo o ausente sin calificaciones. */
  calificacion?: number | null
  cantidadCalificaciones?: number
  /** Solo en atractivos: el nivel vigente de afluencia (RF39, DEC-304); nulo o ausente sin lectura del día. */
  afluencia?: 'Bajo' | 'Medio' | 'Alto' | null
}

export interface PaginaOferta {
  elementos: ResultadoOferta[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export interface Ubicacion {
  latitud: number
  longitud: number
}

/** Criterios de la pantalla. La página empieza en 1; la API la recibe desde 0. */
export interface CriteriosBusqueda {
  texto?: string
  categoria?: Categoria
  /** Solo los de `categoria` (los demás se descartan al leer la URL y al consultar la API). */
  masFiltros?: MasFiltros
  precioMax?: number
  distanciaKm?: number
  accesibilidad: CondicionAccesibilidad[]
  orden?: Orden
  pagina: number
  tamano?: number
  /** Nunca va en la URL de la pantalla (privacidad); solo a la API. */
  ubicacion?: Ubicacion
}

export const TAMANO_PAGINA = 10

/**
 * Consulta para la API. Sin ubicación, la distancia máxima y el orden por distancia no se envían: la API los
 * rechazaría (RF38, RF61) y la pantalla los muestra deshabilitados.
 */
export function consultaApi(criterios: CriteriosBusqueda): string {
  const parametros = new URLSearchParams()
  if (criterios.texto) parametros.set('texto', criterios.texto)
  if (criterios.categoria) parametros.set('categoria', criterios.categoria)
  agregarMasFiltros(parametros, soloDeCategoria(criterios.masFiltros, criterios.categoria))
  if (criterios.precioMax !== undefined) parametros.set('precioMax', String(criterios.precioMax))
  if (criterios.ubicacion) {
    parametros.set('lat', String(criterios.ubicacion.latitud))
    parametros.set('lng', String(criterios.ubicacion.longitud))
    if (criterios.distanciaKm !== undefined) parametros.set('distanciaKm', String(criterios.distanciaKm))
  }
  criterios.accesibilidad.forEach((condicion) => parametros.append('accesibilidad', condicion))
  if (criterios.orden && (criterios.orden !== 'Distancia' || criterios.ubicacion)) {
    parametros.set('orden', criterios.orden)
  }
  parametros.set('pagina', String(Math.max(0, criterios.pagina - 1)))
  parametros.set('tamano', String(criterios.tamano ?? TAMANO_PAGINA))
  return parametros.toString()
}

/** Mismos nombres en la URL de la pantalla y en la API; `serviciosIncluidos` se repite. */
export function agregarMasFiltros(parametros: URLSearchParams, filtros: MasFiltros) {
  filtros.serviciosIncluidos?.forEach((servicio) => parametros.append('serviciosIncluidos', servicio))
  if (filtros.huespedes !== undefined) parametros.set('huespedes', String(filtros.huespedes))
  if (filtros.tipoHabitacion) parametros.set('tipoHabitacion', filtros.tipoHabitacion)
  if (filtros.menuVegetariano) parametros.set('menuVegetariano', 'true')
  if (filtros.tipoCocina) parametros.set('tipoCocina', filtros.tipoCocina)
  if (filtros.nivelDificultad) parametros.set('nivelDificultad', filtros.nivelDificultad)
  if (filtros.duracionMaxHoras !== undefined) parametros.set('duracionMaxHoras', String(filtros.duracionMaxHoras))
  if (filtros.tipoVehiculoEquipo) parametros.set('tipoVehiculoEquipo', filtros.tipoVehiculoEquipo)
}

export function buscarOferta(criterios: CriteriosBusqueda): Promise<PaginaOferta> {
  return solicitar<PaginaOferta>(`/api/v1/catalogo/oferta?${consultaApi(criterios)}`, { publica: true })
}
