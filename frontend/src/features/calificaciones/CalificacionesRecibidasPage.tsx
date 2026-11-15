import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { formatearInstante } from '../reservas/fechasBogota'
import {
  consultarCalificacionesPrestador,
  formatearPromedio,
  type CalificacionesPrestador,
} from './calificacionesApi'
import { EstrellasFijas } from './Estrellas'
import './calificaciones.css'

type Estado = { estado: 'cargando' } | { estado: 'error' } | { estado: 'ok'; datos: CalificacionesPrestador }

/**
 * WF-33 «Calificaciones recibidas» (RF56; DEC-303), solo para el Prestador: las calificaciones visibles de sus
 * servicios con el promedio, un resumen por servicio y filtros por servicio y rango de fechas en la URL; 20 por página.
 * Sin datos del turista.
 */
export function CalificacionesRecibidasPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const servicio = parametros.get('servicio') ?? ''
  const desde = parametros.get('desde') ?? ''
  const hasta = parametros.get('hasta') ?? ''
  const pagina = Math.max(1, Number(parametros.get('pagina')) || 1)
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })
  const [borrador, setBorrador] = useState({ servicio, desde, hasta })

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultarCalificacionesPrestador({ servicio, desde, hasta, pagina })
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setEstado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [servicio, desde, hasta, pagina])

  const filtrar = (evento: FormEvent) => {
    evento.preventDefault()
    const nuevos = new URLSearchParams()
    if (borrador.servicio) nuevos.set('servicio', borrador.servicio)
    if (borrador.desde) nuevos.set('desde', borrador.desde)
    if (borrador.hasta) nuevos.set('hasta', borrador.hasta)
    setParametros(nuevos)
  }

  const irA = (numero: number) => {
    const nuevos = new URLSearchParams(parametros)
    nuevos.set('pagina', String(numero))
    setParametros(nuevos)
  }

  const datos = estado.estado === 'ok' ? estado.datos : null
  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.totalElementos / datos.tamano)) : 1

  return (
    <div className="calificaciones-prestador">
      <h1>{t('calificacionesPrestador.titulo')}</h1>

      <form className="filtros-moderacion" onSubmit={filtrar}>
        <label>
          {t('calificacionesPrestador.servicio')}
          <select value={borrador.servicio} onChange={(e) => setBorrador({ ...borrador, servicio: e.target.value })}>
            <option value="">{t('calificacionesPrestador.todos')}</option>
            {datos?.servicios.map((s) => (
              <option key={s.idServicio} value={s.idServicio}>
                {s.nombre ?? s.idServicio}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('calificacionesPrestador.desde')}
          <input type="date" value={borrador.desde} onChange={(e) => setBorrador({ ...borrador, desde: e.target.value })} />
        </label>
        <label>
          {t('calificacionesPrestador.hasta')}
          <input type="date" value={borrador.hasta} onChange={(e) => setBorrador({ ...borrador, hasta: e.target.value })} />
        </label>
        <button type="submit" className="boton-secundario">
          {t('calificacionesPrestador.filtrar')}
        </button>
      </form>

      {estado.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('calificacionesPrestador.cargando')}
        </p>
      )}
      {estado.estado === 'error' && (
        <p className="calificaciones__error" role="alert">
          {t('calificacionesPrestador.error')}
        </p>
      )}
      {datos && (
        <>
          <p className="calificaciones__promedio">
            {datos.promedio === null ? (
              <span>{t('calificaciones.sinCalificaciones')}</span>
            ) : (
              <>
                <span>{t('calificacionesPrestador.promedio')}</span>
                <strong>{formatearPromedio(datos.promedio, i18n.language)}</strong>
                <EstrellasFijas valor={datos.promedio} />
                <span>{t('calificaciones.cantidad', { count: datos.cantidad })}</span>
              </>
            )}
          </p>
          {datos.servicios.length > 0 && (
            <ul className="resumen-servicios" aria-label={t('calificacionesPrestador.porServicio')}>
              {datos.servicios.map((s) => (
                <li key={s.idServicio}>
                  <strong>{s.nombre ?? s.idServicio}</strong>
                  <br />
                  {s.promedio !== null && formatearPromedio(s.promedio, i18n.language)}{' '}
                  {t('calificaciones.cantidad', { count: s.cantidad })}
                </li>
              ))}
            </ul>
          )}
          {datos.elementos.length === 0 ? (
            <p className="calificaciones__vacio">{t('calificacionesPrestador.vacio')}</p>
          ) : (
            <ol className="lista-moderacion">
              {datos.elementos.map((c) => (
                <li key={c.id}>
                  <strong>{c.nombreServicio ?? c.idServicio}</strong>
                  <span className="solo-lector">{t('calificaciones.estrellasDe', { n: c.estrellas })}</span>
                  <EstrellasFijas valor={c.estrellas} />
                  <time dateTime={c.fecha}>{formatearInstante(c.fecha, i18n.language)}</time>
                  {c.comentario && <p>{c.comentario}</p>}
                </li>
              ))}
            </ol>
          )}
          {totalPaginas > 1 && (
            <nav className="acciones-moderacion" aria-label={t('calificacionesPrestador.paginacion')}>
              <button type="button" className="boton-secundario" disabled={pagina <= 1} onClick={() => irA(pagina - 1)}>
                {t('conocimiento.anterior')}
              </button>
              <span>{t('conocimiento.pagina', { n: pagina, total: totalPaginas })}</span>
              <button
                type="button"
                className="boton-secundario"
                disabled={pagina >= totalPaginas}
                onClick={() => irA(pagina + 1)}
              >
                {t('conocimiento.siguiente')}
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
