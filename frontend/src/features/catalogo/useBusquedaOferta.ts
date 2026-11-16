import { useCallback, useEffect, useState } from 'react'
import { ApiError, servicioNoDisponible } from '../../shared/api/httpClient'
import { buscarOferta, consultaApi, type CriteriosBusqueda, type PaginaOferta } from './catalogoApi'

export type ErrorBusqueda = 'noDisponible' | 'criteriosInvalidos' | 'red' | 'otro'

export type EstadoBusqueda =
  | { estado: 'cargando' }
  | { estado: 'ok'; pagina: PaginaOferta }
  | { estado: 'error'; error: ErrorBusqueda }

function clasificar(error: unknown): ErrorBusqueda {
  if (!(error instanceof ApiError)) return 'otro'
  if (servicioNoDisponible(error)) return 'noDisponible'
  if (error.codigo === 'CRITERIOS_INVALIDOS') return 'criteriosInvalidos'
  if (error.codigo === 'ERROR_RED') return 'red'
  return 'otro'
}

/** Consulta la oferta cada vez que cambian los criterios; ignora las respuestas de consultas anteriores. */
export function useBusquedaOferta(criterios: CriteriosBusqueda) {
  const clave = consultaApi(criterios)
  const [estado, setEstado] = useState<EstadoBusqueda>({ estado: 'cargando' })
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    buscarOferta(criterios)
      .then((pagina) => {
        if (vigente) setEstado({ estado: 'ok', pagina })
      })
      .catch((error: unknown) => {
        if (vigente) setEstado({ estado: 'error', error: clasificar(error) })
      })
    return () => {
      vigente = false
    }
    // consultaApi incluye todos los criterios: una referencia nueva equivalente no repite la petición.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, intento])

  const reintentar = useCallback(() => setIntento((n) => n + 1), [])
  return { ...estado, reintentar } as EstadoBusqueda & { reintentar: () => void }
}
