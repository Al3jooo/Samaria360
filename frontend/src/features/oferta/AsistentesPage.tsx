import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../shared/api/httpClient'
import { formatearFecha } from '../reservas/fechasBogota'
import { listarServiciosParaFiltro, type ServicioResumen } from '../prestador/serviciosPrestadorApi'
import { asistentesCsv, consultarAsistentes, type ListadoAsistentes } from './ofertaApi'
import './oferta.css'

type Estado =
  | { estado: 'inicial' }
  | { estado: 'cargando' }
  | { estado: 'error'; codigo: string }
  | { estado: 'ok'; datos: ListadoAsistentes }

const CATEGORIAS_CON_OFERTA = new Set(['Tour', 'Alojamiento'])

/**
 * WF-40 «Asistentes» (RF42; DEC-312), solo para el Prestador: las reservas Confirmadas de un servicio en una fecha (en
 * Alojamiento, las estadías que la incluyen), con el nombre y el contacto del turista que ms-booking pide a ms-auth
 * con su JWT de servicio. Servicio, fecha y salida viven en la URL. «Descargar CSV» arma el archivo en el navegador;
 * «Descargar PDF» abre el diálogo de impresión con una hoja solo del listado (sin dependencias nuevas).
 */
export function AsistentesPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const servicio = parametros.get('servicio') ?? ''
  const fecha = parametros.get('fecha') ?? ''
  const horaInicio = parametros.get('horaInicio') ?? ''
  const [servicios, setServicios] = useState<ServicioResumen[]>([])
  const [borrador, setBorrador] = useState({ servicio, fecha, horaInicio })
  const [estado, setEstado] = useState<Estado>({ estado: 'inicial' })

  useEffect(() => {
    listarServiciosParaFiltro()
      .then((r) => setServicios(r.servicios.filter((s) => CATEGORIAS_CON_OFERTA.has(s.categoria))))
      .catch(() => setServicios([]))
  }, [])

  useEffect(() => {
    if (!servicio || !fecha) {
      setEstado({ estado: 'inicial' })
      return
    }
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultarAsistentes(servicio, fecha, horaInicio)
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch((error) => {
        if (!vigente) return
        const codigo =
          error instanceof ApiError && error.status === 503
            ? 'noDisponible'
            : error instanceof ApiError && error.status === 400
              ? 'datos'
              : 'otro'
        setEstado({ estado: 'error', codigo })
      })
    return () => {
      vigente = false
    }
  }, [servicio, fecha, horaInicio])

  const consultar = (evento: FormEvent) => {
    evento.preventDefault()
    const nuevos = new URLSearchParams()
    if (borrador.servicio) nuevos.set('servicio', borrador.servicio)
    if (borrador.fecha) nuevos.set('fecha', borrador.fecha)
    if (borrador.horaInicio) nuevos.set('horaInicio', borrador.horaInicio)
    setParametros(nuevos)
  }

  const nombreServicio = servicios.find((s) => s.id === servicio)?.nombreComercial ?? servicio

  const descargarCsv = (datos: ListadoAsistentes) => {
    const encabezados = ['nombre', 'correo', 'telefono', 'personas', 'salida', 'entrada', 'salidaEstadia'].map((c) =>
      t(`asistentes.columnas.${c}`),
    )
    const blob = new Blob([asistentesCsv(datos, encabezados)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = `asistentes-${datos.fecha}${datos.horaInicio ? `-${datos.horaInicio.replace(':', '')}` : ''}.csv`
    document.body.appendChild(enlace)
    enlace.click()
    enlace.remove()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="oferta asistentes">
      <h1>{t('asistentes.titulo')}</h1>
      <p className="oferta__ayuda no-imprimir">{t('asistentes.ayuda')}</p>

      <form className="oferta__filtros no-imprimir" onSubmit={consultar}>
        <label>
          {t('asistentes.servicio')}
          <select
            required
            value={borrador.servicio}
            onChange={(e) => setBorrador({ ...borrador, servicio: e.target.value })}
          >
            <option value="">{t('asistentes.elegirServicio')}</option>
            {servicios.map((s) => (
              <option key={s.id} value={s.id}>
                {s.nombreComercial}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('asistentes.fecha')}
          <input
            type="date"
            required
            value={borrador.fecha}
            onChange={(e) => setBorrador({ ...borrador, fecha: e.target.value })}
          />
        </label>
        <label>
          {t('asistentes.salidaOpcional')}
          <input
            type="time"
            value={borrador.horaInicio}
            onChange={(e) => setBorrador({ ...borrador, horaInicio: e.target.value })}
          />
        </label>
        <button type="submit" className="boton-secundario">
          {t('asistentes.consultar')}
        </button>
      </form>

      {estado.estado === 'inicial' && <p className="oferta__vacio">{t('asistentes.inicial')}</p>}
      {estado.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('asistentes.cargando')}
        </p>
      )}
      {estado.estado === 'error' && (
        <p className="oferta__aviso oferta__aviso--error" role="alert">
          {t(`asistentes.errores.${estado.codigo}`)}
        </p>
      )}
      {estado.estado === 'ok' && (
        <section className="tarjeta oferta__seccion" aria-labelledby="asistentes-listado">
          <h2 id="asistentes-listado">
            {t('asistentes.listado', {
              servicio: nombreServicio,
              fecha: formatearFecha(estado.datos.fecha, i18n.language),
            })}
            {estado.datos.horaInicio ? ` · ${estado.datos.horaInicio}` : ''}
          </h2>
          <p>{t('asistentes.total', { count: estado.datos.totalPersonas })}</p>
          {estado.datos.asistentes.length === 0 ? (
            <p className="oferta__vacio">{t('asistentes.vacio')}</p>
          ) : (
            <>
              <div className="oferta__acciones no-imprimir">
                <button type="button" className="boton-principal" onClick={() => descargarCsv(estado.datos)}>
                  {t('asistentes.descargarCsv')}
                </button>
                <button type="button" className="boton-secundario" onClick={() => window.print()}>
                  {t('asistentes.descargarPdf')}
                </button>
              </div>
              <div className="oferta__tabla-contenedor">
                <table className="oferta__tabla">
                  <thead>
                    <tr>
                      <th scope="col">{t('asistentes.columnas.nombre')}</th>
                      <th scope="col">{t('asistentes.columnas.correo')}</th>
                      <th scope="col">{t('asistentes.columnas.telefono')}</th>
                      <th scope="col">{t('asistentes.columnas.personas')}</th>
                      <th scope="col">{t('asistentes.columnas.salida')}</th>
                      <th scope="col">{t('asistentes.columnas.entrada')}</th>
                      <th scope="col">{t('asistentes.columnas.salidaEstadia')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {estado.datos.asistentes.map((a) => (
                      <tr key={a.idReserva}>
                        <td>{a.nombre ?? t('asistentes.sinContacto')}</td>
                        <td>{a.correo ?? '—'}</td>
                        <td>{a.telefono ?? '—'}</td>
                        <td>{a.personas}</td>
                        <td>{a.horaInicio ?? a.nombreUnidad ?? '—'}</td>
                        <td>{formatearFecha(a.fechaEntrada, i18n.language)}</td>
                        <td>{a.fechaSalida ? formatearFecha(a.fechaSalida, i18n.language) : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  )
}
