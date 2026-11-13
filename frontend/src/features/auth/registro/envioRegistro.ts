import type { TFunction } from 'i18next'
import { ApiError, ERROR_RED, servicioNoDisponible } from '../../../shared/api/httpClient'
import { LONGITUD_MAXIMA, LONGITUD_MINIMA_CONTRASENA, erroresDelServidor, type ErroresCampos } from './validacionRegistro'

/** Resultado de un registro rechazado: errores por campo o un error general (código de `errores.*`). */
export interface RechazoRegistro {
  campos: ErroresCampos
  general: string | null
}

/** Traduce el error del backend: los códigos por campo van a su campo; la indisponibilidad, a un aviso general. */
export function aRechazoRegistro(error: unknown): RechazoRegistro {
  if (!(error instanceof ApiError)) return { campos: {}, general: 'GENERICO' }
  if (servicioNoDisponible(error)) return { campos: {}, general: 'SERVICIO_NO_DISPONIBLE' }
  if (error.codigo === ERROR_RED) return { campos: {}, general: ERROR_RED }
  // 429 del gateway: límite de tasa por conexión (DEC-271).
  if (error.codigo === 'DEMASIADAS_SOLICITUDES') return { campos: {}, general: 'DEMASIADAS_SOLICITUDES' }
  if (error.codigo === 'POLITICAS_NO_ACEPTADAS') return { campos: { aceptoPoliticas: 'POLITICAS_NO_ACEPTADAS' }, general: null }
  if (error.codigo === 'RNT_YA_REGISTRADO') return { campos: { rnt: 'RNT_YA_REGISTRADO' }, general: null }
  if (error.codigo === 'DATOS_INVALIDOS') {
    const campos = erroresDelServidor(error.problema?.campos)
    return Object.keys(campos).length > 0 ? { campos, general: null } : { campos: {}, general: 'REGISTRO_INVALIDO' }
  }
  return { campos: {}, general: 'GENERICO' }
}

/** Texto de un error de campo: primero el específico del campo y, si no existe, el general del código. */
export function textoErrorCampo(t: TFunction, campo: string, motivo: string | undefined): string | undefined {
  if (!motivo) return undefined
  return t([`registro.errores.${campo}.${motivo}`, `registro.errores.${motivo}`], {
    maximo: LONGITUD_MAXIMA[campo],
    minimo: LONGITUD_MINIMA_CONTRASENA,
  })
}

/** Mueve el foco al primer campo con error, en el orden del formulario. */
export function enfocarPrimerError(orden: readonly string[], errores: ErroresCampos, idDe: (campo: string) => string) {
  const primero = orden.find((campo) => errores[campo])
  if (primero) document.getElementById(idDe(primero))?.focus()
}
