import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { Barras } from './Graficos'
import { consultarPanelPrestador, type PanelPrestador } from './tablerosApi'
import './tableros.css'

type Estado = { estado: 'cargando' } | { estado: 'error' } | { estado: 'ok'; datos: PanelPrestador }

/**
 * WF-36 «Mi panel» (RF31; DEC-308), solo para el Prestador: por cada uno de sus servicios, las consultas a la ficha y
 * las reservas recibidas en los últimos 7 o 30 días (el periodo va en la URL). Un servicio sin consultas ni reservas
 * en el periodo no aparece.
 */
export function PanelPrestadorPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const dias: 7 | 30 = parametros.get('dias') === '30' ? 30 : 7
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultarPanelPrestador(dias)
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setEstado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [dias])

  const servicios = estado.estado === 'ok' ? estado.datos.servicios : []
  return (
    <div className="tablero">
      <h1>{t('tableros.prestador.titulo')}</h1>
      <div className="selector-periodo" role="group" aria-label={t('tableros.prestador.periodo')}>
        {([7, 30] as const).map((d) => (
          <button
            key={d}
            type="button"
            className="boton-secundario"
            aria-pressed={dias === d}
            onClick={() => setParametros(d === 7 ? {} : { dias: '30' })}
          >
            {t('tableros.prestador.ultimos', { n: d })}
          </button>
        ))}
      </div>
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
      {estado.estado === 'ok' && servicios.length === 0 && <p>{t('tableros.prestador.vacio')}</p>}
      {servicios.length > 0 && (
        <>
          <table>
            <caption className="solo-lector">{t('tableros.prestador.tabla')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('tableros.prestador.servicio')}</th>
                <th scope="col" className="numero">
                  {t('tableros.prestador.consultas')}
                </th>
                <th scope="col" className="numero">
                  {t('tableros.prestador.reservas')}
                </th>
              </tr>
            </thead>
            <tbody>
              {servicios.map((s) => (
                <tr key={s.idServicio}>
                  <th scope="row">
                    <Link to={`/prestador/servicios/${encodeURIComponent(s.idServicio)}`}>{s.nombre ?? s.idServicio}</Link>
                  </th>
                  <td className="numero">{s.consultas.toLocaleString(i18n.language)}</td>
                  <td className="numero">{s.reservas.toLocaleString(i18n.language)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="tablero__cuadricula">
            <Barras
              titulo={t('tableros.prestador.consultasPorServicio')}
              vacio=""
              datos={servicios.map((s) => ({ clave: s.idServicio, etiqueta: s.nombre ?? s.idServicio, valor: s.consultas }))}
            />
            <Barras
              titulo={t('tableros.prestador.reservasPorServicio')}
              vacio=""
              datos={servicios.map((s) => ({ clave: s.idServicio, etiqueta: s.nombre ?? s.idServicio, valor: s.reservas }))}
            />
          </div>
        </>
      )}
    </div>
  )
}
