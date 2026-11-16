import { useCallback, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError } from '../../shared/api/httpClient'
import { useSesion } from '../auth/useSesion'
import {
  FavoritosContext,
  claveFavorito,
  type AnuncioFavoritos,
  type EstadoFavoritos,
  type ModoFavorito,
  type ReferenciaFavorito,
  type ValorFavoritos,
} from './FavoritosContext'
import { guardarFavorito, listarFavoritos, quitarFavorito, type Favorito, type TipoFavorito } from './favoritosApi'

/** La lista y su estado pertenecen a un turista: si cambia la sesión, lo de antes no vale (DEC-93). */
interface Datos {
  dueno: string | null
  estado: EstadoFavoritos
  elementos: Favorito[]
}

const mismo = (tipo: TipoFavorito, id: string) => (favorito: Favorito) => favorito.tipo === tipo && favorito.id === id

/** Clave i18n del anuncio de un error al marcar o quitar. */
function claveError(error: unknown, tipo: TipoFavorito): string {
  if (error instanceof ApiError) {
    if (error.codigo === 'LIMITE_FAVORITOS') return 'favoritos.anuncios.limite'
    if (error.status === 404) {
      return tipo === 'Atractivo' ? 'favoritos.anuncios.lugarNoDisponible' : 'favoritos.anuncios.servicioNoDisponible'
    }
  }
  return 'favoritos.anuncios.error'
}

/**
 * Favoritos del turista (RF54, DEC-93), compartidos por los corazones de WF-05 y WF-06 y por «Mis favoritos» (WF-17):
 * - una sola carga de GET /favoritos por sesión, diferida hasta que monta el primer consumidor; si falla, el siguiente
 *   consumidor que monta lo vuelve a intentar;
 * - marcar y quitar son optimistas: el cambio se ve al instante y se revierte si el servidor lo rechaza;
 * - al cerrar la sesión (o si cambia el usuario) todo se descarta, y las respuestas que lleguen tarde se ignoran.
 */
export function FavoritosProvider({ children }: { children: ReactNode }) {
  const { sesion } = useSesion()
  const modo: ModoFavorito =
    sesion.estado === 'anonimo'
      ? 'visitante'
      : sesion.estado === 'autenticado' && sesion.usuario.rol === 'Turista'
        ? 'turista'
        : null
  const dueno = modo === 'turista' && sesion.estado === 'autenticado' ? sesion.usuario.idUsuario : null

  const [guardados, setDatos] = useState<Datos>({ dueno: null, estado: 'sinCargar', elementos: [] })
  const datos = useMemo<Datos>(
    () => guardados.dueno === dueno ? guardados : { dueno, estado: 'sinCargar', elementos: [] },
    [guardados, dueno],
  )
  const [pendientes, setPendientes] = useState<ReadonlySet<string>>(new Set())
  const [anuncio, setAnuncio] = useState<AnuncioFavoritos | null>(null)

  // Refs que leen las acciones (los efectos de los hijos corren antes que los del proveedor; los de diseño, antes).
  const duenoActual = useRef(dueno)
  const datosActuales = useRef(datos)
  const carga = useRef<{ dueno: string | null; estado: EstadoFavoritos }>({ dueno: null, estado: 'sinCargar' })
  const enCurso = useRef(new Set<string>())
  const numeroAnuncio = useRef(0)
  useLayoutEffect(() => {
    datosActuales.current = datos
    if (duenoActual.current !== dueno) {
      // Otra sesión (cierre, 401 o un nuevo inicio, aunque sea del mismo turista): se empieza de cero.
      duenoActual.current = dueno
      carga.current = { dueno, estado: 'sinCargar' }
      setDatos({ dueno, estado: 'sinCargar', elementos: [] })
      enCurso.current = new Set()
      setPendientes(new Set())
      setAnuncio(null)
    }
  }, [datos, dueno])

  const anunciar = useCallback((clave: string, error = false) => {
    numeroAnuncio.current += 1
    setAnuncio({ clave, error, n: numeroAnuncio.current })
  }, [])

  const actualizar = useCallback((dueno: string, cambio: (elementos: Favorito[]) => Favorito[]) => {
    setDatos((previos) => (previos.dueno === dueno ? { ...previos, elementos: cambio(previos.elementos) } : previos))
  }, [])

  const marcarPendiente = useCallback((clave: string, activo: boolean) => {
    if (activo) enCurso.current.add(clave)
    else enCurso.current.delete(clave)
    setPendientes(new Set(enCurso.current))
  }, [])

  const asegurarCarga = useCallback(() => {
    const dueno = duenoActual.current
    if (!dueno) return
    const { dueno: de, estado } = carga.current
    if (de === dueno && (estado === 'cargando' || estado === 'ok')) return
    carga.current = { dueno, estado: 'cargando' }
    setDatos({ dueno, estado: 'cargando', elementos: [] })
    listarFavoritos()
      .then(({ elementos }) => {
        if (duenoActual.current !== dueno) return
        carga.current = { dueno, estado: 'ok' }
        setDatos({ dueno, estado: 'ok', elementos })
      })
      .catch(() => {
        // Un 401 ya cerró la sesión (alExpirarSesion); otro error deja la lista para reintentar al siguiente montaje.
        if (duenoActual.current !== dueno) return
        carga.current = { dueno, estado: 'error' }
        setDatos({ dueno, estado: 'error', elementos: [] })
      })
  }, [])

  const quitarDe = useCallback(
    async (dueno: string, tipo: TipoFavorito, id: string): Promise<boolean> => {
      const clave = claveFavorito(tipo, id)
      const anteriores = datosActuales.current.elementos
      const indice = anteriores.findIndex(mismo(tipo, id))
      const quitado = anteriores[indice]
      marcarPendiente(clave, true)
      actualizar(dueno, (elementos) => elementos.filter((favorito) => !mismo(tipo, id)(favorito)))
      try {
        await quitarFavorito(tipo, id)
        if (duenoActual.current === dueno) anunciar('favoritos.anuncios.quitado')
        return true
      } catch (error) {
        if (duenoActual.current !== dueno) return false
        if (quitado) {
          actualizar(dueno, (elementos) => [...elementos.slice(0, indice), quitado, ...elementos.slice(indice)])
        }
        anunciar(claveError(error, tipo), true)
        return false
      } finally {
        marcarPendiente(clave, false)
      }
    },
    [actualizar, anunciar, marcarPendiente],
  )

  const guardarDe = useCallback(
    async (dueno: string, { tipo, id, nombre }: ReferenciaFavorito): Promise<boolean> => {
      const clave = claveFavorito(tipo, id)
      const provisional: Favorito = {
        tipo,
        id,
        nombre,
        categoria: null,
        imagen: null,
        fechaCreacion: new Date().toISOString(),
        disponible: true,
      }
      marcarPendiente(clave, true)
      actualizar(dueno, (elementos) => [provisional, ...elementos.filter((favorito) => !mismo(tipo, id)(favorito))])
      try {
        const guardado = await guardarFavorito(tipo, id)
        if (duenoActual.current !== dueno) return false
        actualizar(dueno, (elementos) => elementos.map((favorito) => (mismo(tipo, id)(favorito) ? guardado : favorito)))
        anunciar('favoritos.anuncios.guardado')
        return true
      } catch (error) {
        if (duenoActual.current !== dueno) return false
        actualizar(dueno, (elementos) => elementos.filter((favorito) => !mismo(tipo, id)(favorito)))
        anunciar(claveError(error, tipo), true)
        return false
      } finally {
        marcarPendiente(clave, false)
      }
    },
    [actualizar, anunciar, marcarPendiente],
  )

  const esFavorito = useCallback(
    (tipo: TipoFavorito, id: string) => datos.elementos.some(mismo(tipo, id)),
    [datos.elementos],
  )

  const alternar = useCallback(
    async (referencia: ReferenciaFavorito): Promise<boolean> => {
      const dueno = duenoActual.current
      const actual = datosActuales.current
      const era = actual.elementos.some(mismo(referencia.tipo, referencia.id))
      // Sin la lista no se sabe el estado: el corazón está deshabilitado y no hace nada.
      if (!dueno || actual.estado !== 'ok' || enCurso.current.has(claveFavorito(referencia.tipo, referencia.id))) {
        return era
      }
      return era ? !(await quitarDe(dueno, referencia.tipo, referencia.id)) : guardarDe(dueno, referencia)
    },
    [guardarDe, quitarDe],
  )

  const quitar = useCallback(
    async (tipo: TipoFavorito, id: string): Promise<boolean> => {
      const dueno = duenoActual.current
      if (!dueno || enCurso.current.has(claveFavorito(tipo, id))) return false
      return quitarDe(dueno, tipo, id)
    },
    [quitarDe],
  )

  const limpiarAnuncio = useCallback(() => setAnuncio(null), [])

  const valor = useMemo<ValorFavoritos>(
    () => ({
      modo,
      estado: datos.estado,
      elementos: datos.elementos,
      asegurarCarga,
      esFavorito,
      pendiente: (tipo, id) => pendientes.has(claveFavorito(tipo, id)),
      alternar,
      quitar,
      anuncio: dueno ? anuncio : null,
      limpiarAnuncio,
    }),
    [modo, dueno, datos.estado, datos.elementos, asegurarCarga, esFavorito, pendientes, alternar, quitar, anuncio, limpiarAnuncio],
  )

  return <FavoritosContext.Provider value={valor}>{children}</FavoritosContext.Provider>
}
