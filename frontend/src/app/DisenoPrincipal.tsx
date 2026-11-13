import { useTranslation } from 'react-i18next'
import { Outlet, useNavigate } from 'react-router-dom'
import { AvisoSesion } from '../features/auth/AvisoSesion'
import { useSesion } from '../features/auth/useSesion'
import { BannerAvisos } from '../features/contenido/BannerAvisos'
import { Encabezado } from '../shared/components/Encabezado'
import { PiePagina } from '../shared/components/PiePagina'

/**
 * Elementos comunes de las pantallas de la plataforma (WF-04 a WF-15, docs/wireframes/README.md v2). Sin botón
 * flotante del asistente (decisión del equipo): el acceso a RF04 es «Asistente IA» en la navegación. El banner de
 * avisos (RF40) va debajo del encabezado en todas estas pantallas (DEC-67, DEC-72); las de acceso no lo llevan.
 */
export function DisenoPrincipal() {
  const { t } = useTranslation()
  const { sesion, cerrarSesion } = useSesion()
  const navegar = useNavigate()

  return (
    <div className="pagina">
      <a className="saltar-contenido" href="#contenido">
        {t('comun.saltarAlContenido')}
      </a>
      <Encabezado
        usuario={sesion.estado === 'autenticado' ? sesion.usuario : null}
        // Solo sin sesión: con sesión o mientras se valida la guardada, el enlace no se muestra.
        mostrarInicioSesion={sesion.estado === 'anonimo'}
        onCerrarSesion={() => {
          cerrarSesion()
          navegar('/login', { replace: true })
        }}
      />
      <BannerAvisos />
      <AvisoSesion />
      <main id="contenido" className="contenido" tabIndex={-1}>
        <Outlet />
      </main>
      <PiePagina esGestor={sesion.estado === 'autenticado' && sesion.usuario.rol === 'Gestor'} />
    </div>
  )
}
