import { solicitar } from '../../shared/api/httpClient'

/** Log de auditoría de RF13 en ms-analytics (`docs/sad/api/ms-analytics.md`), solo para el Administrador. */
export const ACCIONES_AUDITADAS = [
  'PRESTADOR_APROBADO',
  'PRESTADOR_RECHAZADO',
  'SERVICIO_APROBADO',
  'SERVICIO_RECHAZADO',
  'ROL_CAMBIADO',
  'RESERVA_CANCELADA',
  'CALIFICACION_OCULTADA',
  'CALIFICACION_MOSTRADA',
  'CALIFICACION_ELIMINADA',
] as const

export type AccionAuditada = (typeof ACCIONES_AUDITADAS)[number]

export interface RegistroAuditoria {
  usuarioId: string
  accion: AccionAuditada
  detalle: string
  fecha: string
}

export interface PaginaAuditoria {
  elementos: RegistroAuditoria[]
  pagina: number
  tamano: number
  totalElementos: number
}

export interface FiltrosAuditoria {
  accion?: string
  desde?: string
  hasta?: string
  /** Desde 1 en la pantalla; la API empieza en 0. */
  pagina: number
}

export function listarAuditoria(filtros: FiltrosAuditoria): Promise<PaginaAuditoria> {
  const consulta = new URLSearchParams({ pagina: String(Math.max(0, filtros.pagina - 1)) })
  if (filtros.accion) consulta.set('accion', filtros.accion)
  if (filtros.desde) consulta.set('desde', filtros.desde)
  if (filtros.hasta) consulta.set('hasta', filtros.hasta)
  return solicitar<PaginaAuditoria>(`/api/v1/analytics/admin/auditoria?${consulta.toString()}`)
}
