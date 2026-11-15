import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router-dom'
import { BotonNoDisponible } from '../../shared/components/BotonNoDisponible'
import { SinPermiso } from '../../shared/components/SinPermiso'
import { formatearDia } from '../../shared/formato'
import { GaleriaServicio } from '../oferta/GaleriaServicio'
import { DatosServicio } from '../servicios/DatosServicio'
import { InsigniaEstadoServicio } from '../servicios/InsigniaEstadoServicio'
import { esEditable, type EstadoAnuncio } from './edicionServicio'
import { useMiServicio } from './useMisServicios'
import './prestador.css'

/**
 * Detalle propio de WF-18 (DEC-169): estado, fecha de revisión y motivo del rechazo, más los datos tal como se
 * enviaron. Después del 201 llega con el aviso «Enviado a aprobación» (DEC-168). Un servicio ajeno o inexistente
 * responde 404 y se muestra como no encontrado.
 *
 * Iteración D (DEC-196): en `Pendiente` y `Publicado`, «Actualizar precio» (WF-20, RF32) y «Editar datos comerciales»
 * (WF-21, RF43). En `Rechazado` no hay acciones de edición y «Editar y reenviar» sigue deshabilitado (DEC-22, DEC-193).
 * Al volver de una edición (o de un 409 ESTADO_CAMBIADO, DEC-201) muestra su anuncio una vez.
 */
export function DetalleMiServicioPage() {
  const { t, i18n } = useTranslation()
  const { id = '' } = useParams()
  const ubicacion = useLocation()
  const enviado = (ubicacion.state as { enviado?: boolean } | null)?.enviado === true
  const anuncio = (ubicacion.state as Partial<EstadoAnuncio> | null)?.anuncio
  const servicio = useMiServicio(id)

  if (servicio.estado === 'error' && servicio.error === 'sinPermiso') return <SinPermiso />

  return (
    <div className="prestador">
      <p className="prestador__volver">
        <Link to="/prestador/servicios">
          <span aria-hidden="true">← </span>
          {t('prestador.detalle.volver')}
        </Link>
      </p>
      {servicio.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('prestador.detalle.cargando')}
        </p>
      )}
      {servicio.estado === 'error' && (
        <div className="prestador__error-general" role="alert">
          <p>{t(`prestador.detalle.errores.${servicio.error}`)}</p>
          {servicio.error !== 'noEncontrado' && (
            <button type="button" className="boton-secundario" onClick={servicio.recargar}>
              {t('catalogo.busqueda.reintentar')}
            </button>
          )}
        </div>
      )}
      {servicio.estado === 'ok' && (
        <article className="tarjeta prestador__seccion" aria-labelledby="detalle-mi-servicio">
          {enviado && (
            <p className="prestador__aviso prestador__aviso--exito" role="status">
              {t('prestador.detalle.enviado')}
            </p>
          )}
          {typeof anuncio === 'string' && anuncio !== '' && (
            <p className="prestador__aviso prestador__aviso--exito" role="status">
              {anuncio}
            </p>
          )}
          <h1 id="detalle-mi-servicio">{servicio.datos.nombreComercial}</h1>
          <dl className="prestador__datos prestador__datos--estado">
            <div>
              <dt>{t('prestador.misServicios.columnas.estado')}</dt>
              <dd>
                <InsigniaEstadoServicio estado={servicio.datos.estadoPublicacion} />
              </dd>
            </div>
            <div>
              <dt>{t('prestador.misServicios.columnas.registrado')}</dt>
              <dd>{formatearDia(servicio.datos.fechaRegistro, i18n.language)}</dd>
            </div>
            {servicio.datos.fechaRevision && (
              <div>
                <dt>{t('prestador.detalle.revisado')}</dt>
                <dd>{formatearDia(servicio.datos.fechaRevision, i18n.language)}</dd>
              </div>
            )}
          </dl>
          {servicio.datos.estadoPublicacion === 'Rechazado' && servicio.datos.motivoRechazo && (
            <section className="prestador__rechazo" aria-labelledby="motivo-rechazo">
              <h2 id="motivo-rechazo">{t('prestador.detalle.motivoRechazo')}</h2>
              <p>{servicio.datos.motivoRechazo}</p>
            </section>
          )}
          <h2>{t('prestador.detalle.datosEnviados')}</h2>
          <DatosServicio servicio={servicio.datos} fotos={servicio.datos.estadoPublicacion !== 'Publicado'} />
          {/* RF48 (DEC-313): la galería de un servicio aprobado se edita aquí, sin nueva aprobación. */}
          {servicio.datos.estadoPublicacion === 'Publicado' && (
            <GaleriaServicio idServicio={servicio.datos.id} imagenesIniciales={servicio.datos.imagenes ?? []} />
          )}
          <div className="prestador__acciones">
            <Link className="boton-secundario" to="/prestador/servicios">
              {t('prestador.detalle.volver')}
            </Link>
            {servicio.datos.estadoPublicacion === 'Publicado' && (
              <Link className="boton-secundario" to={`/servicios/${encodeURIComponent(servicio.datos.id)}`}>
                {t('prestador.misServicios.verFicha')}
              </Link>
            )}
            {esEditable(servicio.datos.estadoPublicacion) && (
              <>
                <Link className="boton-principal" to={`/prestador/servicios/${encodeURIComponent(servicio.datos.id)}/precio`}>
                  {t('prestador.detalle.actualizarPrecio')}
                </Link>
                <Link
                  className="boton-secundario"
                  to={`/prestador/servicios/${encodeURIComponent(servicio.datos.id)}/datos-comerciales`}
                >
                  {t('prestador.detalle.editarDatos')}
                </Link>
                {/* RF19 (DEC-310): cupos, fechas, horarios y plantilla; solo Tour y Alojamiento tienen oferta. */}
                {servicio.datos.oferta && servicio.datos.estadoPublicacion === 'Publicado' && (
                  <Link
                    className="boton-secundario"
                    to={`/prestador/servicios/${encodeURIComponent(servicio.datos.id)}/oferta`}
                  >
                    {t('prestador.detalle.gestionarOferta')}
                  </Link>
                )}
              </>
            )}
            {/* «Editar y reenviar»: fuera de la iteración D y siempre deshabilitado; solo en Rechazado (DEC-193). */}
            {servicio.datos.estadoPublicacion === 'Rechazado' && (
              <BotonNoDisponible>{t('prestador.detalle.editar')}</BotonNoDisponible>
            )}
          </div>
        </article>
      )}
    </div>
  )
}
