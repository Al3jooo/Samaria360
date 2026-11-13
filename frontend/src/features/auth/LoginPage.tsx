import { useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, Navigate, useLocation } from 'react-router-dom'
import {
  IconoCandado,
  IconoEntrar,
  IconoOjo,
  IconoOjoTachado,
  IconoRayo,
  IconoSobre,
} from '../../shared/components/Iconos'
import { BotonProximamente } from '../../shared/components/Proximamente'
import { RUTA_POLITICA_PRIVACIDAD, RUTA_TERMINOS } from '../../shared/rutasLegales'
import { destinoTrasLogin } from './destinoTrasLogin'
import { esEstadoTrasRegistro } from './estadoAcceso'
import { aErrorInicioSesion, textoError, type ErrorInicioSesion } from './mensajesError'
import { PestanasAcceso } from './PestanasAcceso'
import { TEXTO_AVISO } from './SesionContext'
import { useSesion } from './useSesion'
import './LoginPage.css'

type Campo = 'correo' | 'contrasena'

/**
 * WF-01: Inicio de sesión (RF15). «Registrarse» lleva a /registro (WF-02, WF-03); recuperar la contraseña (RF34) queda
 * visible pero deshabilitado. Después de registrarse llega con un mensaje y el correo escrito (por el estado del router).
 */
export function LoginPage() {
  const { t, i18n } = useTranslation()
  const { sesion, aviso, iniciarSesion } = useSesion()
  const ubicacion = useLocation()
  const trasRegistro = esEstadoTrasRegistro(ubicacion.state) ? ubicacion.state : null

  // El estado del formulario vive aquí y cambiar de idioma no desmonta el componente (RNF08).
  const [correo, setCorreo] = useState(() => trasRegistro?.correo ?? '')
  const [contrasena, setContrasena] = useState('')
  // Mejora de diseño (2026-11-13): ver la contraseña escrita. Se vuelve a ocultar al enviar el formulario.
  const [verContrasena, setVerContrasena] = useState(false)
  const [camposVacios, setCamposVacios] = useState<Set<Campo>>(new Set())
  const [error, setError] = useState<ErrorInicioSesion | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (sesion.estado === 'cargando') {
    return (
      <p className="estado-carga" role="status" aria-busy="true">
        {t('comun.cargando')}
      </p>
    )
  }
  // Con la sesión iniciada (por URL, con «Atrás» o justo después de iniciar sesión) nunca se muestra el formulario.
  if (sesion.estado === 'autenticado') {
    return <Navigate to={destinoTrasLogin((ubicacion.state as { desde?: unknown } | null)?.desde)} replace />
  }

  const enviar = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const vacios = new Set<Campo>()
    if (!correo.trim()) vacios.add('correo')
    if (!contrasena) vacios.add('contrasena')
    setCamposVacios(vacios)
    if (vacios.size > 0) {
      setError(null)
      document.getElementById(vacios.has('correo') ? 'correo' : 'contrasena')?.focus()
      return
    }
    setError(null)
    setVerContrasena(false)
    setEnviando(true)
    try {
      await iniciarSesion(correo.trim(), contrasena)
    } catch (excepcion) {
      setError(aErrorInicioSesion(excepcion))
      setEnviando(false)
    }
  }

  const errorDeCampo = (campo: Campo) =>
    camposVacios.has(campo) ? (
      <p id={`${campo}-error`} className="campo__error">
        {t('login.campoObligatorio')}
      </p>
    ) : null

  return (
    // Disposición de WF-01 (v2), sin imagen: subtítulo, pestañas e introducción alineados a la izquierda; debajo, la
    // tarjeta del formulario, centrada. El título «Samaria360» ya no se ve (el logo está en el encabezado reducido): el
    // h1 queda solo para lectores de pantalla. Las pestañas son enlaces a /login y /registro.
    <div className="login">
      <h1 className="solo-lector">{t('login.titulo')}</h1>
      <PestanasAcceso activa="login" />

      <div className="login__cuerpo">
        <p className="login__introduccion">{t('login.introduccion')}</p>
        {/* Por qué se cerró la sesión, al llegar desde una ruta privada (DEC-50). Iniciar sesión lo descarta. */}
        {aviso && !trasRegistro && (
          <p className="login__aviso-registro" role="status">
            {t(TEXTO_AVISO[aviso].aviso)}
          </p>
        )}
        {trasRegistro && (
          <p className="login__aviso-registro" role="status">
            {t(`login.trasRegistro.${trasRegistro.registro}`)}
          </p>
        )}

        <section className="tarjeta" aria-labelledby="titulo-tarjeta">
          <h2 id="titulo-tarjeta" className="tarjeta__titulo">
            {t('login.tarjeta')}
          </h2>

          <form className="tarjeta__cuerpo" noValidate onSubmit={enviar}>
            <div className="campo">
              <label htmlFor="correo">{t('login.correo')}</label>
              <div className="campo__control">
                <IconoSobre />
                <input
                  id="correo"
                  name="correo"
                  type="email"
                  inputMode="email"
                  autoComplete="username"
                  placeholder={t('login.correoPlaceholder')}
                  value={correo}
                  onChange={(evento) => setCorreo(evento.target.value)}
                  aria-invalid={camposVacios.has('correo') || undefined}
                  aria-describedby={camposVacios.has('correo') ? 'correo-error' : undefined}
                />
              </div>
              {errorDeCampo('correo')}
            </div>

            <div className="campo">
              <label htmlFor="contrasena">{t('login.contrasena')}</label>
              <div className="campo__control">
                <IconoCandado />
                <input
                  id="contrasena"
                  name="contrasena"
                  type={verContrasena ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder={t('login.contrasenaPlaceholder')}
                  value={contrasena}
                  onChange={(evento) => setContrasena(evento.target.value)}
                  aria-invalid={camposVacios.has('contrasena') || undefined}
                  aria-describedby={camposVacios.has('contrasena') ? 'contrasena-error' : undefined}
                />
                {/* Botón de alternancia: nombre fijo y aria-pressed, para que el lector de pantalla diga si está activo. */}
                <button
                  type="button"
                  className="campo__ver"
                  aria-label={t('login.mostrarContrasena')}
                  aria-pressed={verContrasena}
                  aria-controls="contrasena"
                  title={t(verContrasena ? 'login.ocultarContrasena' : 'login.mostrarContrasena')}
                  onClick={() => setVerContrasena((visible) => !visible)}
                >
                  {verContrasena ? <IconoOjoTachado /> : <IconoOjo />}
                </button>
              </div>
              {errorDeCampo('contrasena')}
            </div>

            <BotonProximamente className="enlace-olvido">{t('login.olvido')}</BotonProximamente>

            <p className="aviso-intentos">
              <IconoRayo />
              <span>{t('login.avisoIntentos')}</span>
            </p>

            {/* Siempre presente para que el lector de pantalla anuncie cada error nuevo. */}
            <div className="login__error" role="alert" aria-live="assertive" aria-atomic="true">
              {error && <p>{textoError(error, t, i18n.language)}</p>}
            </div>

            <button type="submit" className="boton-principal" disabled={enviando} aria-busy={enviando}>
              <IconoEntrar />
              <span>{enviando ? t('login.enviando') : t('login.boton')}</span>
            </button>

            {/* RF46 (CU «Consultar términos», FA1): el texto legal lleva a WF-15 y a su sección 4 (DEC-76). */}
            <p className="login__legal">
              <Trans
                i18nKey="login.legal"
                components={{ terminos: <Link to={RUTA_TERMINOS} />, privacidad: <Link to={RUTA_POLITICA_PRIVACIDAD} /> }}
              />
            </p>
          </form>
        </section>
      </div>
    </div>
  )
}
