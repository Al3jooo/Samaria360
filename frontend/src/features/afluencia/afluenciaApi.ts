import { useEffect, useState } from 'react'
import { solicitar } from '../../shared/api/httpClient'

/** Afluencia de los atractivos en ms-catalog (`docs/sad/api/ms-catalog.md`, «Afluencia»; DEC-304, DEC-305). */

export type NivelAfluencia = 'Bajo' | 'Medio' | 'Alto'

export interface Afluencia {
  nivel: NivelAfluencia
  porcentaje: number
  ocupacion: number
  limite: number
  fecha: string
  /** RF08: la ocupación llegó al límite de capacidad de carga. */
  limiteAlcanzado: boolean
}

export interface DestinoAlternativo {
  id: string
  nombre: string
  categoria: string | null
  imagen: string | null
  nivel: NivelAfluencia | null
  porcentaje: number | null
  distanciaKm: number
}

export interface AfluenciaAtractivo {
  afluencia: Afluencia | null
  alternativos: DestinoAlternativo[]
}

export const OCUPACION_MAXIMA = 1_000_000

/** Pública: el nivel vigente (RF39), el límite (RF08) y los alternativos de menor concurrencia (RF51). */
export function consultarAfluencia(id: string): Promise<AfluenciaAtractivo> {
  return solicitar<AfluenciaAtractivo>(`/api/v1/catalogo/atractivos/${encodeURIComponent(id)}/afluencia`, {
    publica: true,
  })
}

/** Solo el Gestor (RF39): registra la ocupación actual y responde el nivel calculado. */
export function registrarOcupacion(id: string, ocupacion: number): Promise<Afluencia> {
  return solicitar<Afluencia>(`/api/v1/catalogo/gestor/atractivos/${encodeURIComponent(id)}/ocupacion`, {
    method: 'POST',
    cuerpo: { ocupacion },
  })
}

export type EstadoAfluencia =
  | { estado: 'cargando' }
  | { estado: 'error' }
  | { estado: 'ok'; datos: AfluenciaAtractivo }

export function useAfluencia(id: string): EstadoAfluencia {
  const [resultado, setResultado] = useState<EstadoAfluencia>({ estado: 'cargando' })
  useEffect(() => {
    let vigente = true
    consultarAfluencia(id)
      .then((datos) => {
        if (vigente) setResultado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setResultado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [id])
  return resultado
}
