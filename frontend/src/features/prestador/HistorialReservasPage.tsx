import { useCallback, useEffect, useId, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { ApiError, servicioNoDisponible } from '../../shared/api/httpClient'
import { Paginacion } from '../../shared/components/Paginacion'
import { SinPermiso } from '../../shared/components/SinPermiso'
import { etiquetaCategoria } from '../catalogo/componentes/etiquetas'
import { validarCriteriosReservas } from '../reservas/filtrosReservas'
import { ESTADOS_RESERVA, type EstadoReserva } from '../reservas/reservasApi'
import { TarjetaReservaPrestador } from './componentes/TarjetaReservaPrestador'
import {
  escribirCriteriosHistorial,
  hayFiltrosHistorial,
  leerCriteriosHistorial,
  servicioInvalido,
  type CriteriosHistorialPrestador,
} from './criteriosHistorialPrestador'
import { consultarReservasPrestador, type PaginaReservasPrestador } from './reservasPrestadorApi'
import { MAXIMO_PAGINAS_SELECTOR, listarServiciosParaFiltro, type ServicioResumen } from './serviciosPrestadorApi'
import '../catalogo/catalogo.css'
import '../reservas/reservas.css'
import './prestador.css'

type ErrorHistorial = 'datosInvalidos' | 'sinPermiso' | 'noDisponible' | 'red' | 'otro'

type EstadoHistorial =
  | { estado: 'inactivo' }
  | { estado: 'cargando' }
  | { estado: 'ok'; pagina: PaginaReservasPrestador }
  | { estado: 'error'; error: ErrorHistorial; campos: Record<string, string> }

function clasificar(error: unknown): { error: ErrorHistorial; campos: Record<string, string> } {
  if (!(error instanceof ApiError)) return { error: 'otro', campos: {} }
  if (error.codigo === 'DATOS_INVALIDOS') {
    return { error: 'datosInvalidos', campos: (error.problema?.campos ?? {}) as Record<string, string> }
  }
  if (error.status === 403) return { error: 'sinPermiso', campos: {} }
  if (servicioNoDisponible(error)) return { error: 'noDisponible', campos: {} }
  if (error.codigo === 'ERROR_RED') return { error: 'red', campos: {} }
  return { error: 'otro', campos: {} }
}

/** El historial con los criterios de la URL; no consulta si los filtros no son válidos. */
function useHistorialPrestador(criterios: CriteriosHistorialPrestador, habilitado: boolean) {
  const [estado, setEstado] = useState<EstadoHistorial>({ estado: 'inactivo' })
  const [intento, setIntento] = useState(0)
  const clave = `${criterios.desde}|${criterios.hasta}|${criterios.estados.join(',')}|${criterios.servicio}|${criterios.pagina}`

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
      servicio: criterios.servicio || undefined,
    }
    consultarReservasPrestador(filtros, criterios.pagina)
      .then((pagina) => {
        if (vigente) setEstado({ estado: 'ok', pagina })
      })
      .catch((error: unknown) => {
        if (vigente) setEstado({ estado: 'error', ...clasificar(error) })
      })
    return () => {
      vigente = false
    }
    // La clave incluye fechas, estados, servicio y página; objetos equivalentes no repiten la petición.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, habilitado, intento])

  const recargar = useCallback(() => setIntento((n) => n + 1), [])
  return { ...estado, recargar } as EstadoHistorial & { recargar: () => void }
}

type CargaServicios = { estado: 'cargando' } | { estado: 'error' } | { estado: 'ok'; servicios: ServicioResumen[]; truncado: boolean }

/**
 * Los servicios del filtro (DEC-204): todas las páginas de «Mis servicios», hasta 200. Un fallo deshabilita el
 * selector, pero no el historial.
 */
function useServiciosFiltro() {
  const [carga, setCarga] = useState<CargaServicios>({ estado: 'cargando' })
  useEffect(() => {
    let vigente = true
    listarServiciosParaFiltro()
      .then(({ servicios, truncado }) => {
        if (vigente) setCarga({ estado: 'ok', servicios, truncado })
      })
      .catch(() => {
        if (vigente) setCarga({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [])
  return carga
}

/**
 * WF-23 «Historial de reservas» del Prestador (RF12, CU «Consultar historial»; DEC-196, DEC-204), solo para el
 * Prestador. Filtros en la URL (DEC-169): fechas de la actividad (como mucho 366 días), estado (uno o varios) y
 * servicio; 20 por página. Las reservas no muestran ningún dato del Turista.
 */
export function HistorialReservasPage() {
  const { t } = useTranslation()
  const id = useId()
  const [parametros, setParametros] = useSearchParams()
  const criterios = useMemo(() => leerCriteriosHistorial(parametros), [parametros])
  const fallos = validarCriteriosReservas(criterios)
  const consulta = useHistorialPrestador(criterios, Object.keys(fallos).length === 0)
  const servicios = useServiciosFiltro()

  // Un `servicio` que no es un ObjectId se quita de la URL sin dejar una entrada en el historial.
  useEffect(() => {
    if (servicioInvalido(parametros)) setParametros(escribirCriteriosHistorial(criterios), { replace: true })
  }, [parametros, criterios, setParametros])

  if (consulta.estado === 'error' && consulta.error === 'sinPermiso') return <SinPermiso />

  const filtrar = (cambios: Partial<CriteriosHistorialPrestador>) =>
    setParametros(escribirCriteriosHistorial({ ...criterios, ...cambios, pagina: 1 }))
  const alternarEstado = (estado: EstadoReserva, marcado: boolean) =>
    filtrar({
      estados: marcado ? [...criterios.estados, estado] : criterios.estados.filter((actual) => actual !== estado),
    })

  const campos = consulta.estado === 'error' ? consulta.campos : {}
  const errorHasta = fallos.hasta ?? campos.hasta
  const errorDesde = campos.desde
  const errorEstado = campos.estado
  const errorServicio = campos.servicio
  const textoValidacion = (codigo: string) =>
    t(`misReservas.validacion.${codigo}`, { defaultValue: t('misReservas.validacion.FORMATO') })
  const pagina = consulta.estado === 'ok' ? consulta.pagina : null
  const idServicio = `${id}-servicio`
  const ayudaServicio =
    servicios.estado === 'error'
      ? t('prestador.historial.serviciosNoDisponibles')
      : servicios.estado === 'ok' && servicios.truncado
        ? t('prestador.historial.limiteServicios', { n: MAXIMO_PAGINAS_SELECTOR * 10 })
        : null
  // Un servicio de la URL que no está en la lista (más allá del tope) igual se puede ver y quitar.
  const servicioFueraDeLista =
    criterios.servicio !== '' && servicios.estado === 'ok' && !servicios.servicios.some((s) => s.id === criterios.servicio)

  return (
    <div className="mis-reservas prestador-historial">
      <h1>{t('prestador.historial.titulo')}</h1>
      <p className="mis-reservas__apoyo">{t('prestador.historial.apoyo')}</p>

      <form className="mis-reservas__filtros" onSubmit={(evento) => evento.preventDefault()} noValidate>
        <fieldset className="reserva__grupo">
          <legend>{t('misReservas.filtros')}</legend>
          <div className="reserva__campos">
            <div className="campo">
              <label htmlFor={`${id}-desde`}>{t('misReservas.desde')}</label>
              <div className="campo__control">
                <input
                  id={`${id}-desde`}
                  type="date"
                  value={criterios.desde}
                  aria-invalid={errorDesde ? true : undefined}
                  aria-describedby={errorDesde ? `${id}-desde-error` : undefined}
                  onChange={(evento) => filtrar({ desde: evento.target.value })}
                />
              </div>
              {errorDesde && (
                <p id={`${id}-desde-error`} className="campo__error">
                  {textoValidacion(errorDesde)}
                </p>
              )}
            </div>
            <div className="campo">
              <label htmlFor={`${id}-hasta`}>{t('misReservas.hasta')}</label>
              <div className="campo__control">
                <input
                  id={`${id}-hasta`}
                  type="date"
                  value={criterios.hasta}
                  min={criterios.desde || undefined}
                  aria-invalid={errorHasta ? true : undefined}
                  aria-describedby={errorHasta ? `${id}-hasta-error` : undefined}
                  onChange={(evento) => filtrar({ hasta: evento.target.value })}
                />
              </div>
              {errorHasta && (
                <p id={`${id}-hasta-error`} className="campo__error" role="alert">
                  {textoValidacion(errorHasta)}
                </p>
              )}
            </div>
            <div className="campo">
              <label htmlFor={idServicio}>{t('prestador.historial.servicio')}</label>
              <div className="campo__control">
                <select
                  id={idServicio}
                  value={criterios.servicio}
                  disabled={servicios.estado !== 'ok'}
                  aria-invalid={errorServicio ? true : undefined}
                  aria-describedby={
                    [ayudaServicio && `${idServicio}-ayuda`, errorServicio && `${idServicio}-error`].filter(Boolean).join(' ') ||
                    undefined
                  }
                  onChange={(evento) => filtrar({ servicio: evento.target.value })}
                >
                  <option value="">{t('prestador.historial.todosServicios')}</option>
                  {servicioFueraDeLista && <option value={criterios.servicio}>{t('prestador.historial.servicioSeleccionado')}</option>}
                  {servicios.estado === 'ok' &&
                    servicios.servicios.map((servicio) => (
                      <option key={servicio.id} value={servicio.id}>
                        {`${servicio.nombreComercial} · ${etiquetaCategoria(t, servicio.categoria)}`}
                      </option>
                    ))}
                </select>
              </div>
              {ayudaServicio && (
                <p id={`${idServicio}-ayuda`} className="campo__ayuda">
                  {ayudaServicio}
                </p>
              )}
              {errorServicio && (
                <p id={`${idServicio}-error`} className="campo__error">
                  {textoValidacion(errorServicio)}
                </p>
              )}
            </div>
          </div>
          <fieldset className="mis-reservas__estados" aria-describedby={errorEstado ? `${id}-estado-error` : undefined}>
            <legend>{t('misReservas.estado')}</legend>
            <ul>
              {ESTADOS_RESERVA.map((estado) => (
                <li key={estado}>
                  <input
                    type="checkbox"
                    id={`${id}-${estado}`}
                    checked={criterios.estados.includes(estado)}
                    onChange={(evento) => alternarEstado(estado, evento.target.checked)}
                  />
                  <label htmlFor={`${id}-${estado}`}>{t(`misReservas.estados.${estado}`)}</label>
                </li>
              ))}
            </ul>
            {errorEstado && (
              <p id={`${id}-estado-error`} className="campo__error">
                {textoValidacion(errorEstado)}
              </p>
            )}
          </fieldset>
          {hayFiltrosHistorial(criterios) && (
            <button type="button" className="boton-secundario" onClick={() => setParametros(new URLSearchParams())}>
              {t('misReservas.quitarFiltros')}
            </button>
          )}
        </fieldset>
      </form>

      <section className="mis-reservas__resultados" aria-labelledby={`${id}-resultados`}>
        <div aria-live="polite" aria-atomic="true">
          <h2 id={`${id}-resultados`}>
            {pagina ? t('prestador.historial.contador', { count: pagina.totalElementos }) : t('misReservas.resultados')}
          </h2>
        </div>

        {consulta.estado === 'cargando' && (
          <p className="estado-carga" role="status" aria-busy="true">
            {t('prestador.historial.cargando')}
          </p>
        )}
        {consulta.estado === 'error' && consulta.error !== 'datosInvalidos' && (
          <div className="estado-catalogo estado-catalogo--error" role="alert">
            <p className="estado-catalogo__mensaje">{t(`prestador.historial.errores.${consulta.error}`)}</p>
            <button type="button" className="boton-secundario" onClick={consulta.recargar}>
              {t('catalogo.busqueda.reintentar')}
            </button>
          </div>
        )}
        {pagina &&
          (pagina.elementos.length === 0 ? (
            <p className="estado-catalogo__mensaje">
              {hayFiltrosHistorial(criterios) ? t('prestador.historial.sinResultados') : t('prestador.historial.sinReservas')}
            </p>
          ) : (
            <>
              <ol className="mis-reservas__lista">
                {pagina.elementos.map((reserva) => (
                  <li key={reserva.idReserva}>
                    <TarjetaReservaPrestador reserva={reserva} />
                  </li>
                ))}
              </ol>
              <Paginacion
                actual={criterios.pagina}
                total={pagina.totalPaginas}
                onCambiar={(numero) => setParametros(escribirCriteriosHistorial({ ...criterios, pagina: numero }))}
              />
            </>
          ))}
      </section>
    </div>
  )
}
