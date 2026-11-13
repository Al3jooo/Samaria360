import type { TFunction } from 'i18next'
import { ApiError, ERROR_RED, servicioNoDisponible } from '../../shared/api/httpClient'

/** Se guarda el código, no el texto: así el mensaje se traduce al cambiar de idioma (RNF08). */
export interface ErrorInicioSesion {
  codigo: string
  bloqueadoHasta?: string
  /** Justificación del rechazo del prestador (CUENTA_RECHAZADA, DEC-273). */
  motivo?: string
}

const CODIGOS_CON_MENSAJE = new Set([
  'CREDENCIALES_INVALIDAS',
  'CUENTA_BLOQUEADA',
  'CUENTA_INACTIVA',
  // Decisión D3: prestador Pendiente o Rechazado, después de verificar la contraseña.
  'CUENTA_PENDIENTE_VALIDACION',
  'CUENTA_RECHAZADA',
  'DATOS_INVALIDOS',
  // 429 del gateway: límite de tasa por conexión (DEC-271).
  'DEMASIADAS_SOLICITUDES',
  ERROR_RED,
])

export function aErrorInicioSesion(error: unknown): ErrorInicioSesion {
  if (!(error instanceof ApiError)) {
    return { codigo: 'GENERICO' }
  }
  // 502/503/504 o SERVICIO_NO_DISPONIBLE: el servicio está caído o arrancando, no es un error del usuario.
  if (servicioNoDisponible(error)) {
    return { codigo: 'SERVICIO_NO_DISPONIBLE' }
  }
  const bloqueadoHasta = error.problema?.bloqueadoHasta
  const motivo = error.problema?.motivo
  return {
    codigo: CODIGOS_CON_MENSAJE.has(error.codigo) ? error.codigo : 'GENERICO',
    bloqueadoHasta: typeof bloqueadoHasta === 'string' ? bloqueadoHasta : undefined,
    motivo: typeof motivo === 'string' && motivo.trim() !== '' ? motivo : undefined,
  }
}

/** Hora de desbloqueo en la zona horaria local del navegador y en el idioma activo. */
export function formatearHora(iso: string, idioma: string): string | null {
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) {
    return null
  }
  return new Intl.DateTimeFormat(idioma, { timeStyle: 'short' }).format(fecha)
}

export function textoError(error: ErrorInicioSesion, t: TFunction, idioma: string): string {
  if (error.codigo === 'CUENTA_BLOQUEADA') {
    const hora = error.bloqueadoHasta ? formatearHora(error.bloqueadoHasta, idioma) : null
    return hora ? t('errores.CUENTA_BLOQUEADA', { hora }) : t('errores.CUENTA_BLOQUEADA_SIN_HORA')
  }
  if (error.codigo === 'CUENTA_RECHAZADA' && error.motivo) {
    return t('errores.CUENTA_RECHAZADA_MOTIVO', { motivo: error.motivo })
  }
  return t(`errores.${error.codigo}`)
}
