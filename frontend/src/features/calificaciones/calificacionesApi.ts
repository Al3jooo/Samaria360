import { solicitar } from '../../shared/api/httpClient'

/** Contrato de calificaciones y reportes de ms-analytics (`docs/sad/api/ms-analytics.md`; DEC-300, DEC-301). */

const BASE = '/api/v1/analytics'

export type TipoEntidad = 'servicio' | 'atractivo'

/** Decisión 1 del equipo: comentario de 0 a 500; motivo de moderación de 1 a 500. */
export const COMENTARIO_MAXIMO = 500
export const MOTIVO_MAXIMO = 500
export const DESCRIPCION_REPORTE_MAXIMA = 500

/** Lista predefinida de RF57 (respuesta 5 del equipo). */
export const MOTIVOS_REPORTE = [
  'INFORMACION_FALSA',
  'INFORMACION_DESACTUALIZADA',
  'PRECIO_INCORRECTO',
  'UBICACION_INCORRECTA',
  'FOTOS_NO_CORRESPONDEN',
  'OTRO',
] as const
export type MotivoReporte = (typeof MOTIVOS_REPORTE)[number]

export interface CalificacionPublica {
  id: string
  estrellas: number
  comentario: string
  fecha: string
}

export interface CalificacionesEntidad {
  promedio: number | null
  cantidad: number
  elementos: CalificacionPublica[]
  pagina: number
  tamano: number
  totalElementos: number
}

export interface Elegibilidad {
  puedeCalificar: boolean
  /** Por qué no: SIN_RESERVA_COMPLETADA, CALIFICACION_DUPLICADA o CUENTA_INACTIVA. */
  motivo: string | null
  propia: CalificacionPublica | null
}

/** Públicas: sin token. */
export function consultarCalificaciones(tipo: TipoEntidad, id: string, pagina = 0): Promise<CalificacionesEntidad> {
  const consulta = new URLSearchParams({ tipo, id, pagina: String(pagina) })
  return solicitar<CalificacionesEntidad>(`${BASE}/calificaciones?${consulta.toString()}`, { publica: true })
}

export function consultarElegibilidad(tipo: TipoEntidad, id: string): Promise<Elegibilidad> {
  const consulta = new URLSearchParams({ tipo, id })
  return solicitar<Elegibilidad>(`${BASE}/calificaciones/elegibilidad?${consulta.toString()}`)
}

export function calificar(tipo: TipoEntidad, id: string, estrellas: number, comentario: string) {
  return solicitar<CalificacionPublica>(`${BASE}/calificaciones`, {
    method: 'POST',
    cuerpo: { tipoEntidad: tipo, idEntidad: id, estrellas, comentario },
  })
}

export function reportar(tipo: TipoEntidad, id: string, motivo: MotivoReporte, descripcion: string) {
  return solicitar<{ id: string; estado: string }>(`${BASE}/reportes`, {
    method: 'POST',
    cuerpo: { tipoEntidad: tipo, idEntidad: id, motivo, descripcion },
  })
}

// ------------------------------------------------------------------ RF56 (Prestador)

export interface ResumenServicio {
  idServicio: string
  nombre: string | null
  promedio: number | null
  cantidad: number
}

export interface CalificacionRecibida {
  id: string
  idServicio: string
  nombreServicio: string | null
  estrellas: number
  comentario: string
  fecha: string
}

export interface CalificacionesPrestador {
  promedio: number | null
  cantidad: number
  servicios: ResumenServicio[]
  elementos: CalificacionRecibida[]
  pagina: number
  tamano: number
  totalElementos: number
}

export interface FiltrosPrestador {
  servicio?: string
  desde?: string
  hasta?: string
  /** Desde 1 en la pantalla; la API empieza en 0. */
  pagina: number
}

export function consultarCalificacionesPrestador(filtros: FiltrosPrestador): Promise<CalificacionesPrestador> {
  const consulta = new URLSearchParams({ pagina: String(Math.max(0, filtros.pagina - 1)) })
  if (filtros.servicio) consulta.set('servicio', filtros.servicio)
  if (filtros.desde) consulta.set('desde', filtros.desde)
  if (filtros.hasta) consulta.set('hasta', filtros.hasta)
  return solicitar<CalificacionesPrestador>(`${BASE}/prestador/calificaciones?${consulta.toString()}`)
}

// ------------------------------------------------------------------ RF29 y RF57 (Administrador)

export interface CalificacionModeracion {
  id: string
  tipoEntidad: TipoEntidad
  idEntidad: string
  nombreEntidad: string | null
  turistaId: string
  estrellas: number
  comentario: string
  estado: 'Visible' | 'Oculta'
  motivoModeracion: string | null
  fecha: string
  fechaModeracion: string | null
}

export interface ReporteModeracion {
  id: string
  tipoEntidad: TipoEntidad
  idEntidad: string
  turistaId: string
  motivo: MotivoReporte
  descripcion: string
  estado: 'Pendiente' | 'Revisado'
  fecha: string
  revisadoPor: string | null
  fechaRevision: string | null
}

export interface PaginaModeracion<T> {
  elementos: T[]
  pagina: number
  tamano: number
  totalElementos: number
}

export function listarCalificacionesModeracion(estado: string, pagina: number) {
  const consulta = new URLSearchParams({ pagina: String(Math.max(0, pagina)) })
  if (estado) consulta.set('estado', estado)
  return solicitar<PaginaModeracion<CalificacionModeracion>>(`${BASE}/admin/calificaciones?${consulta.toString()}`)
}

export type AccionModeracion = 'ocultamiento' | 'visibilidad' | 'eliminacion'

export function moderar(id: string, accion: AccionModeracion, motivo: string) {
  return solicitar<CalificacionModeracion | undefined>(
    `${BASE}/admin/calificaciones/${encodeURIComponent(id)}/${accion}`,
    { method: 'POST', cuerpo: { motivo } },
  )
}

export function listarReportes(estado: string, pagina: number) {
  const consulta = new URLSearchParams({ pagina: String(Math.max(0, pagina)) })
  if (estado) consulta.set('estado', estado)
  return solicitar<PaginaModeracion<ReporteModeracion>>(`${BASE}/admin/reportes?${consulta.toString()}`)
}

export function marcarRevisado(id: string) {
  return solicitar<ReporteModeracion>(`${BASE}/admin/reportes/${encodeURIComponent(id)}/revision`, {
    method: 'POST',
  })
}

/** Ficha pública de lo calificado o reportado. */
export function rutaEntidad(tipo: TipoEntidad, id: string): string {
  return tipo === 'atractivo' ? `/atractivos/${encodeURIComponent(id)}` : `/servicios/${encodeURIComponent(id)}`
}

/** «4,5» en español y «4.5» en inglés. */
export function formatearPromedio(promedio: number, idioma: string): string {
  return new Intl.NumberFormat(idioma.startsWith('en') ? 'en-US' : 'es-CO', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 2,
  }).format(promedio)
}
