import { AlertaAfluencia, DestinosAlternativos } from '../../afluencia/SeccionesAfluencia'
import { useAfluencia } from '../../afluencia/afluenciaApi'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router-dom'
import { BotonNoDisponible } from '../../../shared/components/BotonNoDisponible'
import { ICONOS_ACCESIBILIDAD } from '../../../shared/components/Iconos'
import { AvisoFavoritos } from '../../favoritos/AvisoFavoritos'
import { BotonFavorito } from '../../favoritos/BotonFavorito'
import { MapaSoloLectura } from '../../servicios/MapaSoloLectura'
import { ImagenOferta } from '../componentes/ImagenOferta'
import { InsigniaEstadoOperativo } from '../componentes/InsigniaEstadoOperativo'
import { etiquetaCategoria } from '../componentes/etiquetas'
import { BotonReportar } from '../../calificaciones/BotonReportar'
import { ResumenCalificacion } from '../../calificaciones/Estrellas'
import { SeccionCalificaciones } from '../../calificaciones/SeccionCalificaciones'
import { useCalificacionesEntidad } from '../../calificaciones/useCalificacionesEntidad'
import { useDetalleAtractivo, type DetalleAtractivo } from './atractivoApi'
import { NoDisponible, RegionDeshabilitada } from './DetalleServicioPage'
import { leerVolverA } from './navegacionDetalle'
import '../catalogo.css'
import './detalle.css'

/** Miniaturas de la galería (1 grande + 4), como en WF-06. */
const MAXIMO_MINIATURAS = 4

/**
 * Ficha pública de un atractivo (WF-06 para atractivos, CU «Consultar detalle de atractivo»; DEC-217, DEC-224), sin
 * token y visible mientras exista (DEC-87). Muestra lo que registró el Gestor: el estado operativo (RF33), la
 * accesibilidad (RF27), la capacidad de carga (RF07), las normas ambientales (RF24) y el horario de atención (RF58), los
 * dos textos con sus saltos de línea. Sin precio, contacto ni reserva: un atractivo no se reserva. Lo que depende de
 * funciones descartadas (afluencia RF39, destinos alternativos RF08, calificación RF17) y «Compartir» quedan
 * deshabilitados con el mismo estado único de WF-06.
 */
export function DetalleAtractivoPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const ubicacion = useLocation()
  const volverA = leerVolverA(ubicacion.state)
  const detalle = useDetalleAtractivo(id)

  return (
    <div className="detalle">
      <p className="detalle__volver">
        <Link to={volverA ?? '/explorar'}>
          <span aria-hidden="true">← </span>
          {t('detalle.volverBusqueda')}
        </Link>
      </p>
      {detalle.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('detalleAtractivo.cargando')}
        </p>
      )}
      {detalle.estado === 'error' && (
        <div className="estado-catalogo estado-catalogo--error" role="alert">
          <p className="estado-catalogo__mensaje">{t(`detalleAtractivo.errores.${detalle.error}`)}</p>
          {detalle.error !== 'noEncontrado' && (
            <button type="button" className="boton-secundario" onClick={detalle.recargar}>
              {t('catalogo.busqueda.reintentar')}
            </button>
          )}
        </div>
      )}
      {detalle.estado === 'ok' && <FichaAtractivo atractivo={detalle.atractivo} />}
    </div>
  )
}

function FichaAtractivo({ atractivo }: { atractivo: DetalleAtractivo }) {
  const { t, i18n } = useTranslation()
  const idTitulo = useId()
  const [portada, ...resto] = atractivo.imagenes
  const miniaturas = resto.slice(0, MAXIMO_MINIATURAS)
  const { condicionesAccesibilidad, normasAmbientales, horarioAtencion } = atractivo
  // RF17 (DEC-303): cualquier turista con la cuenta Activa califica un atractivo.
  const calificaciones = useCalificacionesEntidad('atractivo', atractivo.id)
  // RF39, RF08 y RF51 (DEC-306): el nivel vigente, el límite y los alternativos, de ms-catalog.
  const afluencia = useAfluencia(atractivo.id)

  return (
    <article className="detalle__ficha" aria-labelledby={idTitulo}>
      <header className="detalle__encabezado">
        <div className="detalle__titulo">
          <h1 id={idTitulo}>{atractivo.nombre}</h1>
          <p className="detalle__subtitulo">
            <span className="detalle__categoria">
              {t('catalogo.tipos.Atractivo')}
              {atractivo.categoria && ` · ${etiquetaCategoria(t, atractivo.categoria)}`}
            </span>
            <span aria-hidden="true"> · </span>
            <span>
              {t('detalle.calificacion')} <ResumenCalificacion resultado={calificaciones.resultado} />
            </span>
          </p>
          {atractivo.estadoOperativo && <InsigniaEstadoOperativo estado={atractivo.estadoOperativo} />}
          {condicionesAccesibilidad.length > 0 ? (
            <ul className="detalle__accesibilidad" aria-label={t('detalle.accesibilidad')}>
              {condicionesAccesibilidad.map((condicion) => {
                const Icono = ICONOS_ACCESIBILIDAD[condicion]
                const etiqueta = t(`catalogo.accesibilidad.${condicion}`)
                return (
                  <li key={condicion}>
                    <span className="detalle__icono-accesibilidad" role="img" aria-label={etiqueta} title={etiqueta}>
                      <Icono />
                    </span>
                    <span aria-hidden="true">{etiqueta}</span>
                  </li>
                )
              })}
            </ul>
          ) : (
            <p className="detalle__accesibilidad">
              {t('detalle.accesibilidad')} <NoDisponible />
            </p>
          )}
        </div>
        <div className="detalle__acciones-ficha">
          <BotonFavorito conTexto referencia={{ tipo: 'Atractivo', id: atractivo.id, nombre: atractivo.nombre }} />
          <BotonNoDisponible>{t('detalle.compartir')}</BotonNoDisponible>
        </div>
      </header>
      <AvisoFavoritos />

      <div className="detalle__galeria">
        <div className="detalle__portada">
          <ImagenOferta resultado={{ imagen: portada ?? null, categoria: atractivo.categoria ?? '', tipo: 'Atractivo' }} />
        </div>
        {miniaturas.length > 0 && (
          <ul className="detalle__miniaturas">
            {miniaturas.map((imagen, indice) => (
              <li key={imagen}>
                <img
                  src={imagen}
                  alt={t('detalle.foto', { numero: indice + 2, nombre: atractivo.nombre })}
                  loading="lazy"
                  decoding="async"
                  width={150}
                  height={100}
                />
              </li>
            ))}
          </ul>
        )}
      </div>

      <AlertaAfluencia resultado={afluencia} />

      <div className="detalle__cuerpo">
        <section aria-labelledby={`${idTitulo}-descripcion`}>
          <h2 id={`${idTitulo}-descripcion`}>{t('detalle.descripcion')}</h2>
          {atractivo.descripcion ? (
            <p className="detalle__descripcion">{atractivo.descripcion}</p>
          ) : (
            <p>
              <NoDisponible />
            </p>
          )}
          <dl className="detalle__datos">
            <div>
              <dt>{t('detalleAtractivo.capacidad')}</dt>
              <dd>
                {t('detalleAtractivo.personas', {
                  count: atractivo.limiteCapacidadCarga,
                  n: atractivo.limiteCapacidadCarga.toLocaleString(i18n.language),
                })}
              </dd>
            </div>
          </dl>
        </section>

        {normasAmbientales ? (
          <section aria-labelledby={`${idTitulo}-normas`}>
            <h2 id={`${idTitulo}-normas`}>{t('detalleAtractivo.normas')}</h2>
            {/* RF24: estático y con los saltos de línea del Gestor (white-space: pre-line). */}
            <p className="detalle__horario">{normasAmbientales}</p>
          </section>
        ) : (
          <RegionDeshabilitada id={`${idTitulo}-normas`} titulo={t('detalleAtractivo.normas')} />
        )}

        {horarioAtencion ? (
          <section aria-labelledby={`${idTitulo}-horarios`}>
            <h2 id={`${idTitulo}-horarios`}>{t('detalle.horarios')}</h2>
            <p className="detalle__horario">{horarioAtencion}</p>
          </section>
        ) : (
          <RegionDeshabilitada id={`${idTitulo}-horarios`} titulo={t('detalle.horarios')} />
        )}

        <section aria-labelledby={`${idTitulo}-ubicacion`}>
          <h2 id={`${idTitulo}-ubicacion`}>{t('detalleAtractivo.ubicacion')}</h2>
          <MapaSoloLectura
            latitud={atractivo.coordenadas.latitud}
            longitud={atractivo.coordenadas.longitud}
            nombre={atractivo.nombre}
            tipo="atractivo"
            etiqueta={t('detalleAtractivo.mapa', { nombre: atractivo.nombre })}
          />
        </section>
      </div>

      <SeccionCalificaciones tipo="atractivo" id={atractivo.id} {...calificaciones} />
      {/* RF57 (DEC-301): también en la ficha del atractivo. */}
      <div className="detalle__reportar">
        <BotonReportar tipo="atractivo" id={atractivo.id} />
      </div>

      <DestinosAlternativos resultado={afluencia} />
    </article>
  )
}
