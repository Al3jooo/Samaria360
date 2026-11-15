import { Link } from 'react-router-dom'
import { useEffect, useId, useRef, useState, type Ref } from 'react'
import { useTranslation } from 'react-i18next'
import { IconoCalendario, IconoCompartir } from '../../../shared/components/Iconos'
import { compartirEnlace, enlaceServicio, type ResultadoCompartir } from '../compartirReserva'
import { centavos, formatearCentavos } from '../dinero'
import { diasEntre, formatearFecha, formatearInstante, horaCorta } from '../fechasBogota'
import type { Reserva } from '../reservasApi'
import { InsigniaReserva } from './InsigniaReserva'

/**
 * Una reserva de WF-11, con lo que se guardó al reservar (RF32): nombres, fechas, cantidades y total. «Cancelar
 * reserva» solo en las Confirmadas. Pasado `cancelableHasta` (lo calcula ms-booking, RF44) el botón queda con
 * aria-disabled y el motivo visible conectado con aria-describedby; el servidor decide igual al cancelar.
 * «Compartir» solo en las Completadas (RF49, DEC-81, DEC-100): enlace a la ficha del servicio con la hoja de compartir
 * del sistema o el portapapeles (quien la usa anuncia «Enlace copiado»); si no se puede copiar, el enlace aparece en
 * un campo de solo lectura, seleccionado, para copiarlo a mano. Cerrar la hoja de compartir no es un error.
 */
export function TarjetaReserva({
  reserva,
  ahora,
  onCancelar,
  onCompartido,
  refTitulo,
  refBoton,
}: {
  reserva: Reserva
  ahora: number
  onCancelar: (reserva: Reserva) => void
  /** Resultado de «Compartir» (para anunciar «Enlace copiado» en la región de la página). */
  onCompartido?: (resultado: ResultadoCompartir, reserva: Reserva) => void
  refTitulo?: Ref<HTMLHeadingElement>
  refBoton?: Ref<HTMLButtonElement>
}) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const [enlaceManual, setEnlaceManual] = useState<string | null>(null)
  const campoEnlace = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!enlaceManual) return
    campoEnlace.current?.focus()
    campoEnlace.current?.select()
  }, [enlaceManual])

  const compartir = async () => {
    const enlace = enlaceServicio(reserva.idServicio)
    const resultado = await compartirEnlace(reserva.nombreServicio, enlace)
    setEnlaceManual(resultado === 'manual' ? enlace : null)
    onCompartido?.(resultado, reserva)
  }
  const total = centavos(reserva.precioTotal)
  const fueraDePlazo = ahora > Date.parse(reserva.cancelableHasta)

  const fechas =
    reserva.categoria === 'Alojamiento' && reserva.fechaSalida
      ? t('misReservas.tarjeta.estadia', {
          entrada: formatearFecha(reserva.fechaEntrada, i18n.language),
          salida: formatearFecha(reserva.fechaSalida, i18n.language),
          noches: t('reserva.resumen.nNoches', { count: diasEntre(reserva.fechaEntrada, reserva.fechaSalida) }),
        })
      : t('misReservas.tarjeta.tour', {
          fecha: formatearFecha(reserva.fechaEntrada, i18n.language),
          hora: reserva.horaInicio ? horaCorta(reserva.horaInicio) : '',
        })
  const cantidades =
    reserva.categoria === 'Alojamiento'
      ? `${t('reserva.resumen.nHabitaciones', { count: reserva.cantidad })} · ${t('misReservas.tarjeta.huespedes', { count: reserva.personas })}`
      : t('reserva.resumen.nPersonas', { count: reserva.personas })

  return (
    <article className={`tarjeta-reserva tarjeta-reserva--${reserva.estado}`} aria-labelledby={`${id}-titulo`}>
      <InsigniaReserva estado={reserva.estado} />
      <h3 id={`${id}-titulo`} ref={refTitulo} tabIndex={-1} className="tarjeta-reserva__titulo">
        {reserva.nombreServicio}
      </h3>
      {reserva.nombreUnidad && <p className="tarjeta-reserva__unidad">{reserva.nombreUnidad}</p>}
      <dl className="tarjeta-reserva__datos">
        <div>
          <dt>
            <IconoCalendario />
            <span className="solo-lector">{t('misReservas.tarjeta.fechas')}</span>
          </dt>
          <dd>{fechas}</dd>
        </div>
        <div>
          <dt className="solo-lector">{t('misReservas.tarjeta.cantidades')}</dt>
          <dd>{cantidades}</dd>
        </div>
        {total !== null && (
          <div className="tarjeta-reserva__total">
            <dt>{t('reserva.resumen.total')}</dt>
            <dd>{formatearCentavos(total, i18n.language)}</dd>
          </div>
        )}
      </dl>

      {reserva.estado === 'Confirmada' &&
        (fueraDePlazo ? (
          <div className="tarjeta-reserva__acciones">
            <button
              type="button"
              className="boton-secundario deshabilitado"
              aria-disabled="true"
              aria-describedby={`${id}-motivo`}
              onClick={(evento) => evento.preventDefault()}
            >
              {t('misReservas.tarjeta.cancelar')}
            </button>
            <p id={`${id}-motivo`} className="tarjeta-reserva__motivo">
              {t('misReservas.tarjeta.fueraDePlazo', { fecha: formatearInstante(reserva.cancelableHasta, i18n.language) })}
            </p>
          </div>
        ) : (
          <div className="tarjeta-reserva__acciones">
            <button
              ref={refBoton}
              type="button"
              className="boton-secundario"
              aria-describedby={`${id}-plazo`}
              onClick={() => onCancelar(reserva)}
            >
              {t('misReservas.tarjeta.cancelar')}
            </button>
            <p id={`${id}-plazo`} className="tarjeta-reserva__plazo">
              {t('misReservas.tarjeta.cancelableHasta', {
                fecha: formatearInstante(reserva.cancelableHasta, i18n.language),
              })}
            </p>
          </div>
        ))}

      {reserva.estado === 'Completada' && (
        <div className="tarjeta-reserva__acciones">
          {/* RF17 (DEC-303): con la reserva Completada, el turista califica el servicio en su ficha. */}
          <Link className="boton-secundario" to={`/servicios/${encodeURIComponent(reserva.idServicio)}#calificaciones`}>
            {t('misReservas.calificar')}
          </Link>
          <button
            type="button"
            className="boton-secundario boton-compartir"
            aria-label={t('misReservas.compartir.boton', { servicio: reserva.nombreServicio })}
            onClick={() => void compartir()}
          >
            <IconoCompartir />
            <span aria-hidden="true">{t('misReservas.compartir.visible')}</span>
          </button>
          {enlaceManual && (
            <div className="campo tarjeta-reserva__enlace">
              <label htmlFor={`${id}-enlace`}>{t('misReservas.compartir.enlace')}</label>
              <div className="campo__control">
                <input
                  ref={campoEnlace}
                  id={`${id}-enlace`}
                  type="url"
                  readOnly
                  value={enlaceManual}
                  aria-describedby={`${id}-enlace-ayuda`}
                  onFocus={(evento) => evento.target.select()}
                />
              </div>
              <p id={`${id}-enlace-ayuda`} className="campo__ayuda">
                {t('misReservas.compartir.copiaManual')}
              </p>
            </div>
          )}
        </div>
      )}
    </article>
  )
}
