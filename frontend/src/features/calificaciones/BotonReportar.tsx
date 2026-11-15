import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { ApiError } from '../../shared/api/httpClient'
import { useSesion } from '../auth/useSesion'
import {
  DESCRIPCION_REPORTE_MAXIMA,
  MOTIVOS_REPORTE,
  reportar,
  type MotivoReporte,
  type TipoEntidad,
} from './calificacionesApi'
import './calificaciones.css'

/**
 * «Reportar información falsa o desactualizada» de WF-06 (RF57; DEC-301): el Turista elige un motivo de la lista y
 * puede agregar una descripción de hasta 500 caracteres. Sin sesión, lleva al inicio de sesión; los demás roles no lo
 * ven.
 */
export function BotonReportar({ tipo, id }: { tipo: TipoEntidad; id: string }) {
  const { t } = useTranslation()
  const idFormulario = useId()
  const { sesion } = useSesion()
  const ubicacion = useLocation()
  const [abierto, setAbierto] = useState(false)
  const [motivo, setMotivo] = useState<MotivoReporte | ''>('')
  const [descripcion, setDescripcion] = useState('')
  const [estado, setEstado] = useState<'editando' | 'enviando' | 'enviado'>('editando')
  const [error, setError] = useState<string | null>(null)

  if (sesion.estado === 'anonimo') {
    return (
      <Link to="/login" state={{ desde: `${ubicacion.pathname}${ubicacion.search}` }} className="boton-texto-enlace">
        {t('detalle.reportar')}
      </Link>
    )
  }
  if (sesion.estado !== 'autenticado' || sesion.usuario.rol !== 'Turista') return null

  if (estado === 'enviado') {
    return (
      <p className="calificaciones__exito" role="status">
        {t('reportes.enviado')}
      </p>
    )
  }

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    if (!motivo) {
      setError(t('reportes.errores.motivo'))
      return
    }
    setEstado('enviando')
    setError(null)
    try {
      await reportar(tipo, id, motivo, descripcion.trim())
      setEstado('enviado')
    } catch (causa) {
      const codigo = causa instanceof ApiError ? causa.codigo : ''
      setError(t(`reportes.errores.${codigo}`, t('reportes.errores.generico')))
      setEstado('editando')
    }
  }

  return (
    <div className="reportar">
      <button
        type="button"
        className="boton-texto-enlace"
        aria-expanded={abierto}
        aria-controls={idFormulario}
        onClick={() => setAbierto((a) => !a)}
      >
        {t('detalle.reportar')}
      </button>
      {abierto && (
        <form id={idFormulario} className="reportar__formulario" onSubmit={(e) => void enviar(e)}>
          <label>
            {t('reportes.motivo')}
            <select value={motivo} onChange={(e) => setMotivo(e.target.value as MotivoReporte)} required>
              <option value="">{t('reportes.elegir')}</option>
              {MOTIVOS_REPORTE.map((m) => (
                <option key={m} value={m}>
                  {t(`reportes.motivos.${m}`)}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t('reportes.descripcion')}
            <textarea
              value={descripcion}
              maxLength={DESCRIPCION_REPORTE_MAXIMA}
              onChange={(e) => setDescripcion(e.target.value)}
            />
          </label>
          {error && (
            <p className="calificaciones__error" role="alert">
              {error}
            </p>
          )}
          <div className="reportar__acciones">
            <button type="submit" className="boton-principal" disabled={estado === 'enviando'}>
              {t('reportes.enviar')}
            </button>
            <button type="button" className="boton-secundario" onClick={() => setAbierto(false)}>
              {t('reportes.cancelar')}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
