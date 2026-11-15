import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiError, servicioNoDisponible } from '../../shared/api/httpClient'
import {
  IconoAsistente,
  IconoCompartir,
  IconoDescargar,
  IconoEnviar,
  IconoEscudo,
  IconoGuardar,
  IconoInfo,
  IconoMapa,
  IconoPulso,
} from '../../shared/components/Iconos'
import { consultarPerfil } from '../perfil/perfilApi'
import { compartirTexto } from './compartirItinerario'
import {
  MENSAJE_MAXIMO,
  MENSAJES_POR_DIA,
  enviarMensaje,
  guardarItinerario,
  porDia,
  rutaPaso,
  type ItemItinerario,
  type RespuestaAsistente,
  type Sugerencia,
} from './asistenteApi'
import './asistente.css'

interface Mensaje {
  id: number
  autor: 'asistente' | 'turista'
  texto: string
  hora: string
}

/** Chips rápidos de WF-07: envían su texto como mensaje. */
const CHIPS = ['aforo', 'rutas', 'toursManana', 'alquiler'] as const

type EstadoGuardado = 'inactivo' | 'guardando' | 'guardado' | 'limite' | 'error'

/**
 * WF-07 «Asistente virtual IA» (RF04, RF35, RF36, RF59; DEC-297, DEC-298), solo para el Turista. La conversación vive
 * en esta pantalla (sin historial en el servidor). Cada respuesta trae sus sugerencias con el criterio y la
 * justificación (RF36) y, si se pidió, el itinerario por día, que se puede compartir, imprimir como PDF o guardar en el
 * perfil (RF35). Los intereses del perfil (RF53) acompañan cada mensaje, solo para calcular el criterio.
 */
export function AsistentePage() {
  const { t, i18n } = useTranslation()
  const idCampo = useId()
  const [mensajes, setMensajes] = useState<Mensaje[]>(() => [
    { id: 0, autor: 'asistente', texto: '', hora: hora(new Date()) },
  ])
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [beta, setBeta] = useState(true)
  const [ultima, setUltima] = useState<RespuestaAsistente | null>(null)
  // Sin endpoint de uso: hasta la primera respuesta no se sabe cuántos mensajes quedan hoy (null).
  const [restantes, setRestantes] = useState<number | null>(null)
  const [guardado, setGuardado] = useState<EstadoGuardado>('inactivo')
  const [aviso, setAviso] = useState<string | null>(null)
  const intereses = useRef<string[]>([])
  const final = useRef<HTMLDivElement>(null)
  const siguienteId = useRef(1)

  useEffect(() => {
    let vigente = true
    consultarPerfil()
      .then((perfil) => {
        if (vigente) intereses.current = perfil.categoriasInteres
      })
      .catch(() => {
        // Sin perfil, el asistente sigue: solo pierde el criterio «preferencias».
      })
    return () => {
      vigente = false
    }
  }, [])

  useEffect(() => {
    final.current?.scrollIntoView?.({ block: 'nearest' })
  }, [mensajes])

  const agregar = (autor: Mensaje['autor'], contenido: string) =>
    setMensajes((actuales) => [
      ...actuales,
      { id: siguienteId.current++, autor, texto: contenido, hora: hora(new Date()) },
    ])

  const enviar = async (contenido: string) => {
    const limpio = contenido.trim()
    if (!limpio || enviando) return
    setError(null)
    setAviso(null)
    agregar('turista', limpio)
    setTexto('')
    setEnviando(true)
    try {
      const respuesta = await enviarMensaje(limpio, i18n.language, intereses.current)
      agregar('asistente', respuesta.respuesta)
      setRestantes(respuesta.mensajesRestantes)
      setUltima(respuesta)
      if (respuesta.itinerario.length > 0) setGuardado('inactivo')
    } catch (causa) {
      setError(mensajeError(causa))
      if (causa instanceof ApiError && causa.codigo === 'LIMITE_MENSAJES') setRestantes(0)
    } finally {
      setEnviando(false)
    }
  }

  const alEnviar = (evento: FormEvent) => {
    evento.preventDefault()
    void enviar(texto)
  }

  const mensajeError = (causa: unknown): string => {
    if (causa instanceof ApiError) {
      if (causa.codigo === 'LIMITE_MENSAJES') return t('asistente.errores.limite', { n: MENSAJES_POR_DIA })
      if (causa.codigo === 'DATOS_INVALIDOS') return t('asistente.errores.largo', { n: MENSAJE_MAXIMO })
      if (servicioNoDisponible(causa)) return t('asistente.errores.noDisponible')
    }
    return t('asistente.errores.generico')
  }

  const itinerario = ultima?.itinerario ?? []
  const prestadores = (ultima?.sugerencias ?? []).filter((s) => s.tipo === 'Servicio')
  const sinMensajes = restantes !== null && restantes <= 0

  const guardar = async () => {
    if (itinerario.length === 0) return
    setGuardado('guardando')
    try {
      await guardarItinerario({
        nombre: t('asistente.itinerario.nombrePorDefecto', { fecha: new Date().toLocaleDateString(i18n.language) }),
        items: itinerario,
      })
      setGuardado('guardado')
    } catch (causa) {
      setGuardado(causa instanceof ApiError && causa.codigo === 'LIMITE_ITINERARIOS' ? 'limite' : 'error')
    }
  }

  const compartir = async () => {
    const resultado = await compartirTexto(t('asistente.itinerario.titulo'), textoItinerario(itinerario, t))
    if (resultado === 'copiado') setAviso(t('asistente.itinerario.copiado'))
    else if (resultado === 'manual') setAviso(t('asistente.itinerario.sinCompartir'))
  }

  return (
    <div className="asistente">
      <header className="asistente__cabecera">
        <span className="asistente__icono" aria-hidden="true">
          <IconoAsistente />
        </span>
        <div>
          <h1>{t('asistente.titulo')}</h1>
          <p className="asistente__estado">
            <span className="asistente__punto" aria-hidden="true" />
            <strong>{t('asistente.enLinea')}</strong>
            <span aria-hidden="true"> · </span>
            {t('asistente.subtitulo')}
          </p>
        </div>
      </header>

      {beta && (
        <div className="asistente__beta" role="note">
          <IconoInfo />
          <p>{t('asistente.beta')}</p>
          <button type="button" className="asistente__cerrar" onClick={() => setBeta(false)} aria-label={t('asistente.cerrarBeta')}>
            ×
          </button>
        </div>
      )}

      <div className="asistente__cuerpo">
        <section className="asistente__chat" aria-label={t('asistente.conversacion')}>
          <ol className="asistente__mensajes" aria-live="polite">
            {mensajes.map((m) => (
              <li key={m.id} className={`burbuja burbuja--${m.autor}`}>
                <span className="burbuja__autor">{m.autor === 'asistente' ? t('asistente.autorAsistente') : t('asistente.autorTu')}</span>
                <p className="burbuja__texto">{m.id === 0 ? t('asistente.saludo') : m.texto}</p>
                <span className="burbuja__hora">{m.hora}</span>
              </li>
            ))}
            {enviando && (
              <li className="burbuja burbuja--asistente burbuja--escribiendo" role="status">
                {t('asistente.escribiendo')}
              </li>
            )}
          </ol>
          <div ref={final} />

          {ultima && !ultima.catalogoDisponible && (
            <p className="asistente__alerta" role="status">
              {t('asistente.catalogoNoDisponible')}
            </p>
          )}
          {error && (
            <p className="asistente__error" role="alert">
              {error}
            </p>
          )}

          <div className="asistente__acciones">
            <button
              type="button"
              className="boton-principal"
              onClick={() => void guardar()}
              disabled={itinerario.length === 0 || guardado === 'guardando' || guardado === 'guardado'}
            >
              <IconoGuardar />
              {t('asistente.guardar')}
            </button>
            <Link to="/explorar" className="boton-secundario">
              <IconoMapa />
              {t('asistente.verMapa')}
            </Link>
          </div>
          {guardado === 'guardado' && (
            <p className="asistente__exito" role="status">
              {t('asistente.guardado')} <Link to="/perfil/itinerarios">{t('asistente.verItinerarios')}</Link>
            </p>
          )}
          {guardado === 'limite' && (
            <p className="asistente__error" role="alert">
              {t('asistente.errores.limiteItinerarios')}
            </p>
          )}
          {guardado === 'error' && (
            <p className="asistente__error" role="alert">
              {t('asistente.errores.guardar')}
            </p>
          )}

          <div className="asistente__chips" role="group" aria-label={t('asistente.sugerenciasRapidas')}>
            {CHIPS.map((chip) => (
              <button
                key={chip}
                type="button"
                className="chip"
                disabled={enviando || sinMensajes}
                onClick={() => void enviar(t(`asistente.chips.${chip}`))}
              >
                {t(`asistente.chips.${chip}`)}
              </button>
            ))}
          </div>

          <form className="asistente__formulario" onSubmit={alEnviar}>
            <label htmlFor={idCampo}>{t('asistente.campo')}</label>
            <div className="asistente__entrada">
              <input
                id={idCampo}
                type="text"
                value={texto}
                maxLength={MENSAJE_MAXIMO}
                placeholder={t('asistente.placeholder')}
                onChange={(e) => setTexto(e.target.value)}
                disabled={sinMensajes}
                autoComplete="off"
              />
              <button
                type="submit"
                className="asistente__enviar"
                disabled={enviando || sinMensajes || texto.trim() === ''}
                aria-label={t('asistente.enviar')}
              >
                <IconoEnviar />
              </button>
            </div>
            <p className="asistente__restantes">
              {restantes === null
                ? t('asistente.limiteDiario', { total: MENSAJES_POR_DIA })
                : t('asistente.restantes', { n: restantes, total: MENSAJES_POR_DIA })}
            </p>
          </form>
        </section>

        <aside className="asistente__lateral">
          <section className="itinerario-sugerido" aria-labelledby={`${idCampo}-itinerario`}>
            <h2 id={`${idCampo}-itinerario`}>{t('asistente.itinerario.titulo')}</h2>
            {itinerario.length === 0 ? (
              <p className="itinerario-sugerido__vacio">{t('asistente.itinerario.vacio')}</p>
            ) : (
              <ol className="itinerario-sugerido__dias">
                {porDia(itinerario).map(({ dia, items }) => (
                  <li key={dia}>
                    <h3>{t('asistente.itinerario.dia', { n: dia })}</h3>
                    <ul>
                      {items.map((paso) => (
                        <li key={`${paso.tipo}-${paso.refId}`}>
                          <Link to={rutaPaso(paso.tipo, paso.refId)}>{paso.tituloActividad}</Link>
                          <span>{t(`asistente.tipo.${paso.tipo}`)}</span>
                        </li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ol>
            )}
            <div className="itinerario-sugerido__acciones">
              <button type="button" className="boton-texto" disabled={itinerario.length === 0} onClick={() => void compartir()}>
                <IconoCompartir />
                {t('asistente.itinerario.compartir')}
              </button>
              <button type="button" className="boton-texto" disabled={itinerario.length === 0} onClick={() => window.print()}>
                <IconoDescargar />
                {t('asistente.itinerario.pdf')}
              </button>
            </div>
            {aviso && (
              <p className="itinerario-sugerido__aviso" role="status">
                {aviso}
              </p>
            )}
          </section>

          <section className="prestadores-mencionados" aria-labelledby={`${idCampo}-prestadores`}>
            <h2 id={`${idCampo}-prestadores`}>{t('asistente.prestadores')}</h2>
            {prestadores.length === 0 ? (
              <p className="itinerario-sugerido__vacio">{t('asistente.sinPrestadores')}</p>
            ) : (
              <ul>
                {prestadores.map((s) => (
                  <PrestadorMencionado key={s.id} sugerencia={s} />
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>

      <section className="asistente__ayuda" aria-labelledby={`${idCampo}-ayuda`}>
        <h2 id={`${idCampo}-ayuda`}>{t('asistente.ayuda.titulo')}</h2>
        <ul>
          <li>
            <span className="asistente__ayuda-icono" aria-hidden="true">
              <IconoPulso />
            </span>
            <h3>{t('asistente.ayuda.aforosTitulo')}</h3>
            <p>{t('asistente.ayuda.aforos')}</p>
          </li>
          <li>
            <span className="asistente__ayuda-icono" aria-hidden="true">
              <IconoMapa />
            </span>
            <h3>{t('asistente.ayuda.rutasTitulo')}</h3>
            <p>{t('asistente.ayuda.rutas')}</p>
          </li>
          <li>
            <span className="asistente__ayuda-icono" aria-hidden="true">
              <IconoEscudo />
            </span>
            <h3>{t('asistente.ayuda.prestadoresTitulo')}</h3>
            <p>{t('asistente.ayuda.prestadores')}</p>
          </li>
        </ul>
      </section>
    </div>
  )
}

function PrestadorMencionado({ sugerencia }: { sugerencia: Sugerencia }) {
  const { t } = useTranslation()
  return (
    <li>
      <Link to={rutaPaso(sugerencia.tipo, sugerencia.id)} className="prestadores-mencionados__enlace">
        <span>
          <strong>{sugerencia.nombre}</strong>
          <small>
            {sugerencia.categoria ? t(`asistente.categoria.${sugerencia.categoria}`, sugerencia.categoria) : ''}
            {' · '}
            {sugerencia.justificacion}
          </small>
        </span>
        <span aria-hidden="true">›</span>
      </Link>
    </li>
  )
}

function hora(fecha: Date): string {
  return `${String(fecha.getHours()).padStart(2, '0')}:${String(fecha.getMinutes()).padStart(2, '0')}`
}

/** Texto plano del itinerario para compartir: un día por bloque. */
export function textoItinerario(items: ItemItinerario[], t: (clave: string, opciones?: Record<string, unknown>) => string): string {
  return porDia(items)
    .map(({ dia, items: pasos }) => [t('asistente.itinerario.dia', { n: dia }), ...pasos.map((p) => `• ${p.tituloActividad}`)].join('\n'))
    .join('\n\n')
}
