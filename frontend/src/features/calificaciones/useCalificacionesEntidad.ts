import { useCallback, useEffect, useState } from 'react'
import { consultarCalificaciones, type CalificacionesEntidad, type TipoEntidad } from './calificacionesApi'

export type EstadoCalificaciones =
  | { estado: 'cargando' }
  | { estado: 'error' }
  | { estado: 'ok'; datos: CalificacionesEntidad }

/** Las calificaciones visibles de una entidad (RF17): la página pide la primera y «Ver más» agrega las siguientes. */
export function useCalificacionesEntidad(tipo: TipoEntidad, id: string | undefined) {
  const [resultado, setResultado] = useState<EstadoCalificaciones>({ estado: 'cargando' })

  const cargar = useCallback(async () => {
    if (!id) return
    try {
      setResultado({ estado: 'ok', datos: await consultarCalificaciones(tipo, id) })
    } catch {
      setResultado({ estado: 'error' })
    }
  }, [tipo, id])

  useEffect(() => {
    let vigente = true
    if (!id) return
    consultarCalificaciones(tipo, id)
      .then((datos) => {
        if (vigente) setResultado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setResultado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [tipo, id])

  const verMas = useCallback(async () => {
    if (resultado.estado !== 'ok' || !id) return
    const siguiente = await consultarCalificaciones(tipo, id, resultado.datos.pagina + 1)
    setResultado({
      estado: 'ok',
      datos: { ...siguiente, elementos: [...resultado.datos.elementos, ...siguiente.elementos] },
    })
  }, [resultado, tipo, id])

  return { resultado, recargar: cargar, verMas }
}
