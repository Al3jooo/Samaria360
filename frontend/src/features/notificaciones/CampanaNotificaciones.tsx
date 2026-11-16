import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { IconoCampana } from '../../shared/components/Iconos'
import { formatearDia } from '../../shared/formato'
import {
  listarNotificaciones,
  marcarLeida,
  marcarTodas,
  type Notificacion,
  type PaginaNotificaciones,
} from './notificacionesApi'
import './campana.css'

/** Cada minuto se vuelve a consultar el contador; al abrir el panel, la lista (RF63, DEC-294). */
const INTERVALO_MS = 60_000

/** Destino de cada notificación: el Turista va a «Mis reservas» (WF-11); el Prestador, a su historial (WF-23). */
function destino(n: Notificacion): string {
  return n.tipo === 'NUEVA_RESERVA' ? '/prestador/reservas' : '/mis-reservas'
}

/**
 * Campana de notificaciones internas (RF63; WF-29, propuesto por arquitectura): en el encabezado, para el Turista
 * (cambios de estado de sus reservas) y el Prestador (reservas nuevas). Contador de no leídas, panel con las 20 más
 * recientes en el idioma activo, «Marcar todas como leídas» y lectura al abrir una. Si ms-analytics no responde, la
 * campana sigue visible sin contador: no bloquea el resto del encabezado.
 */
export function CampanaNotificaciones() {
  const { t, i18n } = useTranslation()
  const idPanel = useId()
  const [abierta, setAbierta] = useState(false)
  const [pagina, setPagina] = useState<PaginaNotificaciones | null>(null)
  const [error, setError] = useState(false)
  const contenedor = useRef<HTMLDivElement>(null)

  const cargar = useCallback(async () => {
    try {
      setPagina(await listarNotificaciones(0))
      setError(false)
    } catch {
      setError(true)
    }
  }, [])

  useEffect(() => {
    void cargar()
    const intervalo = window.setInterval(() => void cargar(), INTERVALO_MS)
    return () => window.clearInterval(intervalo)
  }, [cargar])

  useEffect(() => {
    if (!abierta) return
    const alPulsarFuera = (evento: PointerEvent) => {
      if (contenedor.current && !contenedor.current.contains(evento.target as Node)) setAbierta(false)
    }
    const alEscape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') setAbierta(false)
    }
    document.addEventListener('pointerdown', alPulsarFuera)
    document.addEventListener('keydown', alEscape)
    return () => {
      document.removeEventListener('pointerdown', alPulsarFuera)
      document.removeEventListener('keydown', alEscape)
    }
  }, [abierta])

  const noLeidas = pagina?.noLeidas ?? 0

  const texto = (n: Notificacion) =>
    t(`notificaciones.tipos.${n.tipo}`, {
      servicio: n.datos.nombreServicio ?? '',
      fecha: n.datos.fecha ? formatearDia(`${n.datos.fecha}T12:00:00Z`, i18n.language) : '',
      count: Number(n.datos.personas ?? 0),
      defaultValue: n.mensaje,
    })

  const leer = async (n: Notificacion) => {
    setAbierta(false)
    if (n.leida) return
    try {
      await marcarLeida(n.id)
    } finally {
      void cargar()
    }
  }

  const leerTodas = async () => {
    try {
      await marcarTodas()
    } finally {
      void cargar()
    }
  }

  return (
    <div className="campana" ref={contenedor}>
      <button
        type="button"
        className="campana__boton"
        aria-label={noLeidas > 0 ? t('notificaciones.botonConNuevas', { count: noLeidas }) : t('notificaciones.boton')}
        aria-expanded={abierta}
        aria-controls={idPanel}
        onClick={() => {
          setAbierta((valor) => !valor)
          if (!abierta) void cargar()
        }}
      >
        <IconoCampana />
        {noLeidas > 0 && (
          <span className="campana__contador" aria-hidden="true">
            {noLeidas > 9 ? '9+' : noLeidas}
          </span>
        )}
      </button>
      {abierta && (
        <div id={idPanel} className="campana__panel" role="region" aria-label={t('notificaciones.titulo')}>
          <div className="campana__cabecera">
            <h2>{t('notificaciones.titulo')}</h2>
            {noLeidas > 0 && (
              <button type="button" className="campana__todas" onClick={() => void leerTodas()}>
                {t('notificaciones.marcarTodas')}
              </button>
            )}
          </div>
          {error && <p className="campana__vacia">{t('notificaciones.error')}</p>}
          {!error && pagina && pagina.elementos.length === 0 && (
            <p className="campana__vacia">{t('notificaciones.vacia')}</p>
          )}
          {!error && pagina && pagina.elementos.length > 0 && (
            <ul className="campana__lista">
              {pagina.elementos.map((n) => (
                <li key={n.id} className={n.leida ? 'campana__item' : 'campana__item campana__item--nueva'}>
                  <Link to={destino(n)} onClick={() => void leer(n)}>
                    <span className="campana__texto">{texto(n)}</span>
                    <span className="campana__fecha">{formatearDia(n.fecha, i18n.language)}</span>
                    {!n.leida && <span className="solo-lector">{t('notificaciones.noLeida')}</span>}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
