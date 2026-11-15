import { useTranslation } from 'react-i18next'
import { formatearPromedio } from './calificacionesApi'
import type { EstadoCalificaciones } from './useCalificacionesEntidad'

/** Cinco estrellas de solo lectura; el texto accesible lo da quien la usa. */
export function EstrellasFijas({ valor }: { valor: number }) {
  return (
    <span className="estrellas" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= Math.round(valor) ? 'estrellas__llena' : 'estrellas__vacia'}>
          ★
        </span>
      ))}
    </span>
  )
}

/** «★ 4,5 (3 calificaciones)» del subtítulo de WF-06 (RF17, DEC-303); sin calificaciones, lo dice. */
export function ResumenCalificacion({ resultado }: { resultado: EstadoCalificaciones }) {
  const { t, i18n } = useTranslation()
  if (resultado.estado === 'cargando') return <span>{t('calificaciones.cargando')}</span>
  if (resultado.estado === 'error') return <span>{t('calificaciones.noDisponibles')}</span>
  const { promedio, cantidad } = resultado.datos
  if (promedio === null) return <span>{t('calificaciones.sinCalificaciones')}</span>
  return (
    <a href="#calificaciones" className="resumen-calificacion">
      <EstrellasFijas valor={promedio} />
      <span>{t('calificaciones.resumen', { promedio: formatearPromedio(promedio, i18n.language), count: cantidad })}</span>
    </a>
  )
}
