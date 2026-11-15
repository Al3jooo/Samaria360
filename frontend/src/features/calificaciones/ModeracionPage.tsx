import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../shared/api/httpClient'
import { formatearInstante } from '../reservas/fechasBogota'
import {
  MOTIVO_MAXIMO,
  listarCalificacionesModeracion,
  listarReportes,
  marcarRevisado,
  moderar,
  rutaEntidad,
  type AccionModeracion,
  type CalificacionModeracion,
  type PaginaModeracion,
  type ReporteModeracion,
} from './calificacionesApi'
import { EstrellasFijas } from './Estrellas'
import './calificaciones.css'

type Pestana = 'calificaciones' | 'reportes'

/**
 * WF-34 «Moderación» (RF29 y RF57; DEC-303), solo para el Administrador: pestañas «Calificaciones» (ocultar, volver
 * a mostrar o eliminar con un motivo de 1 a 500 caracteres, que queda en la auditoría) y «Reportes» (marcar revisado).
 * La pestaña y el filtro de estado van en la URL; 20 por página.
 */
export function ModeracionPage() {
  const { t } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const pestana: Pestana = parametros.get('pestana') === 'reportes' ? 'reportes' : 'calificaciones'
  const estado = parametros.get('estado') ?? (pestana === 'reportes' ? 'Pendiente' : '')
  const pagina = Math.max(0, (Number(parametros.get('pagina')) || 1) - 1)

  const cambiar = (cambios: Record<string, string>) => {
    const nuevos = new URLSearchParams(parametros)
    Object.entries(cambios).forEach(([clave, valor]) => (valor ? nuevos.set(clave, valor) : nuevos.delete(clave)))
    setParametros(nuevos)
  }

  return (
    <div className="moderacion">
      <h1>{t('moderacion.titulo')}</h1>
      <div className="pestanas-moderacion" role="tablist" aria-label={t('moderacion.titulo')}>
        {(['calificaciones', 'reportes'] as const).map((p) => (
          <button
            key={p}
            type="button"
            role="tab"
            aria-selected={pestana === p}
            onClick={() => setParametros(p === 'reportes' ? { pestana: 'reportes' } : {})}
          >
            {t(`moderacion.pestanas.${p}`)}
          </button>
        ))}
      </div>
      <div className="filtros-moderacion">
        <label>
          {t('moderacion.estado')}
          <select value={estado} onChange={(e) => cambiar({ estado: e.target.value, pagina: '' })}>
            {pestana === 'calificaciones' ? (
              <>
                <option value="">{t('moderacion.todas')}</option>
                <option value="Visible">{t('moderacion.estados.Visible')}</option>
                <option value="Oculta">{t('moderacion.estados.Oculta')}</option>
              </>
            ) : (
              <>
                <option value="Pendiente">{t('moderacion.estados.Pendiente')}</option>
                <option value="Revisado">{t('moderacion.estados.Revisado')}</option>
              </>
            )}
          </select>
        </label>
      </div>
      {pestana === 'calificaciones' ? (
        <PanelCalificaciones estado={estado} pagina={pagina} irA={(n) => cambiar({ pagina: String(n + 1) })} />
      ) : (
        <PanelReportes estado={estado} pagina={pagina} irA={(n) => cambiar({ pagina: String(n + 1) })} />
      )}
    </div>
  )
}

function usePagina<T>(cargar: () => Promise<PaginaModeracion<T>>) {
  const [datos, setDatos] = useState<PaginaModeracion<T> | null>(null)
  const [error, setError] = useState(false)
  const recargar = useCallback(() => {
    setError(false)
    cargar()
      .then(setDatos)
      .catch(() => setError(true))
  }, [cargar])
  useEffect(() => {
    recargar()
  }, [recargar])
  return { datos, error, recargar }
}

function Paginacion({ datos, irA }: { datos: PaginaModeracion<unknown>; irA: (n: number) => void }) {
  const { t } = useTranslation()
  const total = Math.max(1, Math.ceil(datos.totalElementos / datos.tamano))
  if (total <= 1) return null
  return (
    <nav className="acciones-moderacion" aria-label={t('moderacion.paginacion')}>
      <button type="button" className="boton-secundario" disabled={datos.pagina === 0} onClick={() => irA(datos.pagina - 1)}>
        {t('conocimiento.anterior')}
      </button>
      <span>{t('conocimiento.pagina', { n: datos.pagina + 1, total })}</span>
      <button
        type="button"
        className="boton-secundario"
        disabled={datos.pagina + 1 >= total}
        onClick={() => irA(datos.pagina + 1)}
      >
        {t('conocimiento.siguiente')}
      </button>
    </nav>
  )
}

function PanelCalificaciones({ estado, pagina, irA }: { estado: string; pagina: number; irA: (n: number) => void }) {
  const { t } = useTranslation()
  const cargar = useCallback(() => listarCalificacionesModeracion(estado, pagina), [estado, pagina])
  const { datos, error, recargar } = usePagina(cargar)
  const [anuncio, setAnuncio] = useState('')

  if (error) return <p className="calificaciones__error" role="alert">{t('moderacion.error')}</p>
  if (!datos) return <p className="estado-carga" role="status">{t('moderacion.cargando')}</p>
  return (
    <>
      <p className="solo-lector" role="status">
        {anuncio}
      </p>
      {datos.elementos.length === 0 ? (
        <p className="calificaciones__vacio">{t('moderacion.sinCalificaciones')}</p>
      ) : (
        <ol className="lista-moderacion">
          {datos.elementos.map((c) => (
            <TarjetaCalificacion
              key={c.id}
              calificacion={c}
              alModerar={(texto) => {
                setAnuncio(texto)
                recargar()
              }}
            />
          ))}
        </ol>
      )}
      <Paginacion datos={datos} irA={irA} />
    </>
  )
}

function TarjetaCalificacion({
  calificacion: c,
  alModerar,
}: {
  calificacion: CalificacionModeracion
  alModerar: (anuncio: string) => void
}) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const [accion, setAccion] = useState<AccionModeracion | null>(null)
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    if (!accion) return
    setEnviando(true)
    setError(null)
    try {
      await moderar(c.id, accion, motivo.trim())
      alModerar(t(`moderacion.hecho.${accion}`))
      setAccion(null)
      setMotivo('')
    } catch (causa) {
      const codigo = causa instanceof ApiError ? causa.codigo : ''
      setError(t(`moderacion.errores.${codigo}`, t('moderacion.errores.generico')))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <li>
      <span className={`etiqueta-estado etiqueta-estado--${c.estado}`}>{t(`moderacion.estados.${c.estado}`)}</span>
      <Link to={rutaEntidad(c.tipoEntidad, c.idEntidad)}>
        {c.nombreEntidad ?? t(`moderacion.tipos.${c.tipoEntidad}`)}
      </Link>
      <span className="solo-lector">{t('calificaciones.estrellasDe', { n: c.estrellas })}</span>
      <EstrellasFijas valor={c.estrellas} />
      <time dateTime={c.fecha}>{formatearInstante(c.fecha, i18n.language)}</time>
      {c.comentario && <p>{c.comentario}</p>}
      {c.motivoModeracion && (
        <p>
          <strong>{t('moderacion.motivoAnterior')}</strong> {c.motivoModeracion}
        </p>
      )}
      {accion ? (
        <form className="formulario-moderacion" onSubmit={(e) => void enviar(e)}>
          <label htmlFor={`${id}-motivo`}>{t(`moderacion.motivoPara.${accion}`)}</label>
          <textarea
            id={`${id}-motivo`}
            value={motivo}
            maxLength={MOTIVO_MAXIMO}
            required
            onChange={(e) => setMotivo(e.target.value)}
          />
          {error && (
            <p className="calificaciones__error" role="alert">
              {error}
            </p>
          )}
          <div className="acciones-moderacion">
            <button type="submit" className="boton-principal" disabled={enviando || motivo.trim() === ''}>
              {t(`moderacion.confirmar.${accion}`)}
            </button>
            <button type="button" className="boton-secundario" onClick={() => setAccion(null)}>
              {t('moderacion.cancelar')}
            </button>
          </div>
        </form>
      ) : (
        <div className="acciones-moderacion">
          {c.estado === 'Visible' ? (
            <button type="button" className="boton-secundario" onClick={() => setAccion('ocultamiento')}>
              {t('moderacion.acciones.ocultamiento')}
            </button>
          ) : (
            <button type="button" className="boton-secundario" onClick={() => setAccion('visibilidad')}>
              {t('moderacion.acciones.visibilidad')}
            </button>
          )}
          <button type="button" className="boton-texto-enlace" onClick={() => setAccion('eliminacion')}>
            {t('moderacion.acciones.eliminacion')}
          </button>
        </div>
      )}
    </li>
  )
}

function PanelReportes({ estado, pagina, irA }: { estado: string; pagina: number; irA: (n: number) => void }) {
  const { t, i18n } = useTranslation()
  const cargar = useCallback(() => listarReportes(estado, pagina), [estado, pagina])
  const { datos, error, recargar } = usePagina<ReporteModeracion>(cargar)
  const [aviso, setAviso] = useState<string | null>(null)

  const revisar = async (id: string) => {
    setAviso(null)
    try {
      await marcarRevisado(id)
      setAviso(t('moderacion.revisado'))
    } catch (causa) {
      setAviso(causa instanceof ApiError && causa.codigo === 'ESTADO_CAMBIADO'
        ? t('moderacion.errores.ESTADO_CAMBIADO') : t('moderacion.errores.generico'))
    }
    recargar()
  }

  if (error) return <p className="calificaciones__error" role="alert">{t('moderacion.error')}</p>
  if (!datos) return <p className="estado-carga" role="status">{t('moderacion.cargando')}</p>
  return (
    <>
      {aviso && <p role="status">{aviso}</p>}
      {datos.elementos.length === 0 ? (
        <p className="calificaciones__vacio">{t('moderacion.sinReportes')}</p>
      ) : (
        <ol className="lista-moderacion">
          {datos.elementos.map((r) => (
            <li key={r.id}>
              <span className={`etiqueta-estado etiqueta-estado--${r.estado}`}>{t(`moderacion.estados.${r.estado}`)}</span>
              <strong>{t(`reportes.motivos.${r.motivo}`)}</strong>
              <Link to={rutaEntidad(r.tipoEntidad, r.idEntidad)}>{t(`moderacion.verFicha.${r.tipoEntidad}`)}</Link>
              <time dateTime={r.fecha}>{formatearInstante(r.fecha, i18n.language)}</time>
              {r.descripcion && <p>{r.descripcion}</p>}
              {r.estado === 'Pendiente' && (
                <div className="acciones-moderacion">
                  <button type="button" className="boton-secundario" onClick={() => void revisar(r.id)}>
                    {t('moderacion.marcarRevisado')}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
      <Paginacion datos={datos} irA={irA} />
    </>
  )
}
