import '../../afluencia/afluencia.css'
import { formatearPromedio } from '../../calificaciones/calificacionesApi'
import { useId, type Ref } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { ICONOS_ACCESIBILIDAD } from '../../../shared/components/Iconos'
import { BotonFavorito } from '../../favoritos/BotonFavorito'
import type { EstadoResumen } from '../../reservas/reservasApi'
import type { ResultadoOferta } from '../catalogoApi'
import { rutaDetalleOferta } from '../detalle/navegacionDetalle'
import { formatearDistancia, formatearPrecio } from '../formato'
import { etiquetaCategoria } from './etiquetas'
import { ImagenOferta } from './ImagenOferta'
import { InsigniaEstadoOperativo } from './InsigniaEstadoOperativo'

/**
 * Tarjeta de un servicio o atractivo: lista de WF-05 ('lista') y destacados de WF-04 ('destacado').
 * Calificación (RF17) y afluencia (RF39) dependen de datos que aún no existen: se muestran como «—». La disponibilidad
 * de los servicios (RF11) sale del resumen de ms-booking para los próximos 7 días; mientras carga, si falla o en las
 * categorías que no se reservan, también «—». «Ver detalle» lleva a WF-06: la ficha del servicio o, desde la
 * iteración E, la del atractivo (DEC-224). En la lista, el corazón «Guardar en favoritos» (RF54, DEC-93) sirve para servicios y atractivos (DEC-87);
 * los destacados de WF-04 no lo llevan.
 *
 * En la lista, `onResaltar` avisa cuando el puntero o el foco entran o salen (para resaltar su marcador en el mapa);
 * la tarjeta acepta el foco por programa (tabIndex -1) para cuando se pulsa su marcador.
 */
export function TarjetaOferta({
  resultado,
  variante,
  resaltada = false,
  onResaltar,
  disponibilidad = null,
  ref,
}: {
  resultado: ResultadoOferta
  variante: 'lista' | 'destacado'
  resaltada?: boolean
  /** Del resumen de ms-booking; null = sin datos. */
  disponibilidad?: EstadoResumen | null
  onResaltar?: (activa: boolean) => void
  ref?: Ref<HTMLElement>
}) {
  const { t, i18n } = useTranslation()
  const idTitulo = useId()
  const ubicacion = useLocation()
  const sinDatos = (
    <>
      <span aria-hidden="true">—</span>
      <span className="solo-lector">{t('comun.sinDatos')}</span>
    </>
  )

  return (
    <article
      ref={ref}
      className={`tarjeta-oferta tarjeta-oferta--${variante}${resaltada ? ' tarjeta-oferta--resaltada' : ''}`}
      aria-labelledby={idTitulo}
      tabIndex={onResaltar ? -1 : undefined}
      onMouseEnter={onResaltar && (() => onResaltar(true))}
      onMouseLeave={onResaltar && (() => onResaltar(false))}
      onFocus={onResaltar && (() => onResaltar(true))}
      onBlur={onResaltar && (() => onResaltar(false))}
    >
      <ImagenOferta resultado={resultado} />
      <div className="tarjeta-oferta__cuerpo">
        <p className="tarjeta-oferta__categoria">
          {resultado.tipo === 'Atractivo' && `${t('catalogo.tipos.Atractivo')} · `}
          {etiquetaCategoria(t, resultado.categoria)}
        </p>
        <h3 id={idTitulo} className="tarjeta-oferta__nombre">
          {resultado.nombre}
        </h3>

        {resultado.estadoOperativo && (
          // Decisión 5: los atractivos cerrados o en mantenimiento aparecen con su estado visible (RF33).
          <InsigniaEstadoOperativo estado={resultado.estadoOperativo} />
        )}

        {variante === 'lista' && resultado.descripcionCorta && (
          <p className="tarjeta-oferta__descripcion">{resultado.descripcionCorta}</p>
        )}

        {resultado.precio !== null && (
          <p className="tarjeta-oferta__precio">
            {t('catalogo.tarjeta.desde', { precio: formatearPrecio(resultado.precio, i18n.language) })}
          </p>
        )}

        <dl className="tarjeta-oferta__datos">
          {resultado.distanciaKm !== null && (
            <div>
              <dt>{t('catalogo.tarjeta.distancia')}</dt>
              <dd>{formatearDistancia(resultado.distanciaKm, i18n.language)}</dd>
            </div>
          )}
          <div>
            <dt>{t('catalogo.tarjeta.calificacion')}</dt>
            <dd>
              {resultado.calificacion != null
                ? t('catalogo.tarjeta.calificacionValor', {
                    promedio: formatearPromedio(resultado.calificacion, i18n.language),
                    count: resultado.cantidadCalificaciones ?? 0,
                  })
                : sinDatos}
            </dd>
          </div>
          <div>
            {/* Según el SRS: afluencia de los atractivos (RF39) y disponibilidad de cupos de los servicios (RF11). */}
            <dt>{t(resultado.tipo === 'Atractivo' ? 'catalogo.tarjeta.afluencia' : 'catalogo.tarjeta.disponibilidad')}</dt>
            <dd>
              {resultado.tipo === 'Servicio' && disponibilidad ? (
                t(`catalogo.tarjeta.disponibilidadEstados.${disponibilidad}`)
              ) : resultado.tipo === 'Atractivo' && resultado.afluencia ? (
                <span className={`nivel-afluencia nivel-afluencia--${resultado.afluencia}`}>
                  {t(`afluencia.niveles.${resultado.afluencia}`)}
                </span>
              ) : (
                sinDatos
              )}
            </dd>
          </div>
        </dl>

        {resultado.accesibilidad.length > 0 && (
          <ul className="tarjeta-oferta__accesibilidad" aria-label={t('catalogo.tarjeta.accesibilidad')}>
            {resultado.accesibilidad.map((condicion) => {
              const Icono = ICONOS_ACCESIBILIDAD[condicion]
              const etiqueta = t(`catalogo.accesibilidad.${condicion}`)
              return (
                <li key={condicion} title={etiqueta}>
                  <Icono />
                  <span className="solo-lector">{etiqueta}</span>
                </li>
              )
            })}
          </ul>
        )}

        {/* WF-04 y WF-05 (v2): solo «Ver detalle»; la reserva se hace desde el detalle (WF-06 → WF-10). */}
        <div className="tarjeta-oferta__acciones">
          <Link
            className="enlace"
            to={rutaDetalleOferta(resultado.tipo, resultado.id)}
            state={{ volverA: ubicacion.pathname + ubicacion.search }}
            aria-describedby={idTitulo}
          >
            {t('catalogo.tarjeta.verDetalle')}
          </Link>
          {variante === 'lista' && (
            <BotonFavorito
              className="tarjeta-oferta__favorito"
              referencia={{ tipo: resultado.tipo, id: resultado.id, nombre: resultado.nombre }}
            />
          )}
        </div>
      </div>
    </article>
  )
}
