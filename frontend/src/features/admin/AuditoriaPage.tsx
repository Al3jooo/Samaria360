import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { ApiError, servicioNoDisponible } from '../../shared/api/httpClient'
import { Paginacion } from '../../shared/components/Paginacion'
import { ACCIONES_AUDITADAS, listarAuditoria, type PaginaAuditoria } from './auditoriaApi'
import { detalleTraducido } from './detalleAuditoria'
import './admin.css'

type Estado =
  | { estado: 'cargando' }
  | { estado: 'ok'; pagina: PaginaAuditoria }
  | { estado: 'error'; clave: string }

function fechaHora(instante: string, idioma: string): string {
  const fecha = new Date(instante)
  if (Number.isNaN(fecha.getTime())) return instante
  return new Intl.DateTimeFormat(idioma, {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Bogota',
  }).format(fecha)
}

/**
 * WF-30 «Auditoría» (RF13; propuesto por arquitectura), solo para el Administrador: las acciones críticas
 * (aprobaciones, cancelaciones y cambios de rol) con el usuario, la fecha y la acción, de la más reciente a la más
 * antigua, 20 por página. Filtros de acción y rango de fechas en la URL (se pueden compartir y sobreviven al cambio de
 * idioma, RNF08). El usuario es el id de la cuenta: el log no guarda nombres (ADR-011, punto 7).
 */


export function AuditoriaPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const accion = parametros.get('accion') ?? ''
  const desde = parametros.get('desde') ?? ''
  const hasta = parametros.get('hasta') ?? ''
  const paginaActual = Math.max(1, Number(parametros.get('pagina')) || 1)
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })
  const [borrador, setBorrador] = useState({ accion, desde, hasta })

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    listarAuditoria({ accion, desde, hasta, pagina: paginaActual })
      .then((pagina) => vigente && setEstado({ estado: 'ok', pagina }))
      .catch((error: unknown) => {
        if (!vigente) return
        const clave =
          error instanceof ApiError && servicioNoDisponible(error)
            ? 'auditoria.noDisponible'
            : error instanceof ApiError && error.codigo === 'DATOS_INVALIDOS'
              ? 'auditoria.filtrosInvalidos'
              : 'auditoria.error'
        setEstado({ estado: 'error', clave })
      })
    return () => {
      vigente = false
    }
  }, [accion, desde, hasta, paginaActual])

  const aplicar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const nuevos = new URLSearchParams()
    if (borrador.accion) nuevos.set('accion', borrador.accion)
    if (borrador.desde) nuevos.set('desde', borrador.desde)
    if (borrador.hasta) nuevos.set('hasta', borrador.hasta)
    setParametros(nuevos)
  }

  const cambiarPagina = (pagina: number) => {
    const nuevos = new URLSearchParams(parametros)
    nuevos.set('pagina', String(pagina))
    setParametros(nuevos)
  }

  const totalPaginas = estado.estado === 'ok' ? Math.ceil(estado.pagina.totalElementos / estado.pagina.tamano) : 0

  return (
    <section className="admin" aria-labelledby="titulo-auditoria">
      <h1 id="titulo-auditoria">{t('auditoria.titulo')}</h1>
      <p className="admin__introduccion">{t('auditoria.introduccion')}</p>

      <form className="admin__filtros" onSubmit={aplicar}>
        <label>
          <span>{t('auditoria.accion')}</span>
          <select value={borrador.accion} onChange={(e) => setBorrador({ ...borrador, accion: e.target.value })}>
            <option value="">{t('auditoria.todas')}</option>
            {ACCIONES_AUDITADAS.map((valor) => (
              <option key={valor} value={valor}>
                {t(`auditoria.acciones.${valor}`)}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>{t('auditoria.desde')}</span>
          <input type="date" value={borrador.desde} onChange={(e) => setBorrador({ ...borrador, desde: e.target.value })} />
        </label>
        <label>
          <span>{t('auditoria.hasta')}</span>
          <input type="date" value={borrador.hasta} onChange={(e) => setBorrador({ ...borrador, hasta: e.target.value })} />
        </label>
        <button type="submit" className="boton-secundario">
          {t('auditoria.filtrar')}
        </button>
      </form>

      <div role="status" aria-live="polite" className="solo-lector">
        {estado.estado === 'ok' ? t('auditoria.resultados', { count: estado.pagina.totalElementos }) : ''}
      </div>

      {estado.estado === 'cargando' && <p aria-busy="true">{t('comun.cargando')}</p>}
      {estado.estado === 'error' && <p role="alert">{t(estado.clave)}</p>}
      {estado.estado === 'ok' && estado.pagina.elementos.length === 0 && <p>{t('auditoria.vacia')}</p>}
      {estado.estado === 'ok' && estado.pagina.elementos.length > 0 && (
        <table className="admin__tabla">
          <caption className="solo-lector">{t('auditoria.titulo')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('auditoria.fecha')}</th>
              <th scope="col">{t('auditoria.usuario')}</th>
              <th scope="col">{t('auditoria.accion')}</th>
              <th scope="col">{t('auditoria.detalle')}</th>
            </tr>
          </thead>
          <tbody>
            {estado.pagina.elementos.map((registro, indice) => (
              <tr key={`${registro.fecha}-${indice}`}>
                <td data-titulo={t('auditoria.fecha')}>{fechaHora(registro.fecha, i18n.language)}</td>
                <td data-titulo={t('auditoria.usuario')}>
                  <code title={registro.usuarioId}>…{registro.usuarioId.slice(-12)}</code>
                </td>
                <td data-titulo={t('auditoria.accion')}>{t(`auditoria.acciones.${registro.accion}`)}</td>
                <td data-titulo={t('auditoria.detalle')}>{detalleTraducido(registro, t)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Paginacion actual={paginaActual} total={totalPaginas} onCambiar={cambiarPagina} />
    </section>
  )
}
