import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSesion } from '../features/auth/useSesion'
import { SinPermiso } from '../shared/components/SinPermiso'

/**
 * Sin sesión, cualquier ruta privada lleva al login y recuerda a dónde se quería ir. Con `rol` (DEC-51), una sesión de
 * otro rol ve «Sin permiso» y la página no se monta: no se descarga ni llama a la API (RNF06).
 */
/** `rol` puede ser una lista: RF14 es del Administrador y del Gestor (DEC-308). */
export function RutaProtegida({ rol }: { rol?: string | string[] }) {
  const { t } = useTranslation()
  const { sesion } = useSesion()
  const ubicacion = useLocation()

  if (sesion.estado === 'cargando') {
    return (
      <p className="estado-carga" role="status" aria-busy="true">
        {t('comun.cargando')}
      </p>
    )
  }
  if (sesion.estado === 'anonimo') {
    // Ruta completa (con ?consulta y #ancla) para volver exactamente a donde se quería ir.
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname + ubicacion.search + ubicacion.hash }} />
  }
  if (rol && !(Array.isArray(rol) ? rol : [rol]).includes(sesion.usuario.rol)) {
    return <SinPermiso />
  }
  return <Outlet />
}
