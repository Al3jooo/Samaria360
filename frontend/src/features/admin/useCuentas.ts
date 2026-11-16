import { useCallback, useEffect, useState } from 'react'
import { ApiError, ERROR_RED, servicioNoDisponible } from '../../shared/api/httpClient'
import {
  listarPersonal,
  listarPrestadores,
  listarTuristas,
  type PaginaCuentas,
  type PersonalResumen,
  type PrestadorResumen,
  type TuristaResumen,
} from './adminApi'
import type { CriteriosAdmin } from './criteriosAdmin'

export type Lista =
  | { tipo: 'prestadores'; pagina: PaginaCuentas<PrestadorResumen> }
  | { tipo: 'turistas'; pagina: PaginaCuentas<TuristaResumen> }
  | { tipo: 'personal'; pagina: PaginaCuentas<PersonalResumen> }

/** sinPermiso: 403 ACCESO_DENEGADO (el rol no es Administrador). Un 401 ya cerró la sesión (httpClient). */
export type ErrorCuentas = 'sinPermiso' | 'noDisponible' | 'red' | 'otro'

export type EstadoCuentas =
  | { estado: 'cargando' }
  | { estado: 'ok'; lista: Lista }
  | { estado: 'error'; error: ErrorCuentas }

export function clasificarError(error: unknown): ErrorCuentas {
  if (!(error instanceof ApiError)) return 'otro'
  if (error.status === 403 && error.codigo === 'ACCESO_DENEGADO') return 'sinPermiso'
  if (servicioNoDisponible(error)) return 'noDisponible'
  if (error.codigo === ERROR_RED) return 'red'
  return 'otro'
}

function consultar(criterios: CriteriosAdmin): Promise<Lista> {
  if (criterios.pestana === 'turistas') {
    return listarTuristas({ estado: criterios.estado, q: criterios.q }, criterios.pagina).then((pagina) => ({
      tipo: 'turistas',
      pagina,
    }))
  }
  if (criterios.pestana === 'personal') {
    // «Consultar personal» (RF45).
    return listarPersonal({ rol: criterios.rol, q: criterios.q }, criterios.pagina).then((pagina) => ({
      tipo: 'personal',
      pagina,
    }))
  }
  // «Prestadores por validar» es la lista de Pendientes (RF28).
  const estado = criterios.pestana === 'pendientes' ? 'Pendiente' : criterios.estado
  return listarPrestadores({ estado, q: criterios.q }, criterios.pagina).then((pagina) => ({
    tipo: 'prestadores',
    pagina,
  }))
}

/**
 * Listado de la pestaña con los criterios de la URL y el contador de «Prestadores por validar (n)». El contador es el
 * `totalElementos` de los Pendientes **sin búsqueda**: en la pestaña de Pendientes sin `q` sale de la misma lista; si
 * no, de una consulta propia. `recargar` vuelve a consultar los dos; `reemplazarFila` cambia una fila en su sitio
 * (un turista de RF62 o una cuenta del personal de RF45, DEC-120).
 */
export function useCuentas(criterios: CriteriosAdmin) {
  const [estado, setEstado] = useState<EstadoCuentas>({ estado: 'cargando' })
  const [pendientes, setPendientes] = useState<number | null>(null)
  const [intento, setIntento] = useState(0)
  const clave = `${criterios.pestana}|${criterios.estado}|${criterios.rol}|${criterios.q}|${criterios.pagina}`
  const contadorDeLaLista = criterios.pestana === 'pendientes' && criterios.q === ''

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultar(criterios)
      .then((lista) => {
        if (!vigente) return
        setEstado({ estado: 'ok', lista })
        if (contadorDeLaLista) setPendientes(lista.pagina.totalElementos)
      })
      .catch((error: unknown) => {
        if (vigente) setEstado({ estado: 'error', error: clasificarError(error) })
      })
    return () => {
      vigente = false
    }
    // La clave incluye todos los criterios; una nueva referencia equivalente no debe repetir la petición.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, intento])

  useEffect(() => {
    if (contadorDeLaLista) return
    let vigente = true
    listarPrestadores({ estado: 'Pendiente' }, 1)
      .then((pagina) => {
        if (vigente) setPendientes(pagina.totalElementos)
      })
      .catch(() => {
        // Sin contador: la pestaña se muestra sin «(n)»; el error de la lista ya se ve en su región.
        if (vigente) setPendientes(null)
      })
    return () => {
      vigente = false
    }
  }, [contadorDeLaLista, intento])

  const recargar = useCallback(() => setIntento((n) => n + 1), [])
  const reemplazarFila = useCallback((fila: TuristaResumen | PersonalResumen) => {
    setEstado((actual) => {
      if (actual.estado !== 'ok' || actual.lista.tipo === 'prestadores') return actual
      // La fila es del mismo tipo que la lista: el turista en «Turistas», la cuenta en «Personal».
      const lista = actual.lista as { tipo: 'turistas' | 'personal'; pagina: PaginaCuentas<typeof fila> }
      return {
        ...actual,
        lista: {
          ...lista,
          pagina: {
            ...lista.pagina,
            elementos: lista.pagina.elementos.map((f) => (f.idUsuario === fila.idUsuario ? fila : f)),
          },
        } as Lista,
      }
    })
  }, [])

  return { estado, pendientes, recargar, reemplazarFila }
}
