import { useEffect, useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router-dom'
import { BotonNoDisponible } from '../../../shared/components/BotonNoDisponible'
import { ICONOS_ACCESIBILIDAD } from '../../../shared/components/Iconos'
import { BotonReportar } from '../../calificaciones/BotonReportar'
import { ResumenCalificacion } from '../../calificaciones/Estrellas'
import { SeccionCalificaciones } from '../../calificaciones/SeccionCalificaciones'
import { useCalificacionesEntidad } from '../../calificaciones/useCalificacionesEntidad'
import { centavos, formatearCentavos } from '../../reservas/dinero'
import { AvisoFavoritos } from '../../favoritos/AvisoFavoritos'
import { BotonFavorito } from '../../favoritos/BotonFavorito'
import { CATEGORIAS_RESERVABLES } from '../../reservas/reservasApi'
import { ImagenOferta } from '../componentes/ImagenOferta'
import { etiquetaCategoria } from '../componentes/etiquetas'
import { formatearHoras } from '../formato'
import { useDetalleServicio, type DetalleServicio, type ErrorDetalle } from './detalleApi'
import { leerVolverA, rutaReserva } from './navegacionDetalle'
import '../catalogo.css'
import './detalle.css'

/** Miniaturas de la galería de WF-06 (1 grande + 4). */
const MAXIMO_MINIATURAS = 4

/**
 * WF-06 (pública), con todas sus regiones en el orden del wireframe. Política de visibilidad (docs/trazabilidad.md):
 * - lo que trae el catálogo (contacto, accesibilidad, horario de RF58 y precios) se muestra;
 * - lo que falta, sea un dato que el servicio no trae o una función pendiente (calificación RF17, etiquetas,
 *   compartir, alerta de afluencia RF39, WhatsApp, «Reportar» RF57 y destinos alternativos RF51; la dirección y el RNT
 *   cuando el servicio no los trae, HC2 y DEC-170),
 *   tiene un único estado deshabilitado: sin texto visible de estado, con una señal que no depende del color («—» como
 *   valor o borde discontinuo) y «No disponible» solo para lectores de pantalla (WCAG 1.4.1). Los botones llevan
 *   aria-disabled y no hacen nada.
 * «Ver disponibilidad y reservar» (WF-10) solo aparece en las categorías que se reservan (Tour y Alojamiento).
 * El corazón «Guardar en favoritos» (RF54) funciona como en WF-05 (DEC-93); «Compartir» sigue deshabilitado: RF49 solo
 * traza el botón de las reservas Completadas de WF-11 (DEC-90).
 */
export function DetalleServicioPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const ubicacion = useLocation()
  const volverA = leerVolverA(ubicacion.state)
  const detalle = useDetalleServicio(id)

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
          {t('detalle.cargando')}
        </p>
      )}
      {detalle.estado === 'error' && <ErrorDetalleServicio error={detalle.error} onReintentar={detalle.recargar} />}
      {detalle.estado === 'ok' && <FichaServicio servicio={detalle.servicio} volverA={volverA} />}
    </div>
  )
}

/** Estados de error del detalle (y de WF-10): 404, servicio caído, red u otro. */
export function ErrorDetalleServicio({ error, onReintentar }: { error: ErrorDetalle; onReintentar: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="estado-catalogo estado-catalogo--error" role="alert">
      <p className="estado-catalogo__mensaje">{t(`detalle.errores.${error}`)}</p>
      {error === 'noDisponible' && <p className="estado-catalogo__ayuda">{t('catalogo.busqueda.noDisponibleAyuda')}</p>}
      {error !== 'noEncontrado' && (
        <button type="button" className="boton-secundario" onClick={onReintentar}>
          {t('catalogo.busqueda.reintentar')}
        </button>
      )}
    </div>
  )
}

/** Valor deshabilitado: «—» a la vista y «No disponible» para lectores de pantalla. También en la ficha del atractivo. */
export function NoDisponible() {
  const { t } = useTranslation()
  return (
    <span className="deshabilitado">
      <span aria-hidden="true">—</span>
      <span className="solo-lector">{t('detalle.noDisponible')}</span>
    </span>
  )
}

/** Región de WF-06 visible pero deshabilitada (role="group", que admite aria-disabled), con «—» como contenido. */
export function RegionDeshabilitada({ id, titulo }: { id: string; titulo: string }) {
  return (
    <section role="group" aria-labelledby={id} aria-disabled="true" className="detalle__region-deshabilitada">
      <h2 id={id}>{titulo}</h2>
      <p>
        <NoDisponible />
      </p>
    </section>
  )
}

function FichaServicio({ servicio, volverA }: { servicio: DetalleServicio; volverA: string | null }) {
  const { t, i18n } = useTranslation()
  const idTitulo = useId()
  const precio = centavos(servicio.precioBase)
  const [portada, ...resto] = servicio.imagenes
  const miniaturas = resto.slice(0, MAXIMO_MINIATURAS)
  const { contacto, condicionesAccesibilidad, horarioAtencion, direccion, rnt, oferta } = servicio
  const diasOferta = oferta?.diasOperacion ?? []
  const salidasOferta = oferta?.salidas ?? []
  const hayOferta = diasOferta.length > 0 || salidasOferta.length > 0
  // RF17 (DEC-303): el promedio del subtítulo y la sección de reseñas salen de ms-analytics.
  const calificaciones = useCalificacionesEntidad('servicio', servicio.id)
  // «Calificar» de «Mis reservas» llega con `#calificaciones`: la ficha se pinta después de cargar, así que el
  // navegador ya no encuentra el ancla por su cuenta y hay que llevarla a la vista al montar.
  const { hash } = useLocation()
  useEffect(() => {
    if (!hash) return
    // jsdom no implementa scrollIntoView.
    document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView?.({ block: 'start' })
  }, [hash])
  const datoContacto = (etiqueta: string, valor?: ReactNode) => (
    <div key={etiqueta}>
      <dt>{etiqueta}</dt>
      <dd>{valor ?? <NoDisponible />}</dd>
    </div>
  )

  return (
    <article className="detalle__ficha" aria-labelledby={idTitulo}>
      <header className="detalle__encabezado">
        <div className="detalle__titulo">
          <h1 id={idTitulo}>{servicio.nombreComercial}</h1>
          <p className="detalle__subtitulo">
            <span className="detalle__categoria">{etiquetaCategoria(t, servicio.categoria)}</span>
            <span aria-hidden="true"> · </span>
            <span>
              {t('detalle.ubicacion')} <NoDisponible />
            </span>
            <span aria-hidden="true"> · </span>
            <span>
              {t('detalle.calificacion')} <ResumenCalificacion resultado={calificaciones.resultado} />
            </span>
          </p>
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
          <ul className="detalle__etiquetas detalle__etiquetas--pendientes" aria-label={t('detalle.etiquetas')}>
            {[t('detalle.prestadorVerificado'), t('detalle.afluenciaEnVivo'), t('detalle.reservaInmediata')].map(
              (etiqueta) => (
                <li key={etiqueta} className="detalle__no-disponible">
                  {etiqueta}
                  <span className="solo-lector"> ({t('detalle.noDisponible')})</span>
                </li>
              ),
            )}
          </ul>
        </div>
        <div className="detalle__acciones-ficha">
          <BotonFavorito conTexto referencia={{ tipo: 'Servicio', id: servicio.id, nombre: servicio.nombreComercial }} />
          <BotonNoDisponible>{t('detalle.compartir')}</BotonNoDisponible>
        </div>
      </header>
      <AvisoFavoritos />

      <div className="detalle__galeria">
        <div className="detalle__portada">
          <ImagenOferta prioritaria resultado={{ imagen: portada ?? null, categoria: servicio.categoria, tipo: 'Servicio' }} />
        </div>
        {miniaturas.length > 0 && (
          <ul className="detalle__miniaturas">
            {miniaturas.map((imagen, indice) => (
              <li key={imagen}>
                <img
                  src={imagen}
                  alt={t('detalle.foto', { numero: indice + 2, nombre: servicio.nombreComercial })}
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

      <RegionDeshabilitada id={`${idTitulo}-afluencia`} titulo={t('detalle.alertaAfluencia')} />

      <div className="detalle__cuerpo">
        <section aria-labelledby={`${idTitulo}-descripcion`}>
          <h2 id={`${idTitulo}-descripcion`}>{t('detalle.descripcion')}</h2>
          {servicio.descripcion ? (
            <p className="detalle__descripcion">{servicio.descripcion}</p>
          ) : (
            <p>
              <NoDisponible />
            </p>
          )}
          <DatosCategoria servicio={servicio} idTitulo={idTitulo} />
        </section>

        <section aria-labelledby={`${idTitulo}-contacto`}>
          <h2 id={`${idTitulo}-contacto`}>{t('detalle.contacto')}</h2>
          <dl className="detalle__datos detalle__contacto">
            {datoContacto(
              t('detalle.telefono'),
              contacto?.telefono ? (
                <a href={`tel:${contacto.telefono.replace(/\s+/g, '')}`}>{contacto.telefono}</a>
              ) : undefined,
            )}
            {datoContacto(t('detalle.whatsapp'))}
            {datoContacto(t('detalle.direccion'), direccion || undefined)}
            {datoContacto(
              t('detalle.correo'),
              contacto?.correo ? <a href={`mailto:${contacto.correo}`}>{contacto.correo}</a> : undefined,
            )}
            {datoContacto(t('detalle.rnt'), rnt || undefined)}
          </dl>
          <div className="detalle__reportar">
            <BotonReportar tipo="servicio" id={servicio.id} />
          </div>
        </section>

        <section aria-labelledby={`${idTitulo}-precio`} className="detalle__precios">
          <h2 id={`${idTitulo}-precio`}>{t('detalle.precio')}</h2>
          {precio !== null && (
            <p className="detalle__precio">
              {t('catalogo.tarjeta.desde', { precio: formatearCentavos(precio, i18n.language) })}
            </p>
          )}
          <TablaPrecios servicio={servicio} />
          {CATEGORIAS_RESERVABLES.includes(servicio.categoria) && (
            <p className="detalle__acciones">
              <Link className="boton-principal" to={rutaReserva(servicio.id)} state={volverA ? { volverA } : undefined}>
                {t('detalle.reservar')}
              </Link>
            </p>
          )}
        </section>

        {horarioAtencion || hayOferta ? (
          <section aria-labelledby={`${idTitulo}-horarios`}>
            <h2 id={`${idTitulo}-horarios`}>{t('detalle.horarios')}</h2>
            {horarioAtencion && <p className="detalle__horario">{horarioAtencion}</p>}
            {/* Oferta inicial (DEC-170): días de operación y, en Tour, las salidas. */}
            {hayOferta && (
              <dl className="detalle__datos">
                {diasOferta.length > 0 &&
                  dato(
                    t('detalle.oferta.dias'),
                    diasOferta.map((dia) => t(`comun.dias.${dia}`, { defaultValue: dia })).join(', '),
                  )}
                {salidasOferta.length > 0 && dato(t('detalle.oferta.salidas'), salidasOferta.join(', '))}
              </dl>
            )}
          </section>
        ) : (
          <RegionDeshabilitada id={`${idTitulo}-horarios`} titulo={t('detalle.horarios')} />
        )}
      </div>

      <SeccionCalificaciones tipo="servicio" id={servicio.id} {...calificaciones} />

      <RegionDeshabilitada id={`${idTitulo}-alternativos`} titulo={t('detalle.alternativos')} />
    </article>
  )
}

function dato(etiqueta: string, valor: string) {
  return (
    <div key={etiqueta}>
      <dt>{etiqueta}</dt>
      <dd>{valor}</dd>
    </div>
  )
}

/** Región «Precio» de WF-06: las unidades con su precio por noche (Alojamiento) o el precio por plato (Gastronomía). */
function TablaPrecios({ servicio }: { servicio: DetalleServicio }) {
  const { t, i18n } = useTranslation()

  if (servicio.categoria === 'Gastronomia') {
    const { rangoPrecioPlato } = servicio.detalleCategoria
    return rangoPrecioPlato ? <dl className="detalle__datos">{dato(t('detalle.rangoPrecioPlato'), rangoPrecioPlato)}</dl> : null
  }
  if (servicio.categoria !== 'Alojamiento') return null

  return (
    <ul className="detalle__unidades" aria-label={t('detalle.unidades')}>
      {servicio.detalleCategoria.unidades.map((unidad) => {
        const precio = centavos(unidad.precioNoche)
        return (
          <li key={unidad.idUnidad} className="detalle__unidad">
            <h3>{unidad.nombre}</h3>
            <dl>
              {dato(t('detalle.tipoHabitacion'), unidad.tipoHabitacion)}
              {dato(t('detalle.habitaciones'), String(unidad.numHabitaciones))}
              {dato(t('detalle.capacidad'), t('detalle.huespedesPorHabitacion', { count: unidad.capacidadMaxHuespedes }))}
            </dl>
            {precio !== null && (
              <p className="detalle__unidad-precio">
                {t('detalle.precioNoche', { precio: formatearCentavos(precio, i18n.language) })}
              </p>
            )}
          </li>
        )
      })}
    </ul>
  )
}

/** Dentro de la Descripción (WF-06): los servicios incluidos del alojamiento o los datos de la categoría (RF10). */
function DatosCategoria({ servicio, idTitulo }: { servicio: DetalleServicio; idTitulo: string }) {
  const { t, i18n } = useTranslation()

  if (servicio.categoria === 'Alojamiento') {
    const { serviciosIncluidos } = servicio.detalleCategoria
    if (serviciosIncluidos.length === 0) return null
    return (
      <>
        <h3 id={`${idTitulo}-incluidos`}>{t('detalle.serviciosIncluidos')}</h3>
        <ul className="detalle__etiquetas" aria-labelledby={`${idTitulo}-incluidos`}>
          {serviciosIncluidos.map((incluido) => (
            <li key={incluido}>{t(`catalogo.serviciosIncluidos.${incluido}`, { defaultValue: incluido })}</li>
          ))}
        </ul>
      </>
    )
  }

  const datos: [string, string][] = []
  if (servicio.categoria === 'Tour') {
    const tour = servicio.detalleCategoria
    datos.push([t('detalle.duracion'), t('detalle.horas', { horas: formatearHoras(tour.duracionHoras, i18n.language) })])
    datos.push([t('detalle.puntoEncuentro'), tour.puntoEncuentro])
    if (tour.nivelDificultad) datos.push([t('detalle.dificultad'), tour.nivelDificultad])
    datos.push([t('detalle.aforoGrupo'), t('detalle.personas', { count: tour.aforoMaxGrupo })])
  } else if (servicio.categoria === 'Gastronomia') {
    const restaurante = servicio.detalleCategoria
    datos.push([t('detalle.tipoCocina'), restaurante.tipoCocina])
    datos.push([t('detalle.aforoTurno'), t('detalle.personas', { count: restaurante.aforoMaxTurno })])
    if (restaurante.menuVegetariano !== null) {
      datos.push([t('detalle.menuVegetariano'), t(restaurante.menuVegetariano ? 'detalle.si' : 'detalle.no')])
    }
  } else {
    const alquiler = servicio.detalleCategoria
    datos.push([t('detalle.tipoEquipo'), alquiler.tipoVehiculoEquipo])
    datos.push([t('detalle.unidadesDisponibles'), String(alquiler.unidadesDisponibles)])
    if (alquiler.condicionesAlquiler) datos.push([t('detalle.condiciones'), alquiler.condicionesAlquiler])
  }

  return (
    <>
      <h3 id={`${idTitulo}-datos`}>{t('detalle.datos')}</h3>
      <dl className="detalle__datos" aria-labelledby={`${idTitulo}-datos`}>
        {datos.map(([etiqueta, valor]) => dato(etiqueta, valor))}
      </dl>
    </>
  )
}
