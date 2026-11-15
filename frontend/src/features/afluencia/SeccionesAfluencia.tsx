import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { formatearInstante } from '../reservas/fechasBogota'
import { ImagenOferta } from '../catalogo/componentes/ImagenOferta'
import type { EstadoAfluencia } from './afluenciaApi'
import './afluencia.css'

/**
 * «Alerta de afluencia» de WF-06 para atractivos (RF39, RF08; DEC-306): el nivel vigente con el porcentaje del límite
 * de capacidad y la hora de la lectura; si llegó al 100 %, una alerta que lleva a los destinos alternativos. Sin
 * lectura del día, lo dice.
 */
export function AlertaAfluencia({ resultado }: { resultado: EstadoAfluencia }) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const afluencia = resultado.estado === 'ok' ? resultado.datos.afluencia : null
  return (
    <section
      aria-labelledby={id}
      className={`alerta-afluencia${afluencia ? ` alerta-afluencia--${afluencia.nivel.toLowerCase()}` : ''}`}
    >
      <h2 id={id}>{t('detalle.alertaAfluencia')}</h2>
      {resultado.estado === 'cargando' && <p role="status">{t('afluencia.cargando')}</p>}
      {resultado.estado === 'error' && <p>{t('afluencia.noDisponible')}</p>}
      {resultado.estado === 'ok' && !afluencia && <p>{t('afluencia.sinDatos')}</p>}
      {afluencia && (
        <>
          <p className="alerta-afluencia__nivel">
            <span className="alerta-afluencia__punto" aria-hidden="true" />
            <strong>{t(`afluencia.niveles.${afluencia.nivel}`)}</strong>{' '}
            {t('afluencia.detalle', {
              porcentaje: afluencia.porcentaje,
              ocupacion: afluencia.ocupacion,
              limite: afluencia.limite,
              hora: formatearInstante(afluencia.fecha, i18n.language),
            })}
          </p>
          {afluencia.limiteAlcanzado && (
            <p className="alerta-afluencia__limite" role="alert">
              {t('afluencia.limiteAlcanzado')} <a href="#alternativos">{t('afluencia.verAlternativos')}</a>
            </p>
          )}
        </>
      )}
    </section>
  )
}

/** «Destinos alternativos con menor afluencia» de WF-06 (RF51; DEC-305): hasta 3 tarjetas de la misma categoría. */
export function DestinosAlternativos({ resultado }: { resultado: EstadoAfluencia }) {
  const { t } = useTranslation()
  const id = useId()
  const alternativos = resultado.estado === 'ok' ? resultado.datos.alternativos : []
  return (
    <section id="alternativos" aria-labelledby={id} className="destinos-alternativos">
      <h2 id={id}>{t('detalle.alternativos')}</h2>
      {resultado.estado === 'cargando' && <p role="status">{t('afluencia.cargando')}</p>}
      {resultado.estado === 'error' && <p>{t('afluencia.noDisponible')}</p>}
      {resultado.estado === 'ok' && alternativos.length === 0 && <p>{t('afluencia.sinAlternativos')}</p>}
      {alternativos.length > 0 && (
        <ul>
          {alternativos.map((a) => (
            <li key={a.id}>
              <ImagenOferta resultado={{ tipo: 'Atractivo', categoria: a.categoria, imagen: a.imagen }} />
              <Link to={`/atractivos/${encodeURIComponent(a.id)}`}>{a.nombre}</Link>
              <span>
                {a.nivel
                  ? t('afluencia.alternativo', { nivel: t(`afluencia.niveles.${a.nivel}`), porcentaje: a.porcentaje })
                  : t('afluencia.sinDatos')}
                {' · '}
                {t('afluencia.distancia', { km: a.distanciaKm.toLocaleString() })}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
