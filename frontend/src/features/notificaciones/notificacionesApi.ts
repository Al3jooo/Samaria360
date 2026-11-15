import { solicitar } from '../../shared/api/httpClient'

/** Campana de RF63 en ms-analytics (`docs/sad/api/ms-analytics.md`), a través del gateway. Turista y Prestador. */
const BASE = '/api/v1/analytics/notificaciones'

export type TipoNotificacion = 'RESERVA_CONFIRMADA' | 'RESERVA_CANCELADA' | 'RESERVA_COMPLETADA' | 'NUEVA_RESERVA'

export interface Notificacion {
  id: string
  tipo: TipoNotificacion
  /** Respaldo en español; la interfaz arma el texto con `tipo` y `datos` en el idioma activo (RF03). */
  mensaje: string
  datos: Record<string, string>
  leida: boolean
  fecha: string
}

export interface PaginaNotificaciones {
  elementos: Notificacion[]
  pagina: number
  tamano: number
  totalElementos: number
  noLeidas: number
}

export function listarNotificaciones(pagina = 0): Promise<PaginaNotificaciones> {
  return solicitar<PaginaNotificaciones>(`${BASE}?pagina=${Math.max(0, pagina)}`)
}

export function marcarLeida(id: string): Promise<void> {
  return solicitar<void>(`${BASE}/${encodeURIComponent(id)}/lectura`, { method: 'POST' })
}

export function marcarTodas(): Promise<{ marcadas: number }> {
  return solicitar<{ marcadas: number }>(`${BASE}/lectura`, { method: 'POST' })
}
