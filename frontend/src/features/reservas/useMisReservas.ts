import { useCallback, useEffect, useState } from 'react'
import { ApiError, servicioNoDisponible } from '../../shared/api/httpClient'
import type { CriteriosReservas } from './filtrosReservas'
import { consultarMisReservas, type PaginaReservas, type Reserva } from './reservasApi'

export type ErrorMisReservas = 'datosInvalidos' | 'noDisponible' | 'red' | 'otro'

export type EstadoMisReservas =
  | { estado: 'inactivo' }
  | { estado: 'cargando' }
  | { estado: 'ok'; pagina: PaginaReservas }
  | { estado: 'error'; error: ErrorMisReservas; campos: Record<string, string> }

function clasificar(error: unknown): { error: ErrorMisReservas; campos: Record<string, string> } {
  if (!(error instanceof ApiError)) return { error: 'otro', campos: {} }
  if (error.codigo === 'DATOS_INVALIDOS') {
    return { error: 'datosInvalidos', campos: (error.problema?.campos ?? {}) as Record<string, string> }
  }
  if (servicioNoDisponible(error)) return { error: 'noDisponible', campos: {} }
  if (error.codigo === 'ERROR_RED') return { error: 'red', campos: {} }
  return { error: 'otro', campos: {} }
}

/**
 * «Mis reservas» (RF12, turista) con los filtros de la URL. `reemplazar` actualiza una reserva en la página sin volver
 * a consultar (después de cancelarla); `recargar` vuelve a consultar.
 */
export function useMisReservas(criterios: CriteriosReservas, habilitado: boolean) {
  const [estado, setEstado] = useState<EstadoMisReservas>({ estado: 'inactivo' })
  const [intento, setIntento] = useState(0)
  const clave = `${criterios.desde}|${criterios.hasta}|${criterios.estados.join(',')}|${criterios.pagina}`

  useEffect(() => {
    if (!habilitado) {
      setEstado({ estado: 'inactivo' })
      return
    }
    let vigente = true
    setEstado({ estado: 'cargando' })
    const filtros = {
      desde: criterios.desde || undefined,
      hasta: criterios.hasta || undefined,
      estados: criterios.estados,
    }
    consultarMisReservas(filtros, criterios.pagina)
      .then((pagina) => {
        if (vigente) setEstado({ estado: 'ok', pagina })
      })
      .catch((error: unknown) => {
        if (vigente) setEstado({ estado: 'error', ...clasificar(error) })
      })
    return () => {
      vigente = false
    }
    // La clave incluye fechas, estados y página; objetos equivalentes no deben repetir la petición.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, habilitado, intento])

  const recargar = useCallback(() => setIntento((n) => n + 1), [])
  const reemplazar = useCallback((reserva: Reserva) => {
    setEstado((actual) =>
      actual.estado === 'ok'
        ? {
            ...actual,
            pagina: {
              ...actual.pagina,
              elementos: actual.pagina.elementos.map((r) => (r.idReserva === reserva.idReserva ? reserva : r)),
            },
          }
        : actual,
    )
  }, [])

  return { ...estado, recargar, reemplazar } as EstadoMisReservas & {
    recargar: () => void
    reemplazar: (reserva: Reserva) => void
  }
}
