import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, ERROR_RED } from '../../../shared/api/httpClient'
import { IconoCandado } from '../../../shared/components/Iconos'
import { restablecerContrasena } from '../authApi'
import '../LoginPage.css'

/** Mínimo de la contraseña, el mismo del registro (ContrasenaNueva de ms-auth). */
const LONGITUD_MINIMA = 8

type Estado = 'formulario' | 'listo' | 'enlaceInvalido'

/** El token llega en el fragmento del enlace (#token=…): el navegador no lo envía al servidor ni al Referer. */
function tokenDelFragmento(hash: string): string | null {
  const valor = new URLSearchParams(hash.replace(/^#/, '')).get('token')
  return valor && /^[A-Za-z0-9_-]{43}$/.test(valor) ? valor : null
}

/**
 * WF-28 «Restablecer contraseña» (RF34, DEC-285; propuesto por arquitectura): con el token del correo, elige una
 * contraseña nueva. Al leerlo, el fragmento se quita de la URL para que no quede en el historial. Un enlace usado,
 * vencido o mal copiado lleva a pedir otro.
 */
export function RestablecerPage() {
  const { t } = useTranslation()
  const ubicacion = useLocation()
  const navegar = useNavigate()
  const [token] = useState(() => tokenDelFragmento(ubicacion.hash))
  const [contrasena, setContrasena] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [errorCampo, setErrorCampo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [estado, setEstado] = useState<Estado>(() => (token ? 'formulario' : 'enlaceInvalido'))
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (ubicacion.hash) navegar(ubicacion.pathname, { replace: true })
  }, [ubicacion.hash, ubicacion.pathname, navegar])

  const enviar = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    if ([...contrasena].length < LONGITUD_MINIMA) {
      setErrorCampo(t('recuperacion.contrasenaCorta'))
      return
    }
    if (contrasena !== confirmacion) {
      setErrorCampo(t('recuperacion.noCoinciden'))
      return
    }
    setErrorCampo(null)
    setEnviando(true)
    try {
      await restablecerContrasena(token ?? '', contrasena)
      setEstado('listo')
    } catch (excepcion) {
      if (excepcion instanceof ApiError && excepcion.codigo === 'TOKEN_INVALIDO') {
        setEstado('enlaceInvalido')
      } else if (excepcion instanceof ApiError && excepcion.codigo === 'DATOS_INVALIDOS') {
        setErrorCampo(t('recuperacion.contrasenaInvalida'))
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
      <h1 className="solo-lector">{t('recuperacion.tituloRestablecer')}</h1>
      <div className="login__cuerpo">
        <section className="tarjeta" aria-labelledby="titulo-restablecer">
          <h2 id="titulo-restablecer" className="tarjeta__titulo">
            {t('recuperacion.tituloRestablecer')}
          </h2>
          {estado === 'listo' && (
            <div className="tarjeta__cuerpo">
              <p className="login__aviso-registro" role="status">
                {t('recuperacion.listo')}
              </p>
              <Link to="/login" className="boton-principal">
                {t('recuperacion.iniciarSesion')}
              </Link>
            </div>
          )}
          {estado === 'enlaceInvalido' && (
            <div className="tarjeta__cuerpo">
              <p className="login__error" role="alert">
                {t('recuperacion.enlaceInvalido')}
              </p>
              <Link to="/recuperar" className="boton-principal">
                {t('recuperacion.pedirOtro')}
              </Link>
            </div>
          )}
          {estado === 'formulario' && (
            <form className="tarjeta__cuerpo" noValidate onSubmit={enviar}>
              <div className="campo">
                <label htmlFor="contrasena-nueva">{t('recuperacion.contrasenaNueva')}</label>
                <div className="campo__control">
                  <IconoCandado />
                  <input
                    id="contrasena-nueva"
                    name="contrasena"
                    type="password"
                    autoComplete="new-password"
                    placeholder={t('login.contrasenaPlaceholder')}
                    value={contrasena}
                    onChange={(e) => setContrasena(e.target.value)}
                    aria-invalid={errorCampo ? true : undefined}
                    aria-describedby={errorCampo ? 'restablecer-error' : undefined}
                  />
                </div>
              </div>
              <div className="campo">
                <label htmlFor="contrasena-confirmacion">{t('recuperacion.confirmar')}</label>
                <div className="campo__control">
                  <IconoCandado />
                  <input
                    id="contrasena-confirmacion"
                    name="confirmacion"
                    type="password"
                    autoComplete="new-password"
                    value={confirmacion}
                    onChange={(e) => setConfirmacion(e.target.value)}
                    aria-invalid={errorCampo ? true : undefined}
                    aria-describedby={errorCampo ? 'restablecer-error' : undefined}
                  />
                </div>
                {errorCampo && (
                  <p id="restablecer-error" className="campo__error">
                    {errorCampo}
                  </p>
                )}
              </div>
              <div className="login__error" role="alert" aria-live="assertive" aria-atomic="true">
                {error && <p>{error}</p>}
              </div>
              <button type="submit" className="boton-principal" disabled={enviando} aria-busy={enviando}>
                <span>{enviando ? t('recuperacion.guardando') : t('recuperacion.guardar')}</span>
              </button>
            </form>
          )}
        </section>
      </div>
    </div>
  )
}
