import { RegistroOcupacion } from '../afluencia/RegistroOcupacion'
import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Paginacion } from '../../shared/components/Paginacion'
import { SinPermiso } from '../../shared/components/SinPermiso'
import { formatearDia } from '../../shared/formato'
import { ImagenOferta } from '../catalogo/componentes/ImagenOferta'
import { InsigniaEstadoOperativo } from '../catalogo/componentes/InsigniaEstadoOperativo'
import { etiquetaCategoria } from '../catalogo/componentes/etiquetas'
import { rutaDetalleAtractivo } from '../catalogo/detalle/navegacionDetalle'
import { clasificarError, type EstadoCarga } from '../prestador/useMisServicios'
import { listarAtractivos, type ElementoAtractivo, type PaginaAtractivos } from './atractivosGestorApi'
import { DialogoEstadoAtractivo } from './componentes/DialogoEstadoAtractivo'
import {
  escribirCriteriosAtractivos,
  leerCriteriosAtractivos,
  type EstadoAnuncioGestor,
} from './criteriosAtractivosGestor'
import '../prestador/prestador.css'
import './gestor.css'

/** Listado del panel con la página de la URL; `recargar` es «Reintentar» y lo usa el diálogo de estado. */
function useAtractivosGestor(pagina: number) {
  const [estado, setEstado] = useState<EstadoCarga<PaginaAtractivos>>({ estado: 'cargando' })
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let vigente = true
    setEstado((actual) => (actual.estado === 'ok' ? actual : { estado: 'cargando' }))
    listarAtractivos(pagina)
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
  return { ...estado, recargar } as EstadoCarga<PaginaAtractivos> & { recargar: () => void }
}

/**
 * WF-24 «Atractivos» (Panel del gestor; flujo «Consultar atractivos» del CU «Actualizar atractivo»), solo para el
 * Gestor (DEC-220, DEC-233). **Todos** los atractivos, no solo los que registró (DEC-206), del más reciente al más
 * antiguo, en páginas de 10 con la página en la URL. Tabla como WF-18 (por debajo de 40 rem cada fila es una tarjeta,
 * DEC-222). Los filtros de estado y de texto se ven deshabilitados: el contrato no los admite (G2, pendiente).
 * «Cambiar estado» abre el diálogo de RF33 (DEC-225); al guardar, la fila se actualiza y el anuncio lo dice.
 */
export function AtractivosGestorPage() {
  const { t, i18n } = useTranslation()
  const id = useId()
  const ubicacion = useLocation()
  const navegarRuta = useNavigate()
  const [parametros, setParametros] = useSearchParams()
  const criterios = useMemo(() => leerCriteriosAtractivos(parametros), [parametros])
  const lista = useAtractivosGestor(criterios.pagina)
  const [enDialogo, setEnDialogo] = useState<ElementoAtractivo | null>(null)
  const [anuncio, setAnuncio] = useState<string | null>(() => {
    const llegado = (ubicacion.state as EstadoAnuncioGestor | null)?.anuncio
    return llegado ? t(`gestor.panel.anuncios.${llegado}`) : null
  })
  const botones = useRef(new Map<string, HTMLButtonElement>())
  const titulo = useRef<HTMLHeadingElement>(null)

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
  const cerrarDialogo = (clave: string | null) => {
    const origen = enDialogo
    setEnDialogo(null)
    if (clave) setAnuncio(t(clave))
    setTimeout(() => (origen && botones.current.get(origen.id)?.focus()) || titulo.current?.focus(), 0)
  }

  return (
    <div className="prestador gestor">
      <div className="prestador__cabecera">
        <h1 ref={titulo} tabIndex={-1}>
          {t('gestor.panel.titulo')}
        </h1>
        <Link className="boton-principal" to="/gestor/atractivos/nuevo">
          {t('gestor.panel.registrar')}
        </Link>
      </div>
      <p className="prestador__apoyo">{t('gestor.panel.apoyo')}</p>

      <div className="prestador__columnas gestor__filtros" role="group" aria-disabled="true" aria-label={t('gestor.panel.filtros')}>
        <div className="campo">
          <label htmlFor={`${id}-estado`}>{t('gestor.panel.filtroEstado')}</label>
          <div className="campo__control">
            <select id={`${id}-estado`} disabled aria-describedby={`${id}-pendiente`}>
              <option>{t('gestor.panel.todos')}</option>
            </select>
          </div>
        </div>
        <div className="campo">
          <label htmlFor={`${id}-texto`}>{t('gestor.panel.filtroTexto')}</label>
          <div className="campo__control">
            <input id={`${id}-texto`} type="search" disabled aria-describedby={`${id}-pendiente`} />
          </div>
        </div>
        <p id={`${id}-pendiente`} className="campo__ayuda">
          {t('gestor.panel.filtrosPendientes')}
        </p>
      </div>

      {/* Región de estado de WF-24: anuncio, total y lista vacía. Siempre presente. */}
      <div role="status" aria-live="polite" className="prestador__region">
        {anuncio && <p className="gestor__anuncio">{anuncio}</p>}
        {datos && !vacia && <p>{t('gestor.panel.contador', { count: datos.totalElementos })}</p>}
        {vacia && <p>{t('gestor.panel.vacio')}</p>}
      </div>

      {lista.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('gestor.panel.cargando')}
        </p>
      )}
      {lista.estado === 'error' && (
        <div className="prestador__error-general" role="alert">
          <p>{t(`gestor.panel.errores.${lista.error}`)}</p>
          <button type="button" className="boton-secundario" onClick={lista.recargar}>
            {t('catalogo.busqueda.reintentar')}
          </button>
        </div>
      )}

      {datos && !vacia && (
        <div className="prestador__tabla-contenedor">
          {/* Roles explícitos: por debajo de 40 rem cada fila es una tarjeta (display: block) y conserva su semántica. */}
          <table className="prestador__tabla gestor__tabla" role="table">
            <caption className="solo-lector">{t('gestor.panel.tabla')}</caption>
            <thead>
              <tr role="row">
                <th scope="col" role="columnheader">
                  <span className="solo-lector">{t('gestor.panel.columnas.portada')}</span>
                </th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.nombre')}</th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.categoria')}</th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.estado')}</th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.capacidad')}</th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.registrado')}</th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.afluencia')}</th>
                <th scope="col" role="columnheader">{t('gestor.panel.columnas.acciones')}</th>
              </tr>
            </thead>
            <tbody>
              {datos.elementos.map((atractivo) => (
                <tr key={atractivo.id} role="row">
                  <td role="cell" className="gestor__portada">
                    <ImagenOferta
                      resultado={{ imagen: atractivo.imagen, categoria: atractivo.categoria ?? '', tipo: 'Atractivo' }}
                    />
                  </td>
                  <th scope="row" role="rowheader">{atractivo.nombre}</th>
                  <td role="cell" data-etiqueta={t('gestor.panel.columnas.categoria')}>
                    {etiquetaCategoria(t, atractivo.categoria) || '—'}
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.panel.columnas.estado')}>
                    {atractivo.estadoOperativo ? <InsigniaEstadoOperativo estado={atractivo.estadoOperativo} /> : '—'}
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.panel.columnas.capacidad')}>
                    {t('detalleAtractivo.personas', {
                      count: atractivo.limiteCapacidadCarga,
                      n: atractivo.limiteCapacidadCarga.toLocaleString(i18n.language),
                    })}
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.panel.columnas.registrado')}>
                    {atractivo.fechaRegistro ? formatearDia(atractivo.fechaRegistro, i18n.language) : '—'}
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.panel.columnas.afluencia')}>
                    {/* RF39 (DEC-306): el Gestor registra la ocupación actual del atractivo. */}
                    <RegistroOcupacion id={atractivo.id} nombre={atractivo.nombre} />
                  </td>
                  <td role="cell" data-etiqueta={t('gestor.panel.columnas.acciones')}>
                    <div className="prestador__acciones-fila">
                      <Link
                        to={`/gestor/atractivos/${encodeURIComponent(atractivo.id)}`}
                        aria-label={t('gestor.panel.editarDe', { nombre: atractivo.nombre })}
                      >
                        {t('gestor.panel.editar')}
                      </Link>
                      <button
                        ref={(elemento) => {
                          if (elemento) botones.current.set(atractivo.id, elemento)
                          else botones.current.delete(atractivo.id)
                        }}
                        type="button"
                        className="enlace"
                        aria-label={t('gestor.panel.cambiarEstadoDe', { nombre: atractivo.nombre })}
                        onClick={() => {
                          setAnuncio(null)
                          setEnDialogo(atractivo)
                        }}
                      >
                        {t('gestor.panel.cambiarEstado')}
                      </button>
                      <Link
                        to={rutaDetalleAtractivo(atractivo.id)}
                        aria-label={t('gestor.panel.verFichaDe', { nombre: atractivo.nombre })}
                      >
                        {t('gestor.panel.verFicha')}
                      </Link>
                    </div>
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

      {enDialogo && (
        <DialogoEstadoAtractivo
          atractivo={enDialogo}
          onCerrar={() => cerrarDialogo(null)}
          onCambiado={(actualizado) => {
            lista.recargar()
            cerrarDialogo(null)
            setAnuncio(null)
            setTimeout(
              () =>
                setAnuncio(
                  t('gestor.panel.anuncios.estadoCambiado', {
                    nombre: actualizado.nombre,
                    estado: actualizado.estadoOperativo ? t(`catalogo.estados.${actualizado.estadoOperativo}`) : '—',
                  }),
                ),
              0,
            )
          }}
          onNoEncontrado={() => {
            lista.recargar()
            cerrarDialogo('gestor.panel.anuncios.noEncontrado')
          }}
        />
      )}
    </div>
  )
}
