import { useTranslation } from 'react-i18next'
import { Link, NavLink } from 'react-router-dom'
import { LogoSamaria } from './LogoSamaria'
import { MenuUsuario, type UsuarioEncabezado } from './MenuUsuario'
import { AccionNoDisponible } from './BotonNoDisponible'
import { SelectorIdioma } from './SelectorIdioma'

function LogoInicio() {
  const { t } = useTranslation()
  return (
    <Link to="/" className="logo" aria-label={t('encabezado.inicio')}>
      <LogoSamaria />
      <span aria-hidden="true">Samaria360</span>
    </Link>
  )
}

/**
 * Encabezado de las pantallas de la plataforma (WF-04 a WF-10, v2): logo; navegación Búsqueda, Servicios y Asistente
 * IA; ES / EN y, con sesión, el avatar. Búsqueda lleva a la exploración (WF-05); Servicios y Asistente IA llegan en
 * iteraciones siguientes.
 * Recibe la sesión por props para que shared/ no dependa de features/.
 * «Inicio de sesión» solo se muestra sin sesión (`mostrarInicioSesion`). Mientras se valida una sesión guardada
 * tampoco se muestra, para que no aparezca y desaparezca al cargar.
 */
export function Encabezado({
  usuario,
  mostrarInicioSesion,
  onCerrarSesion,
}: {
  usuario: UsuarioEncabezado | null
  mostrarInicioSesion: boolean
  onCerrarSesion: () => void
}) {
  const { t } = useTranslation()

  return (
    <header className="encabezado">
      <div className="encabezado__contenido">
        <LogoInicio />

        <nav className="navegacion" aria-label={t('encabezado.navegacion')}>
          <ul>
            {mostrarInicioSesion && (
              <li>
                <NavLink to="/login">{t('encabezado.inicioSesion')}</NavLink>
              </li>
            )}
            <li>
              <NavLink to="/explorar">{t('encabezado.busqueda')}</NavLink>
            </li>
            <li>
              <AccionNoDisponible>{t('encabezado.servicios')}</AccionNoDisponible>
            </li>
            <li>
              <AccionNoDisponible>{t('encabezado.asistente')}</AccionNoDisponible>
            </li>
          </ul>
        </nav>

        <div className="encabezado__acciones">
          <SelectorIdioma />
          {usuario && <MenuUsuario usuario={usuario} onCerrarSesion={onCerrarSesion} />}
        </div>
      </div>
    </header>
  )
}

/** Encabezado reducido de las pantallas de acceso (WF-01 a WF-03, v2): solo el logo y ES / EN. */
export function EncabezadoAcceso() {
  return (
    <header className="encabezado">
      <div className="encabezado__contenido encabezado__contenido--acceso">
        <LogoInicio />
        <div className="encabezado__acciones">
          <SelectorIdioma />
        </div>
      </div>
    </header>
  )
}
