import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { formatearFecha, formatearInstante } from '../reservas/fechasBogota'
import { Columnas } from './Graficos'
import { diaCorto } from './TableroGeneralPage'
import { consultarReporteAfluencia, type ReporteAfluencia } from './tablerosApi'
import '../afluencia/afluencia.css'
import './tableros.css'

type Estado = { estado: 'cargando' } | { estado: 'error' } | { estado: 'ok'; datos: ReporteAfluencia }

/**
 * WF-37 «Reporte de afluencia» (RF64; DEC-308), solo para el Gestor: con las lecturas de ocupación registradas (RF39),
 * por atractivo (lecturas, porcentaje promedio y máximo, último nivel) y por día, con filtros de rango de fechas y de
 * atractivo en la URL.
 */
export function ReporteAfluenciaPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const desde = parametros.get('desde') ?? ''
  const hasta = parametros.get('hasta') ?? ''
  const atractivo = parametros.get('atractivo') ?? ''
  const [borrador, setBorrador] = useState({ desde, hasta, atractivo })
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultarReporteAfluencia(desde, hasta, atractivo)
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setEstado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [desde, hasta, atractivo])

  const filtrar = (evento: FormEvent) => {
    evento.preventDefault()
    const nuevos = new URLSearchParams()
    Object.entries(borrador).forEach(([clave, valor]) => {
      if (valor) nuevos.set(clave, valor)
    })
    setParametros(nuevos)
  }

  const datos = estado.estado === 'ok' ? estado.datos : null
  return (
    <div className="tablero">
      <h1>{t('tableros.afluencia.titulo')}</h1>
      <form className="tablero__filtros" onSubmit={filtrar}>
        <label>
          {t('tableros.desde')}
          <input type="date" value={borrador.desde} onChange={(e) => setBorrador({ ...borrador, desde: e.target.value })} />
        </label>
        <label>
          {t('tableros.hasta')}
          <input type="date" value={borrador.hasta} onChange={(e) => setBorrador({ ...borrador, hasta: e.target.value })} />
        </label>
        <label>
          {t('tableros.afluencia.atractivo')}
          <select value={borrador.atractivo} onChange={(e) => setBorrador({ ...borrador, atractivo: e.target.value })}>
            <option value="">{t('tableros.afluencia.todos')}</option>
            {datos?.atractivosDisponibles.map((a) => (
              <option key={a.idAtractivo} value={a.idAtractivo}>
                {a.nombre}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="boton-secundario">
          {t('tableros.aplicar')}
        </button>
      </form>
      {estado.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('tableros.cargando')}
        </p>
      )}
      {estado.estado === 'error' && (
        <p className="tablero__error" role="alert">
          {t('tableros.error')}
        </p>
      )}
      {datos && (
        <>
          <p>{t('tableros.periodo', {
            desde: formatearFecha(datos.desde, i18n.language),
            hasta: formatearFecha(datos.hasta, i18n.language),
          })}</p>
          {datos.atractivos.length === 0 ? (
            <p>{t('tableros.afluencia.vacio')}</p>
          ) : (
            <>
              <table>
                <caption className="solo-lector">{t('tableros.afluencia.tabla')}</caption>
                <thead>
                  <tr>
                    <th scope="col">{t('tableros.afluencia.atractivo')}</th>
                    <th scope="col" className="numero">
                      {t('tableros.afluencia.lecturas')}
                    </th>
                    <th scope="col" className="numero">
                      {t('tableros.afluencia.promedio')}
                    </th>
                    <th scope="col" className="numero">
                      {t('tableros.afluencia.maximo')}
                    </th>
                    <th scope="col">{t('tableros.afluencia.ultimo')}</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.atractivos.map((a) => (
                    <tr key={a.idAtractivo}>
                      <th scope="row">
                        <Link to={`/atractivos/${encodeURIComponent(a.idAtractivo)}`}>{a.nombre}</Link>
                      </th>
                      <td className="numero">{a.lecturas.toLocaleString(i18n.language)}</td>
                      <td className="numero">{a.porcentajePromedio === null ? '—' : `${a.porcentajePromedio} %`}</td>
                      <td className="numero">{a.porcentajeMaximo} %</td>
                      <td>
                        <span className={`nivel-afluencia nivel-afluencia--${a.ultimoNivel}`}>
                          {t(`afluencia.niveles.${a.ultimoNivel}`)}
                        </span>{' '}
                        <small>{formatearInstante(a.ultimaLectura, i18n.language)}</small>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Columnas
                titulo={t('tableros.afluencia.porDia')}
                unidad={t('tableros.afluencia.porcentaje')}
                datos={datos.porDia.map((d) => ({
                  clave: d.dia,
                  etiqueta: diaCorto(d.dia),
                  valor: d.porcentajePromedio ?? 0,
                  texto: d.porcentajePromedio === null ? '—' : `${d.porcentajePromedio} %`,
                }))}
              />
            </>
          )}
        </>
      )}
    </div>
  )
}
