import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { formatearInstante } from '../reservas/fechasBogota'
import { listarServiciosParaFiltro, type ServicioResumen } from '../prestador/serviciosPrestadorApi'
import { consultarHistorialModificaciones, type PaginaModificaciones } from './ofertaApi'
import './oferta.css'

type Estado = { estado: 'cargando' } | { estado: 'error' } | { estado: 'ok'; datos: PaginaModificaciones }

/**
 * WF-39 «Historial de modificaciones» (RF41; DEC-311), solo para el Prestador: los cambios de precio, cupos, fechas,
 * horarios y plantilla de sus servicios de los últimos 30 días, con la fecha y la hora y el valor anterior frente al
 * nuevo; filtro por servicio y página en la URL, 20 por página.
 */
export function HistorialModificacionesPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const servicio = parametros.get('servicio') ?? ''
  const pagina = Math.max(1, Number(parametros.get('pagina')) || 1)
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })
  const [servicios, setServicios] = useState<ServicioResumen[]>([])
  const [borrador, setBorrador] = useState(servicio)

  useEffect(() => {
    listarServiciosParaFiltro()
      .then((r) => setServicios(r.servicios))
      .catch(() => setServicios([]))
  }, [])

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultarHistorialModificaciones(servicio, pagina)
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setEstado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [servicio, pagina])

  const filtrar = (evento: FormEvent) => {
    evento.preventDefault()
    setParametros(borrador ? new URLSearchParams({ servicio: borrador }) : new URLSearchParams())
  }

  const irA = (numero: number) => {
    const nuevos = new URLSearchParams(parametros)
    nuevos.set('pagina', String(numero))
    setParametros(nuevos)
  }

  const datos = estado.estado === 'ok' ? estado.datos : null
  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.totalElementos / datos.tamano)) : 1

  return (
    <div className="oferta">
      <h1>{t('historialModificaciones.titulo')}</h1>
      <p className="oferta__ayuda">{t('historialModificaciones.ayuda')}</p>

      <form className="oferta__filtros" onSubmit={filtrar}>
        <label>
          {t('historialModificaciones.servicio')}
          <select value={borrador} onChange={(e) => setBorrador(e.target.value)}>
            <option value="">{t('historialModificaciones.todos')}</option>
            {servicios.map((s) => (
              <option key={s.id} value={s.id}>
                {s.nombreComercial}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="boton-secundario">
          {t('historialModificaciones.filtrar')}
        </button>
      </form>

      {estado.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('historialModificaciones.cargando')}
        </p>
      )}
      {estado.estado === 'error' && (
        <p className="oferta__aviso oferta__aviso--error" role="alert">
          {t('historialModificaciones.error')}
        </p>
      )}
      {datos &&
        (datos.elementos.length === 0 ? (
          <p className="oferta__vacio">{t('historialModificaciones.vacio')}</p>
        ) : (
          <div className="oferta__tabla-contenedor">
            <table className="oferta__tabla">
              <thead>
                <tr>
                  <th scope="col">{t('historialModificaciones.columnas.fecha')}</th>
                  <th scope="col">{t('historialModificaciones.columnas.servicio')}</th>
                  <th scope="col">{t('historialModificaciones.columnas.tipo')}</th>
                  <th scope="col">{t('historialModificaciones.columnas.detalle')}</th>
                  <th scope="col">{t('historialModificaciones.columnas.anterior')}</th>
                  <th scope="col">{t('historialModificaciones.columnas.nuevo')}</th>
                </tr>
              </thead>
              <tbody>
                {datos.elementos.map((m, i) => (
                  <tr key={`${m.fecha}-${i}`}>
                    <td>
                      <time dateTime={m.fecha}>{formatearInstante(m.fecha, i18n.language)}</time>
                    </td>
                    <td>{m.nombreServicio ?? m.idServicio}</td>
                    <td>{t(`historialModificaciones.tipos.${m.tipo}`)}</td>
                    <td>{m.detalle ?? '—'}</td>
                    <td>{m.valorAnterior ?? '—'}</td>
                    <td>{m.valorNuevo}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      {datos && totalPaginas > 1 && (
        <nav className="oferta__paginacion" aria-label={t('historialModificaciones.paginacion')}>
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
    </div>
  )
}
