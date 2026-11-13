import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { AccionNoDisponible } from '../../shared/components/BotonNoDisponible'
import { useSesion } from '../auth/useSesion'
import { AvisoFavoritos } from '../favoritos/AvisoFavoritos'
import { useFavoritos } from '../favoritos/useFavoritos'
import { useDisponibilidadResumen } from '../reservas/useDisponibilidadResumen'
import type { CriteriosBusqueda, Orden, PaginaOferta, ResultadoOferta, Ubicacion } from './catalogoApi'
import { BotonUbicacion } from './componentes/BotonUbicacion'
import { EstadoCatalogo } from './componentes/EstadoCatalogo'
import { FiltrosOferta, type CambiosFiltros } from './componentes/FiltrosOferta'
import { MapaOferta, claveResultado, type FavoritoPopup } from './componentes/MapaOferta'
import { Paginacion } from '../../shared/components/Paginacion'
import { TarjetaOferta } from './componentes/TarjetaOferta'
import { UltimasBusquedas, type EstadoRepetir } from './componentes/UltimasBusquedas'
import { escribirCriterios, leerCriterios, sinMasFiltrosDescartados } from './criteriosUrl'
import { cuerpoHistorial } from './historialApi'
import { useBusquedaOferta } from './useBusquedaOferta'
import { useEsEscritorio } from './useEsEscritorio'
import { useHistorialBusquedas } from './useHistorialBusquedas'
import { useUbicacion } from './useUbicacion'
import './catalogo.css'

/** Órdenes que la API soporta; «Precio» y «Nombre» son Mejora de diseño (WF-05 no los muestra). */
const ORDENES_DISPONIBLES: Orden[] = ['PrecioAscendente', 'PrecioDescendente', 'Nombre']

const SIN_RESULTADOS: ResultadoOferta[] = []

type Vista = 'lista' | 'mapa'

/**
 * WF-05: exploración (pública). Los filtros, el orden y la página viven en la URL; la ubicación, solo en memoria.
 * La lista de resultados es el contenido principal y el mapa es complementario: en escritorio se ven lado a lado; en
 * una columna, el selector «Lista / Mapa» muestra uno a la vez.
 *
 * Con la sesión de un turista (B1): «Últimas búsquedas» (RF60) y el corazón de favoritos en tarjetas y popups (RF54).
 * Cada búsqueda distinta (sin contar la página) que llega con éxito se registra en el historial, aparte de la búsqueda
 * pública y sin coordenadas (DEC-95). Al repetir una que usaba la ubicación, se vuelve a pedir; si no se comparte, la
 * búsqueda corre sin distancia ni orden por cercanía y se avisa en la región de ubicación (DEC-96, RF38).
 */
export function ExplorarPage() {
  const { t } = useTranslation()
  const id = useId()
  const [parametros, setParametros] = useSearchParams()
  const ubicacionRuta = useLocation()
  const irA = useNavigate()
  const criterios = useMemo(() => leerCriterios(parametros), [parametros])
  const geo = useUbicacion()
  const ubicacion: Ubicacion | undefined = geo.estado === 'disponible' ? geo.ubicacion : undefined
  const busqueda = useBusquedaOferta(useMemo(() => ({ ...criterios, ubicacion }), [criterios, ubicacion]))
  const esEscritorio = useEsEscritorio()
  const { sesion } = useSesion()
  const esTurista = sesion.estado === 'autenticado' && sesion.usuario.rol === 'Turista'
  const historial = useHistorialBusquedas(esTurista)
  const favoritos = useFavoritos()
  const [vista, setVista] = useState<Vista>('lista')
  const [resaltada, setResaltada] = useState<string | null>(null)
  const tarjetas = useRef(new Map<string, HTMLElement>())

  const tituloResultados = useRef<HTMLHeadingElement>(null)
  const enfocarAlCargar = useRef(false)
  useEffect(() => {
    if (busqueda.estado !== 'cargando' && enfocarAlCargar.current) {
      enfocarAlCargar.current = false
      tituloResultados.current?.focus()
    }
  }, [busqueda.estado])

  // Con la ubicación nueva cambian el orden y las distancias: la página que se estaba viendo ya no corresponde.
  const ubicacionAtendida = useRef<Ubicacion | undefined>(undefined)
  useEffect(() => {
    if (!ubicacion || ubicacion === ubicacionAtendida.current) return
    ubicacionAtendida.current = ubicacion
    if (criterios.pagina > 1) setParametros(escribirCriterios({ ...criterios, pagina: 1 }), { replace: true })
  }, [ubicacion, criterios, setParametros])

  // RF60 (DEC-95): se registra cada resultado nuevo que llega con éxito (una página distinta de la ya vista), con los
  // criterios vigentes sin la página. Si llega mientras se pide la ubicación, espera: si la ubicación llega, la
  // búsqueda cambia y se registra esa; si no, esta. La misma clave dos veces seguidas se registra una sola vez.
  const usarUbicacion = ubicacion !== undefined
  const claveHistorial = useMemo(() => {
    const cuerpo = cuerpoHistorial(criterios, usarUbicacion)
    return cuerpo ? JSON.stringify(cuerpo) : null
  }, [criterios, usarUbicacion])
  const paginaVista = useRef<PaginaOferta | null>(null)
  const porRegistrar = useRef<string | null>(null)
  const registrada = useRef<string | null>(null)
  const { registrar } = historial
  const paginaRecibida = busqueda.estado === 'ok' ? busqueda.pagina : null
  useEffect(() => {
    if (paginaRecibida && paginaRecibida !== paginaVista.current) {
      paginaVista.current = paginaRecibida
      porRegistrar.current = claveHistorial
    }
    if (!porRegistrar.current || geo.estado === 'solicitando') return
    const clave = porRegistrar.current
    porRegistrar.current = null
    // Los criterios ya cambiaron (p. ej. llegó la ubicación): se registrará el resultado de la búsqueda nueva.
    if (clave !== claveHistorial || !esTurista || registrada.current === clave) return
    registrada.current = clave
    registrar(JSON.parse(clave))
  }, [paginaRecibida, claveHistorial, esTurista, geo.estado, registrar])

  // Repetir una búsqueda de «Últimas búsquedas» (DEC-96): una vez por entrada del historial del navegador.
  const repetida = useRef<string | null>(null)
  const esperandoUbicacion = useRef(false)
  const [sinUbicacionAlRepetir, setSinUbicacionAlRepetir] = useState(false)
  const estadoRepetir = ubicacionRuta.state as Partial<EstadoRepetir> | null
  const { estado: estadoGeo, centrar } = geo
  useEffect(() => {
    if (!estadoRepetir?.repetirBusqueda || repetida.current === ubicacionRuta.key) return
    repetida.current = ubicacionRuta.key
    enfocarAlCargar.current = true
    setSinUbicacionAlRepetir(false)
    if (!estadoRepetir.pedirUbicacion || estadoGeo === 'disponible') return
    if (estadoGeo === 'noSoportada') {
      setSinUbicacionAlRepetir(true)
      return
    }
    esperandoUbicacion.current = true
    centrar()
  }, [estadoRepetir, ubicacionRuta.key, estadoGeo, centrar])
  useEffect(() => {
    if (!esperandoUbicacion.current || estadoGeo === 'solicitando' || estadoGeo === 'inactiva') return
    esperandoUbicacion.current = false
    setSinUbicacionAlRepetir(estadoGeo !== 'disponible')
  }, [estadoGeo])

  // Una URL con «Más filtros» de otra categoría (o sin categoría) se limpia sin mostrar un error: la API los rechazaría.
  useEffect(() => {
    const limpios = sinMasFiltrosDescartados(parametros)
    if (limpios) setParametros(limpios, { replace: true })
  }, [parametros, setParametros])

  const navegar = (nuevos: CriteriosBusqueda, enfocarResultados = false) => {
    enfocarAlCargar.current = enfocarResultados
    setParametros(escribirCriterios(nuevos))
  }
  const quitarFiltros = () => setParametros(new URLSearchParams())

  // Sin orden en la URL rige el de la API: por distancia con ubicación y por nombre sin ella. Un orden por distancia
  // sin ubicación (p. ej. de un enlace compartido) no aplica.
  const ordenEfectivo: Orden =
    criterios.orden && (criterios.orden !== 'Distancia' || ubicacion) ? criterios.orden : ubicacion ? 'Distancia' : 'Nombre'

  const pagina = busqueda.estado === 'ok' ? busqueda.pagina : null
  const resultadosMapa = pagina ? pagina.elementos : busqueda.estado === 'error' ? SIN_RESULTADOS : null
  const disponibilidad = useDisponibilidadResumen(pagina ? pagina.elementos : null)

  // «Ver detalle» del popup del mapa (RF23): como el de la tarjeta, recuerda la búsqueda para volver a ella.
  const verDetalle = (ruta: string) => irA(ruta, { state: { volverA: ubicacionRuta.pathname + ubicacionRuta.search } })

  const seleccionarDesdeMapa = (clave: string) => {
    setResaltada(clave)
    const tarjeta = tarjetas.current.get(clave)
    tarjeta?.focus({ preventScroll: true })
    tarjeta?.scrollIntoView?.({ block: 'nearest' })
  }

  const botonUbicacion = <BotonUbicacion estado={geo.estado} onCentrar={geo.centrar} />

  // Corazón del popup (DEC-94): las mismas reglas que el de la tarjeta; con otro rol, ninguno.
  const favoritoPopup: FavoritoPopup | undefined = favoritos?.modo
    ? {
        modo: favoritos.modo,
        listo: favoritos.estado === 'ok',
        esFavorito: (resultado) => favoritos.esFavorito(resultado.tipo, resultado.id),
        alternar: (resultado) => favoritos.alternar({ tipo: resultado.tipo, id: resultado.id, nombre: resultado.nombre }),
        irALogin: () =>
          irA('/login', { state: { desde: ubicacionRuta.pathname + ubicacionRuta.search + ubicacionRuta.hash } }),
      }
    : undefined

  return (
    <div className="explorar">
      <nav className="migas" aria-label={t('explorar.migas')}>
        <ol>
          <li>
            <Link to="/">Samaria360</Link>
          </li>
          <li aria-current="page">{t('explorar.migaExploracion')}</li>
        </ol>
      </nav>
      <h1 className="explorar__titulo">{t('explorar.titulo')}</h1>
      <p className="explorar__apoyo">{t('explorar.apoyo')}</p>

      <FiltrosOferta
        key={parametros.toString()}
        criterios={criterios}
        hayUbicacion={ubicacion !== undefined}
        onAplicar={(cambios: CambiosFiltros) => navegar({ ...criterios, ...cambios, pagina: 1 })}
        onQuitarCategoria={() => navegar({ ...criterios, categoria: undefined, masFiltros: undefined, pagina: 1 })}
        onQuitarMasFiltro={(restantes) => navegar({ ...criterios, masFiltros: restantes, pagina: 1 })}
      />

      {esTurista && <UltimasBusquedas elementos={historial.elementos} />}

      <div className="explorar__barra">
        {/* En una columna: selector de vista y botón de ubicación a mano también en la lista. */}
        <div className="vista-selector" role="group" aria-label={t('vista.selector')}>
          {(['lista', 'mapa'] as const).map((opcion) => (
            <button
              key={opcion}
              type="button"
              className="vista-selector__opcion"
              aria-pressed={vista === opcion}
              onClick={() => setVista(opcion)}
            >
              {t(`vista.${opcion}`)}
            </button>
          ))}
        </div>
        {!esEscritorio && botonUbicacion}
        <p role="status" className="aviso-ubicacion">
          {geo.estado === 'disponible' && (
            <>
              {t('ubicacion.usando')}
              {geo.lejos && <> {t('ubicacion.lejos')}</>}
            </>
          )}
          {/* Un solo mensaje a la vez: al repetir una búsqueda sin la ubicación, este reemplaza al error de ubicación. */}
          {sinUbicacionAlRepetir
            ? t('historial.sinUbicacion')
            : geo.estado === 'error' && t(`ubicacion.${geo.motivo}`)}
        </p>
      </div>

      <div className="explorar__cuerpo" data-vista={vista}>
        <section className="explorar__resultados" aria-labelledby={`${id}-resultados`}>
          <div className="resultados__encabezado">
            <div aria-live="polite" aria-atomic="true">
              <h2 id={`${id}-resultados`} ref={tituloResultados} tabIndex={-1}>
                {pagina ? t('explorar.contador', { count: pagina.totalElementos }) : t('explorar.resultados')}
              </h2>
            </div>

            <div className="orden" role="group" aria-label={t('explorar.ordenar')}>
              {ubicacion ? (
                <button
                  type="button"
                  className="orden__opcion"
                  aria-pressed={ordenEfectivo === 'Distancia'}
                  onClick={() => navegar({ ...criterios, orden: 'Distancia', pagina: 1 })}
                >
                  {t('explorar.ordenes.Distancia')}
                </button>
              ) : (
                <button
                  type="button"
                  className="orden__opcion deshabilitado"
                  aria-disabled="true"
                  aria-describedby={`${id}-orden-sin-ubicacion`}
                  onClick={(evento) => evento.preventDefault()}
                >
                  {t('explorar.ordenes.Distancia')}
                </button>
              )}
              <AccionNoDisponible className="orden__opcion">
                {t('explorar.ordenes.Afluencia')}
              </AccionNoDisponible>
              <AccionNoDisponible className="orden__opcion">
                {t('explorar.ordenes.Calificacion')}
              </AccionNoDisponible>
              {ORDENES_DISPONIBLES.map((orden) => (
                <button
                  key={orden}
                  type="button"
                  className="orden__opcion"
                  aria-pressed={ordenEfectivo === orden}
                  onClick={() => navegar({ ...criterios, orden, pagina: 1 })}
                >
                  {t(`explorar.ordenes.${orden}`)}
                </button>
              ))}
            </div>
            {!ubicacion && (
              <p id={`${id}-orden-sin-ubicacion`} className="filtro__ayuda">
                {t('explorar.sinUbicacion')}
              </p>
            )}
            <AvisoFavoritos />
          </div>

          {busqueda.estado === 'cargando' && <EstadoCatalogo tipo="cargando" />}
          {busqueda.estado === 'error' && (
            <EstadoCatalogo tipo={busqueda.error} onReintentar={busqueda.reintentar} onQuitarFiltros={quitarFiltros} />
          )}
          {pagina &&
            (pagina.elementos.length === 0 ? (
              pagina.totalElementos > 0 ? (
                <EstadoCatalogo tipo="fueraDeRango" onPrimeraPagina={() => navegar({ ...criterios, pagina: 1 }, true)} />
              ) : (
                <EstadoCatalogo tipo="vacio" onQuitarFiltros={quitarFiltros} />
              )
            ) : (
              <>
                <ol className="lista-resultados">
                  {pagina.elementos.map((resultado) => {
                    const clave = claveResultado(resultado)
                    return (
                      <li key={clave}>
                        <TarjetaOferta
                          ref={(elemento) => {
                            if (elemento) tarjetas.current.set(clave, elemento)
                            return () => {
                              tarjetas.current.delete(clave)
                            }
                          }}
                          resultado={resultado}
                          variante="lista"
                          disponibilidad={disponibilidad?.[resultado.id] ?? null}
                          resaltada={resaltada === clave}
                          onResaltar={(activa) =>
                            setResaltada((actual) => (activa ? clave : actual === clave ? null : actual))
                          }
                        />
                      </li>
                    )
                  })}
                </ol>
                <Paginacion
                  actual={criterios.pagina}
                  total={pagina.totalPaginas}
                  onCambiar={(numero) => navegar({ ...criterios, pagina: numero }, true)}
                />
              </>
            ))}
        </section>

        <aside className="explorar__mapa" aria-labelledby={`${id}-mapa`}>
          <h2 id={`${id}-mapa`} className="explorar__mapa-titulo">
            {t('explorar.mapa')}
          </h2>
          <MapaOferta
            resultados={resultadosMapa}
            ubicacion={ubicacion}
            ubicacionLejana={geo.estado === 'disponible' && geo.lejos}
            centrado={geo.centrado}
            visible={esEscritorio || vista === 'mapa'}
            resaltada={resaltada}
            onSeleccionar={seleccionarDesdeMapa}
            onVerDetalle={verDetalle}
            favorito={favoritoPopup}
          />
          <div className="leyenda-afluencia">
            <p className="leyenda-afluencia__titulo">
              {t('explorar.leyendaAfluencia')}: <span>{t('explorar.sinDatosAfluencia')}</span>
            </p>
            <ul aria-label={t('explorar.leyendaAfluencia')}>
              <li className="leyenda-afluencia__item leyenda-afluencia__item--bajo">{t('explorar.nivelBajo')}</li>
              <li className="leyenda-afluencia__item leyenda-afluencia__item--medio">{t('explorar.nivelMedio')}</li>
              <li className="leyenda-afluencia__item leyenda-afluencia__item--alto">{t('explorar.nivelAlto')}</li>
            </ul>
          </div>
          {esEscritorio && botonUbicacion}
        </aside>
      </div>
    </div>
  )
}
