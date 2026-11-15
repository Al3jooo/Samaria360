import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { etiquetaCategoria } from '../catalogo/componentes/etiquetas'
import { centavos, formatearCentavos } from '../reservas/dinero'
import { MapaSoloLectura } from './MapaSoloLectura'
import type { ServicioGestion } from './servicioGestion'
import './servicio.css'

/** Un par etiqueta/valor; sin valor, «—» con «sin datos» para el lector de pantalla. */
export function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  const { t } = useTranslation()
  const vacio = children === null || children === undefined || children === ''
  return (
    <div>
      <dt>{etiqueta}</dt>
      <dd>
        {vacio ? (
          <span className="deshabilitado">
            <span aria-hidden="true">—</span>
            <span className="solo-lector">{t('comun.sinDatos')}</span>
          </span>
        ) : (
          children
        )}
      </dd>
    </div>
  )
}

const comoTexto = (valor: unknown) => (valor === null || valor === undefined ? '' : String(valor))

/**
 * Los datos de un servicio tal como se enviaron, con los que fijó el servidor (precio derivado y RNT): detalle propio
 * de WF-18 y revisión de WF-19 (DEC-176). Con `mapa`, la ubicación lleva además el pin en un mapa de solo lectura.
 */
/**
 * `fotos`: las fotos del registro (DEC-318), en solo lectura, para que el Administrador las vea al revisar; el detalle de
 * un servicio Publicado del Prestador no las repite, porque allí está la galería editable (RF48).
 */
export function DatosServicio({
  servicio,
  mapa = false,
  fotos = true,
}: {
  servicio: ServicioGestion
  mapa?: boolean
  fotos?: boolean
}) {
  const { t, i18n } = useTranslation()
  const dinero = (valor: unknown) => {
    const valorCentavos = centavos(valor as number | string | null)
    return valorCentavos === null ? '' : formatearCentavos(valorCentavos, i18n.language)
  }
  const detalle = servicio.detalleCategoria ?? {}
  const unidades = Array.isArray(detalle.unidades) ? (detalle.unidades as Record<string, unknown>[]) : []
  const dias = servicio.oferta?.diasOperacion ?? []
  const salidas = servicio.oferta?.salidas ?? []
  // Defensa: ms-catalog siempre envía una lista, pero un null no debe romper la página.
  const accesibilidad = servicio.condicionesAccesibilidad ?? []

  return (
    <dl className="datos-servicio">
      <Dato etiqueta={t('prestador.campos.categoria')}>{etiquetaCategoria(t, servicio.categoria)}</Dato>
      <Dato etiqueta={t('prestador.campos.rnt')}>{servicio.rnt}</Dato>
      <Dato etiqueta={t('prestador.campos.descripcion')}>{servicio.descripcion}</Dato>
      <Dato etiqueta={t('prestador.campos.telefono')}>{servicio.contacto?.telefono}</Dato>
      <Dato etiqueta={t('prestador.campos.correo')}>{servicio.contacto?.correo}</Dato>
      <Dato etiqueta={t('prestador.campos.direccion')}>{servicio.direccion}</Dato>
      <Dato etiqueta={t('prestador.ubicacion.titulo')}>
        {servicio.coordenadas && (
          <>
            {t('prestador.detalle.coordenadas', {
              latitud: servicio.coordenadas.latitud,
              longitud: servicio.coordenadas.longitud,
            })}
            {mapa && (
              <MapaSoloLectura
                latitud={servicio.coordenadas.latitud}
                longitud={servicio.coordenadas.longitud}
                nombre={servicio.nombreComercial}
              />
            )}
          </>
        )}
      </Dato>
      {fotos && (servicio.imagenes ?? []).length > 0 && (
        <Dato etiqueta={t('prestador.registro.secciones.fotos')}>
          <ul className="datos-servicio__fotos">
            {servicio.imagenes.map((ruta, indice) => (
              <li key={ruta}>
                <img src={ruta} alt={t('galeria.imagen', { n: indice + 1 })} loading="lazy" width={160} height={110} />
              </li>
            ))}
          </ul>
        </Dato>
      )}
      <Dato etiqueta={t('prestador.campos.horarioAtencion')}>{servicio.horarioAtencion}</Dato>
      <Dato etiqueta={t('prestador.campos.precioBase')}>{dinero(servicio.precioBase)}</Dato>
      <Dato etiqueta={t('prestador.registro.secciones.accesibilidad')}>
        {accesibilidad.map((condicion) => t(`catalogo.accesibilidad.${condicion}`)).join(', ')}
      </Dato>
      {servicio.categoria === 'Tour' && (
        <>
          <Dato etiqueta={t('prestador.campos.puntoEncuentro')}>{comoTexto(detalle.puntoEncuentro)}</Dato>
          <Dato etiqueta={t('prestador.campos.duracionHoras')}>{comoTexto(detalle.duracionHoras)}</Dato>
          <Dato etiqueta={t('prestador.campos.nivelDificultad')}>{comoTexto(detalle.nivelDificultad)}</Dato>
          <Dato etiqueta={t('prestador.campos.aforoMaxGrupo')}>{comoTexto(detalle.aforoMaxGrupo)}</Dato>
        </>
      )}
      {servicio.categoria === 'Gastronomia' && (
        <>
          <Dato etiqueta={t('prestador.campos.tipoCocina')}>{comoTexto(detalle.tipoCocina)}</Dato>
          <Dato etiqueta={t('prestador.campos.aforoMaxTurno')}>{comoTexto(detalle.aforoMaxTurno)}</Dato>
          <Dato etiqueta={t('prestador.campos.rangoPrecioPlato')}>{comoTexto(detalle.rangoPrecioPlato)}</Dato>
          <Dato etiqueta={t('prestador.campos.menuVegetariano')}>
            {detalle.menuVegetariano === true ? t('prestador.detalle.si') : t('prestador.detalle.no')}
          </Dato>
        </>
      )}
      {servicio.categoria === 'AlquilerEquipo' && (
        <>
          <Dato etiqueta={t('prestador.campos.tipoVehiculoEquipo')}>{comoTexto(detalle.tipoVehiculoEquipo)}</Dato>
          <Dato etiqueta={t('prestador.campos.unidadesDisponibles')}>{comoTexto(detalle.unidadesDisponibles)}</Dato>
          <Dato etiqueta={t('prestador.campos.condicionesAlquiler')}>{comoTexto(detalle.condicionesAlquiler)}</Dato>
        </>
      )}
      {servicio.categoria === 'Alojamiento' && (
        <>
          <Dato etiqueta={t('prestador.campos.serviciosIncluidos')}>
            {(Array.isArray(detalle.serviciosIncluidos) ? (detalle.serviciosIncluidos as string[]) : [])
              .map((valor) => t(`catalogo.serviciosIncluidos.${valor}`, { defaultValue: valor }))
              .join(', ')}
          </Dato>
          <Dato etiqueta={t('prestador.registro.unidades')}>
            {unidades.length > 0 && (
              <ul className="datos-servicio__unidades">
                {unidades.map((unidad, indice) => (
                  <li key={comoTexto(unidad.idUnidad) || indice}>
                    {t('prestador.detalle.unidad', {
                      nombre: comoTexto(unidad.nombre),
                      tipo: comoTexto(unidad.tipoHabitacion),
                      habitaciones: comoTexto(unidad.numHabitaciones),
                      huespedes: comoTexto(unidad.capacidadMaxHuespedes),
                      precio: dinero(unidad.precioNoche),
                    })}
                  </li>
                ))}
              </ul>
            )}
          </Dato>
        </>
      )}
      {servicio.oferta && (
        <>
          <Dato etiqueta={t('prestador.registro.diasOperacion')}>
            {dias.map((dia) => t(`comun.dias.${dia}`, { defaultValue: dia })).join(', ')}
          </Dato>
          {servicio.categoria === 'Tour' && <Dato etiqueta={t('prestador.registro.salidas')}>{salidas.join(', ')}</Dato>}
        </>
      )}
    </dl>
  )
}
