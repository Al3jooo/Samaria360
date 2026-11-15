import { useEffect, useId, useRef, useState, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { iniciales } from './iniciales'

export interface UsuarioEncabezado {
  nombreVisible: string
  correo: string
  /**
   * «Mi perfil» (WF-16), «Mis favoritos» (WF-17) y «Mis reservas» (WF-11) solo aparecen para el Turista; las pantallas
   * de gestión (WF-12 a WF-14), para el Administrador; «Mis servicios» (WF-18), el registro (WF-08), el historial de
   * reservas (WF-23) y el perfil comercial (WF-22), para el Prestador.
   */
  rol?: string
}

/** Las pantallas propias de cada rol en el menú, en orden (ruta y clave i18n). */
const OPCIONES_POR_ROL: Record<string, { ruta: string; texto: string }[]> = {
  // DEC-92: perfil (WF-16), favoritos dentro del perfil (WF-17, RF54) y reservas (WF-11).
  Turista: [
    { ruta: '/perfil', texto: 'encabezado.miPerfil' },
    { ruta: '/perfil/favoritos', texto: 'encabezado.misFavoritos' },
    // DEC-298: los itinerarios guardados desde el asistente (WF-31, RF35).
    { ruta: '/perfil/itinerarios', texto: 'encabezado.misItinerarios' },
    { ruta: '/mis-reservas', texto: 'encabezado.misReservas' },
  ],
  // DEC-161: «Mis servicios» (WF-18) y el registro de un servicio (WF-08); DEC-197: el historial de reservas (WF-23,
  // RF12) y el perfil comercial (WF-22, RF43).
  Prestador: [
    { ruta: '/prestador/servicios', texto: 'encabezado.misServicios' },
    { ruta: '/prestador/servicios/nuevo', texto: 'encabezado.registrarServicio' },
    { ruta: '/prestador/reservas', texto: 'encabezado.historialReservas' },
    { ruta: '/prestador/perfil', texto: 'encabezado.perfilComercial' },
    // DEC-303: las calificaciones recibidas (WF-33, RF56).
    { ruta: '/prestador/calificaciones', texto: 'encabezado.calificacionesRecibidas' },
    // DEC-308: el panel de consultas y reservas (WF-36, RF31).
    { ruta: '/prestador/panel', texto: 'encabezado.miPanel' },
    // DEC-311 y DEC-312: el historial de modificaciones (WF-39, RF41) y los asistentes (WF-40, RF42).
    { ruta: '/prestador/historial-modificaciones', texto: 'encabezado.historialModificaciones' },
    { ruta: '/prestador/asistentes', texto: 'encabezado.asistentes' },
  ],
  // DEC-70: cuentas (WF-12), preguntas frecuentes (WF-13) y avisos (WF-14); DEC-174: servicios por aprobar (WF-19).
  Administrador: [
    { ruta: '/admin/cuentas', texto: 'encabezado.administrarCuentas' },
    { ruta: '/admin/servicios', texto: 'encabezado.aprobarServicios' },
    { ruta: '/admin/preguntas-frecuentes', texto: 'encabezado.administrarPreguntas' },
    { ruta: '/admin/avisos', texto: 'encabezado.administrarAvisos' },
    // DEC-303: el panel de moderación (WF-34, RF29 y RF57).
    { ruta: '/admin/moderacion', texto: 'encabezado.moderacion' },
    // DEC-297: la base de conocimientos del asistente (WF-32, RF05).
    { ruta: '/admin/conocimiento', texto: 'encabezado.conocimiento' },
    // DEC-294: el log de auditoría (WF-30, RF13).
    { ruta: '/admin/auditoria', texto: 'encabezado.auditoria' },
    // DEC-308: los tableros (WF-35, RF14).
    { ruta: '/tableros', texto: 'encabezado.tableros' },
  ],
  // DEC-233 (cierra N4 para el Gestor): el panel «Atractivos» (WF-24) y el registro de un atractivo (WF-09); DEC-251:
  // el panel de eventos y el registro de un evento (WF-25, RF25).
  Gestor: [
    { ruta: '/gestor/atractivos', texto: 'encabezado.atractivos' },
    { ruta: '/gestor/atractivos/nuevo', texto: 'encabezado.registrarAtractivo' },
    { ruta: '/gestor/eventos', texto: 'encabezado.eventos' },
    { ruta: '/gestor/eventos/nuevo', texto: 'encabezado.registrarEvento' },
    // DEC-308: los tableros (WF-35, RF14) y el reporte de afluencia (WF-37, RF64).
    { ruta: '/tableros', texto: 'encabezado.tableros' },
    { ruta: '/gestor/afluencia', texto: 'encabezado.reporteAfluencia' },
  ],
}

/**
 * Avatar con iniciales y menú: las pantallas del rol (perfil, favoritos y reservas del turista, WF-16, WF-17 y WF-11;
 * la gestión del Administrador, WF-12 a WF-14; los atractivos y los eventos del Gestor, WF-24, WF-09 y WF-25) y «Cerrar
 * sesión» (RF55). Accesible con teclado: Enter abre y lleva el foco a la primera opción,
 * Escape cierra y devuelve el foco al avatar.
 */
export function MenuUsuario({ usuario, onCerrarSesion }: { usuario: UsuarioEncabezado; onCerrarSesion: () => void }) {
  const { t } = useTranslation()
  const [abierto, setAbierto] = useState(false)
  const idMenu = useId()
  const contenedor = useRef<HTMLDivElement>(null)
  const boton = useRef<HTMLButtonElement>(null)
  const primeraOpcion = useRef<HTMLElement>(null)
  const opcionesRol = (usuario.rol && OPCIONES_POR_ROL[usuario.rol]) || []

  useEffect(() => {
    if (!abierto) return
    primeraOpcion.current?.focus()
    const alPulsarFuera = (evento: MouseEvent) => {
      if (!contenedor.current?.contains(evento.target as Node)) setAbierto(false)
    }
    document.addEventListener('mousedown', alPulsarFuera)
    return () => document.removeEventListener('mousedown', alPulsarFuera)
  }, [abierto])

  const cerrarMenu = () => {
    setAbierto(false)
    boton.current?.focus()
  }

  return (
    <div
      className="menu-usuario"
      ref={contenedor}
      onKeyDown={(evento) => {
        if (evento.key === 'Escape' && abierto) {
          evento.stopPropagation()
          cerrarMenu()
        }
      }}
      onBlur={(evento) => {
        if (abierto && !contenedor.current?.contains(evento.relatedTarget as Node | null)) setAbierto(false)
      }}
    >
      <button
        ref={boton}
        type="button"
        className="avatar"
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-controls={abierto ? idMenu : undefined}
        aria-label={t('encabezado.menuUsuario', { nombre: usuario.nombreVisible })}
        onClick={() => setAbierto((valor) => !valor)}
      >
        <span aria-hidden="true">{iniciales(usuario.nombreVisible)}</span>
      </button>
      {abierto && (
        <div className="menu-usuario__panel">
          <p className="menu-usuario__nombre">{usuario.nombreVisible}</p>
          <p className="menu-usuario__correo">{usuario.correo}</p>
          <ul id={idMenu} role="menu" aria-label={usuario.nombreVisible}>
            {opcionesRol.map((opcion, indice) => (
              <li key={opcion.ruta} role="none">
                <Link
                  ref={indice === 0 ? (primeraOpcion as RefObject<HTMLAnchorElement>) : undefined}
                  to={opcion.ruta}
                  role="menuitem"
                  onClick={() => setAbierto(false)}
                >
                  {t(opcion.texto)}
                </Link>
              </li>
            ))}
            <li role="none">
              <button
                ref={opcionesRol.length > 0 ? undefined : (primeraOpcion as RefObject<HTMLButtonElement>)}
                type="button"
                role="menuitem"
                onClick={() => {
                  setAbierto(false)
                  onCerrarSesion()
                }}
              >
                {t('encabezado.cerrarSesion')}
              </button>
            </li>
          </ul>
        </div>
      )}
    </div>
  )
}
