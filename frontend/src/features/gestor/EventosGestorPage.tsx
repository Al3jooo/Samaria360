import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Paginacion } from '../../shared/components/Paginacion'
import { SinPermiso } from '../../shared/components/SinPermiso'
import { formatearDia } from '../../shared/formato'
import type { PaginaEventos } from '../agenda/agendaApi'
import { formatearRango } from '../agenda/fechasEvento'
import { clasificarError, type EstadoCarga } from '../prestador/useMisServicios'
import {
  escribirCriteriosAtractivos,
  leerCriteriosAtractivos,
  type EstadoAnuncioGestor,
} from './criteriosAtractivosGestor'
import { listarEventos, type EventoGestion } from './eventosGestorApi'
import '../prestador/prestador.css'
import './gestor.css'

/** Listado del panel con la página de la URL; `recargar` es «Reintentar». */
function useEventosGestor(pagina: number) {
  const [estado, setEstado] = useState<EstadoCarga<PaginaEventos<EventoGestion>>>({ estado: 'cargando' })
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let vigente = true
    setEstado((actual) => (actual.estado === 'ok' ? actual : { estado: 'cargando' }))
    listarEventos(pagina)
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch((error: unknown) => {
        if (vigente) setEstado({ estado: 'error', error: clasificarError(error) })
      })
    return () => {
      vigente = false
    }
  }, [pagina, intento])

  const recargar = useCallback(() => setIntento((n) => n + 1), [])
  return { ...estado, recargar } as EstadoCarga<PaginaEventos<EventoGestion>> & { recargar: () => void }
}

/**
 * WF-25 «Eventos» (flujo «Consultar eventos registrados» del CU «Registrar evento en agenda», RF25), solo para el
 * Gestor (DEC-220, DEC-251). **Todos** los eventos, también los vencidos, del más reciente al más antiguo (DEC-238), en
 * páginas de 10 con la página en la URL (el mismo criterio que WF-24). Tabla como WF-24, sin acciones: no hay edición
 * ni borrado (DEC-215). Sin el Gestor (un uuid sin nombre en catalog_db) ni marca de «vencido», que exigiría calcular
 * «hoy» en el cliente (DEC-247).
 */
export function EventosGestorPage() {
  const { t, i18n } = useTranslation()
  const ubicacion = useLocation()
  const navegarRuta = useNavigate()
  const [parametros, setParametros] = useSearchParams()
  const criterios = useMemo(() => leerCriteriosAtractivos(parametros), [parametros])
  const lista = useEventosGestor(criterios.pagina)
  const [anuncio] = useState<string | null>(() => {
    const llegado = (ubicacion.state as EstadoAnuncioGestor | null)?.anuncio
    return llegado ? t(`gestor.eventos.panel.anuncios.${llegado}`) : null
  })

  // El anuncio de la navegación se muestra una vez: se quita del historial.
  useEffect(() => {
    if ((ubicacion.state as EstadoAnuncioGestor | null)?.anuncio) {
      navegarRuta(ubicacion.pathname + ubicacion.search, { replace: true, state: null })
    }
    // Consume el anuncio inicial; los filtros posteriores no deben volver a reemplazar el historial.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Una página que no es válida se normaliza en la URL.
  useEffect(() => {
    const normalizados = escribirCriteriosAtractivos(criterios)
    if (normalizados.toString() !== parametros.toString()) setParametros(normalizados, { replace: true })
  }, [criterios, parametros, setParametros])

  const datos = lista.estado === 'ok' ? lista.datos : null
  useEffect(() => {
    if (datos && datos.totalPaginas > 0 && criterios.pagina > datos.totalPaginas) {
      setParametros(escribirCriteriosAtractivos({ pagina: datos.totalPaginas }), { replace: true })
    }
  }, [datos, criterios, setParametros])

  if (lista.estado === 'error' && lista.error === 'sinPermiso') return <SinPermiso />

  const vacia = datos !== null && datos.elementos.length === 0

  return (
    <div className="prestador gestor">
      <div className="prestador__cabecera">
        <h1>{t('gestor.eventos.panel.titulo')}</h1>
        <Link className="boton-principal" to="/gestor/eventos/nuevo">
          {t('gestor.eventos.panel.registrar')}
        </Link>
      </div>
      <p className="prestador__apoyo">{t('gestor.eventos.panel.apoyo')}</p>

      {/* Región de estado de WF-25: anuncio, total y lista vacía. Siempre presente. */}
      <div role="status" aria-live="polite" className="prestador__region">
        {anuncio && <p className="gestor__anuncio">{anuncio}</p>}
        {datos && !vacia && <p>{t('gestor.eventos.panel.contador', { count: datos.totalElementos })}</p>}
        {vacia && <p>{t('gestor.eventos.panel.vacio')}</p>}
      </div>

      {lista.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('gestor.eventos.panel.cargando')}
        </p>
      )}
      {lista.estado === 'error' && (
        <div className="prestador__error-general" role="alert">
          <p>{t(`gestor.eventos.panel.errores.${lista.error}`)}</p>
          <button type="button" className="boton-secundario" onClick={lista.recargar}>
            {t('catalogo.busqueda.reintentar')}
          </button>
        </div>
      )}

      {datos && !vacia && (
        <div className="prestador__tabla-contenedor">
          {/* Roles explícitos: por debajo de 40 rem cada fila es una tarjeta (display: block) y conserva su semántica. */}
          <table className="prestador__tabla" role="table">
            <caption className="solo-lector">{t('gestor.eventos.panel.tabla')}</caption>
            <thead>
              <tr role="row">
                <th scope="col" role="columnheader">{t('gestor.eventos.panel.columnas.nombre')}</th>
                <th scope="col" role="columnheader">{t('gestor.eventos.panel.columnas.fechas')}</th>
                <th scope="col" role="columnheader">{t('gestor.eventos.panel.columnas.ubicacion')}</th>
                <th scope="col" role="columnheader">{t('gestor.eventos.panel.columnas.registrado')}</th>
              </tr>
            </thead>
            <tbody>
              {datos.elementos.map((evento) => (
                <tr key={evento.id} role="row">
                  <th scope="row" role="rowheader">{evento.nombre}</th>
                  <td role="cell" data-etiqueta={t('gestor.eventos.panel.columnas.fechas')}>
                    {formatearRango(evento.fechaInicio, evento.fechaFin, i18n.language)}
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.eventos.panel.columnas.ubicacion')}>
                    {evento.ubicacion}
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.eventos.panel.columnas.registrado')}>
                    {formatearDia(evento.fechaRegistro, i18n.language)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {datos && datos.totalPaginas > 1 && (
        <Paginacion
          actual={criterios.pagina}
          total={datos.totalPaginas}
          onCambiar={(pagina) => setParametros(escribirCriteriosAtractivos({ pagina }))}
        />
      )}
    </div>
  )
}
