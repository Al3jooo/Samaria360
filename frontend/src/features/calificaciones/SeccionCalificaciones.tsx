import { useEffect, useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { ApiError } from '../../shared/api/httpClient'
import { useSesion } from '../auth/useSesion'
import { formatearInstante } from '../reservas/fechasBogota'
import {
  COMENTARIO_MAXIMO,
  calificar,
  consultarElegibilidad,
  formatearPromedio,
  type Elegibilidad,
  type TipoEntidad,
} from './calificacionesApi'
import { EstrellasFijas } from './Estrellas'
import type { EstadoCalificaciones } from './useCalificacionesEntidad'
import './calificaciones.css'

/**
 * «Calificaciones y reseñas» de WF-06 (RF17; decisión 1 del equipo, DEC-303): el promedio, las reseñas visibles (de la
 * más reciente a la más antigua, 10 por página) y, para el Turista que puede, el formulario de 1 a 5 estrellas con un
 * comentario opcional de hasta 500 caracteres. Un servicio se califica con una reserva Completada; un atractivo, con la
 * cuenta Activa; una sola vez, sin edición.
 */
export function SeccionCalificaciones({
  tipo,
  id,
  resultado,
  recargar,
  verMas,
}: {
  tipo: TipoEntidad
  id: string
  resultado: EstadoCalificaciones
  recargar: () => Promise<void>
  verMas: () => Promise<void>
}) {
  const { t, i18n } = useTranslation()
  const idTitulo = useId()
  const { sesion } = useSesion()
  const ubicacion = useLocation()
  const esTurista = sesion.estado === 'autenticado' && sesion.usuario.rol === 'Turista'
  const [elegibilidad, setElegibilidad] = useState<Elegibilidad | null>(null)
  const [recienCalificada, setRecienCalificada] = useState(false)

  useEffect(() => {
    let vigente = true
    if (!esTurista) return
    consultarElegibilidad(tipo, id)
      .then((e) => {
        if (vigente) setElegibilidad(e)
      })
      .catch(() => {
        if (vigente) setElegibilidad(null)
      })
    return () => {
      vigente = false
    }
  }, [esTurista, tipo, id])

  return (
    <section id="calificaciones" className="calificaciones" aria-labelledby={idTitulo}>
      <h2 id={idTitulo}>{t('calificaciones.titulo')}</h2>
      {resultado.estado === 'cargando' && (
        <p className="estado-carga" role="status">
          {t('calificaciones.cargando')}
        </p>
      )}
      {resultado.estado === 'error' && <p role="alert">{t('calificaciones.noDisponibles')}</p>}
      {resultado.estado === 'ok' && (
        <>
          {resultado.datos.promedio === null ? (
            <p className="calificaciones__vacio">{t('calificaciones.sinCalificaciones')}</p>
          ) : (
            <p className="calificaciones__promedio">
              <strong>{formatearPromedio(resultado.datos.promedio, i18n.language)}</strong>
              <EstrellasFijas valor={resultado.datos.promedio} />
              <span>{t('calificaciones.cantidad', { count: resultado.datos.cantidad })}</span>
            </p>
          )}
          {resultado.datos.elementos.length > 0 && (
            <ol className="calificaciones__lista">
              {resultado.datos.elementos.map((c) => (
                <li key={c.id}>
                  <span className="solo-lector">{t('calificaciones.estrellasDe', { n: c.estrellas })}</span>
                  <EstrellasFijas valor={c.estrellas} />
                  <time dateTime={c.fecha}>{formatearInstante(c.fecha, i18n.language)}</time>
                  {c.comentario && <p>{c.comentario}</p>}
                </li>
              ))}
            </ol>
          )}
          {resultado.datos.elementos.length < resultado.datos.totalElementos && (
            <button type="button" className="boton-secundario" onClick={() => void verMas()}>
              {t('calificaciones.verMas')}
            </button>
          )}
        </>
      )}

      {sesion.estado === 'anonimo' && (
        <p className="calificaciones__invitacion">
          <Link to="/login" state={{ desde: `${ubicacion.pathname}${ubicacion.search}` }}>
            {t('calificaciones.iniciarSesion')}
          </Link>
        </p>
      )}
      {recienCalificada && (
        <p className="calificaciones__exito" role="status">
          {t('calificaciones.gracias')}
        </p>
      )}
      {esTurista && !recienCalificada && elegibilidad && !elegibilidad.puedeCalificar && (
        <p className="calificaciones__invitacion" role="note">
          {t(`calificaciones.motivos.${elegibilidad.motivo ?? 'OTRO'}`, t('calificaciones.motivos.OTRO'))}
        </p>
      )}
      {esTurista && elegibilidad?.puedeCalificar && (
        <FormularioCalificacion
          tipo={tipo}
          id={id}
          alCalificar={async () => {
            setRecienCalificada(true)
            setElegibilidad({ puedeCalificar: false, motivo: 'CALIFICACION_DUPLICADA', propia: null })
            await recargar()
          }}
        />
      )}
    </section>
  )
}

function FormularioCalificacion({
  tipo,
  id,
  alCalificar,
}: {
  tipo: TipoEntidad
  id: string
  alCalificar: () => Promise<void>
}) {
  const { t } = useTranslation()
  const idGrupo = useId()
  const [estrellas, setEstrellas] = useState(0)
  const [comentario, setComentario] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    if (estrellas < 1) {
      setError(t('calificaciones.errores.estrellas'))
      return
    }
    setEnviando(true)
    setError(null)
    try {
      await calificar(tipo, id, estrellas, comentario.trim())
      await alCalificar()
    } catch (causa) {
      const codigo = causa instanceof ApiError ? causa.codigo : ''
      setError(t(`calificaciones.motivos.${codigo}`, t('calificaciones.errores.generico')))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="calificaciones__formulario" onSubmit={(e) => void enviar(e)}>
      <fieldset>
        <legend id={idGrupo}>{t('calificaciones.tuCalificacion')}</legend>
        <div className="calificaciones__selector">
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className={n <= estrellas ? 'activa' : ''}>
              <input
                type="radio"
                name={`${idGrupo}-estrellas`}
                value={n}
                checked={estrellas === n}
                onChange={() => setEstrellas(n)}
              />
              <span aria-hidden="true">★</span>
              <span className="solo-lector">{t('calificaciones.estrellasDe', { n })}</span>
            </label>
          ))}
        </div>
      </fieldset>
      <label className="calificaciones__comentario">
        {t('calificaciones.comentario')}
        <textarea value={comentario} maxLength={COMENTARIO_MAXIMO} onChange={(e) => setComentario(e.target.value)} />
        <span>{t('calificaciones.caracteres', { n: [...comentario].length, max: COMENTARIO_MAXIMO })}</span>
      </label>
      {error && (
        <p className="calificaciones__error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="boton-principal" disabled={enviando}>
        {t('calificaciones.enviar')}
      </button>
    </form>
  )
}
