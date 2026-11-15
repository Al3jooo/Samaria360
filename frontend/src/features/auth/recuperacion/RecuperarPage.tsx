import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiError, ERROR_RED } from '../../../shared/api/httpClient'
import { IconoSobre } from '../../../shared/components/Iconos'
import { solicitarRecuperacion } from '../authApi'
import '../LoginPage.css'

type Estado = 'formulario' | 'enviado'

/**
 * WF-27 «Recuperar contraseña» (RF34, DEC-285; propuesto por arquitectura): pide el correo y muestra siempre el mismo
 * mensaje, exista o no la cuenta (ADR-006). El correo sale de ms-auth por SMTP directo, con un enlace que vence a los
 * 30 minutos.
 */
export function RecuperarPage() {
  const { t, i18n } = useTranslation()
  const [correo, setCorreo] = useState('')
  const [estado, setEstado] = useState<Estado>('formulario')
  const [errorCampo, setErrorCampo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const enviar = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    if (!correo.trim()) {
      setErrorCampo(t('login.campoObligatorio'))
      document.getElementById('correo-recuperacion')?.focus()
      return
    }
    setErrorCampo(null)
    setEnviando(true)
    try {
      await solicitarRecuperacion(correo.trim(), i18n.language)
      setEstado('enviado')
    } catch (excepcion) {
      if (excepcion instanceof ApiError && excepcion.codigo === 'DATOS_INVALIDOS') {
        setErrorCampo(t('recuperacion.correoInvalido'))
      } else if (excepcion instanceof ApiError && excepcion.status === 429) {
        setError(t('recuperacion.demasiadas'))
      } else if (excepcion instanceof ApiError && excepcion.codigo === ERROR_RED) {
        setError(t('recuperacion.sinConexion'))
      } else {
        setError(t('recuperacion.errorGeneral'))
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="login">
      <h1 className="solo-lector">{t('recuperacion.titulo')}</h1>
      <div className="login__cuerpo">
        <p className="login__introduccion">{t('recuperacion.introduccion')}</p>
        <section className="tarjeta" aria-labelledby="titulo-recuperacion">
          <h2 id="titulo-recuperacion" className="tarjeta__titulo">
            {t('recuperacion.titulo')}
          </h2>
          {estado === 'enviado' ? (
            <div className="tarjeta__cuerpo">
              <p className="login__aviso-registro" role="status">
                {t('recuperacion.enviado')}
              </p>
              <Link to="/login" className="boton-principal">
                {t('recuperacion.volver')}
              </Link>
            </div>
          ) : (
            <form className="tarjeta__cuerpo" noValidate onSubmit={enviar}>
              <div className="campo">
                <label htmlFor="correo-recuperacion">{t('login.correo')}</label>
                <div className="campo__control">
                  <IconoSobre />
                  <input
                    id="correo-recuperacion"
                    name="correo"
                    type="email"
                    inputMode="email"
                    autoComplete="username"
                    placeholder={t('login.correoPlaceholder')}
                    value={correo}
                    onChange={(e) => setCorreo(e.target.value)}
                    aria-invalid={errorCampo ? true : undefined}
                    aria-describedby={errorCampo ? 'correo-recuperacion-error' : undefined}
                  />
                </div>
                {errorCampo && (
                  <p id="correo-recuperacion-error" className="campo__error">
                    {errorCampo}
                  </p>
                )}
              </div>
              <div className="login__error" role="alert" aria-live="assertive" aria-atomic="true">
                {error && <p>{error}</p>}
              </div>
              <button type="submit" className="boton-principal" disabled={enviando} aria-busy={enviando}>
                <span>{enviando ? t('recuperacion.enviando') : t('recuperacion.boton')}</span>
              </button>
              <Link to="/login" className="enlace-olvido">
                {t('recuperacion.volver')}
              </Link>
            </form>
          )}
        </section>
      </div>
    </div>
  )
}
