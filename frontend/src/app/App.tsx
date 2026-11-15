import { Suspense, lazy, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../features/auth/LoginPage'
import { RegistroPage } from '../features/auth/registro/RegistroPage'
import { RecuperarPage } from '../features/auth/recuperacion/RecuperarPage'
import { RestablecerPage } from '../features/auth/recuperacion/RestablecerPage'
import { SesionProvider } from '../features/auth/SesionProvider'
import { EstadoCatalogo } from '../features/catalogo/componentes/EstadoCatalogo'
import { FavoritosProvider } from '../features/favoritos/FavoritosProvider'
import { InicioPage } from '../features/catalogo/InicioPage'
import { LimiteErrores } from '../shared/components/LimiteErrores'
import { navegador } from '../shared/navegador'
import { DisenoAcceso } from './DisenoAcceso'
import { DisenoPrincipal } from './DisenoPrincipal'
import { RutaProtegida } from './RutaProtegida'

/**
 * Exploración (WF-05) se carga de forma diferida: Leaflet y su CSS solo se descargan al entrar a /explorar. Mientras
 * llega, se ve el estado de carga del catálogo; si la descarga falla, un aviso con «Reintentar» (recarga la página,
 * porque React.lazy guarda el fallo). El encabezado, el pie y la sesión quedan fuera del límite de errores.
 */
const ExplorarPage = lazy(() => import('../features/catalogo/ExplorarPage').then((m) => ({ default: m.ExplorarPage })))

/** Detalle (WF-06), reserva (WF-10) y «Mis reservas» (WF-11): también diferidas, cada una en su propio archivo. */
const DetalleServicioPage = lazy(() =>
  import('../features/catalogo/detalle/DetalleServicioPage').then((m) => ({ default: m.DetalleServicioPage })),
)
/** Ficha pública de un atractivo (WF-06 para atractivos, DEC-224): diferida, como la del servicio. */
const DetalleAtractivoPage = lazy(() =>
  import('../features/catalogo/detalle/DetalleAtractivoPage').then((m) => ({ default: m.DetalleAtractivoPage })),
)
const ReservaPage = lazy(() => import('../features/reservas/ReservaPage').then((m) => ({ default: m.ReservaPage })))
const MisReservasPage = lazy(() =>
  import('../features/reservas/MisReservasPage').then((m) => ({ default: m.MisReservasPage })),
)
/** «Administrar cuentas» (WF-12): diferida y solo para el Administrador (DEC-51). */
const AdministrarCuentasPage = lazy(() =>
  import('../features/admin/AdministrarCuentasPage').then((m) => ({ default: m.AdministrarCuentasPage })),
)
/** «Contenido público» (DEC-70): preguntas frecuentes (WF-13) y términos (WF-15), públicas; su gestión, del Administrador. */
const PreguntasFrecuentesPage = lazy(() =>
  import('../features/contenido/PreguntasFrecuentesPage').then((m) => ({ default: m.PreguntasFrecuentesPage })),
)
const TerminosPage = lazy(() =>
  import('../features/contenido/terminos/TerminosPage').then((m) => ({ default: m.TerminosPage })),
)
const AdministrarPreguntasPage = lazy(() =>
  import('../features/admin/AdministrarPreguntasPage').then((m) => ({ default: m.AdministrarPreguntasPage })),
)
/**
 * Asistente virtual (iteración L, DEC-297, DEC-298): WF-07 y «Mis itinerarios» (WF-31), solo para el Turista, y la base
 * de conocimientos (WF-32), solo para el Administrador; diferidas.
 */
const AsistentePage = lazy(() => import('../features/asistente/AsistentePage').then((m) => ({ default: m.AsistentePage })))
const MisItinerariosPage = lazy(() =>
  import('../features/asistente/MisItinerariosPage').then((m) => ({ default: m.MisItinerariosPage })),
)
const ConocimientoAdminPage = lazy(() =>
  import('../features/asistente/ConocimientoAdminPage').then((m) => ({ default: m.ConocimientoAdminPage })),
)
/** Calificaciones y reportes (iteración H, DEC-303): WF-33 del Prestador (RF56) y WF-34 del Administrador (RF29, RF57). */
const CalificacionesRecibidasPage = lazy(() =>
  import('../features/calificaciones/CalificacionesRecibidasPage').then((m) => ({
    default: m.CalificacionesRecibidasPage,
  })),
)
const ModeracionPage = lazy(() =>
  import('../features/calificaciones/ModeracionPage').then((m) => ({ default: m.ModeracionPage })),
)
/** Tableros (iteración K, DEC-308): WF-35 (RF14), WF-36 (RF31) y WF-37 (RF64). */
const TableroGeneralPage = lazy(() =>
  import('../features/tableros/TableroGeneralPage').then((m) => ({ default: m.TableroGeneralPage })),
)
const PanelPrestadorPage = lazy(() =>
  import('../features/tableros/PanelPrestadorPage').then((m) => ({ default: m.PanelPrestadorPage })),
)
const ReporteAfluenciaPage = lazy(() =>
  import('../features/tableros/ReporteAfluenciaPage').then((m) => ({ default: m.ReporteAfluenciaPage })),
)
/** WF-30 «Auditoría» (RF13, DEC-294): diferida y solo para el Administrador. */
const AuditoriaPage = lazy(() => import('../features/admin/AuditoriaPage').then((m) => ({ default: m.AuditoriaPage })))
const AdministrarAvisosPage = lazy(() =>
  import('../features/admin/AdministrarAvisosPage').then((m) => ({ default: m.AdministrarAvisosPage })),
)
/** WF-19 «Aprobar servicios» (RF21, DEC-174): cola y revisión, diferidas y solo para el Administrador. */
const AprobarServiciosPage = lazy(() =>
  import('../features/admin/servicios/AprobarServiciosPage').then((m) => ({ default: m.AprobarServiciosPage })),
)
const RevisarServicioPage = lazy(() =>
  import('../features/admin/servicios/RevisarServicioPage').then((m) => ({ default: m.RevisarServicioPage })),
)
/** «Turista» (DEC-92): perfil (WF-16) y «Mis favoritos» (WF-17), diferidas y solo para el Turista. */
const PerfilPage = lazy(() => import('../features/perfil/PerfilPage').then((m) => ({ default: m.PerfilPage })))
const MisFavoritosPage = lazy(() =>
  import('../features/perfil/MisFavoritosPage').then((m) => ({ default: m.MisFavoritosPage })),
)

/**
 * «Registrar servicio comercial» (iteración C, B1, DEC-161): «Mis servicios» (WF-18), su detalle y el registro
 * (WF-08), diferidas y solo para el Prestador.
 */
const MisServiciosPage = lazy(() =>
  import('../features/prestador/MisServiciosPage').then((m) => ({ default: m.MisServiciosPage })),
)
const RegistrarServicioPage = lazy(() =>
  import('../features/prestador/RegistrarServicioPage').then((m) => ({ default: m.RegistrarServicioPage })),
)
const DetalleMiServicioPage = lazy(() =>
  import('../features/prestador/DetalleMiServicioPage').then((m) => ({ default: m.DetalleMiServicioPage })),
)
/**
 * «Oferta del prestador» (iteración D, sesión B, DEC-196): actualizar precio (WF-20, RF32), datos comerciales del
 * servicio (WF-21) y perfil comercial (WF-22, RF43), e historial de reservas (WF-23, RF12); diferidas y solo para el
 * Prestador.
 */
const ActualizarPrecioPage = lazy(() =>
  import('../features/prestador/ActualizarPrecioPage').then((m) => ({ default: m.ActualizarPrecioPage })),
)
const DatosComercialesPage = lazy(() =>
  import('../features/prestador/DatosComercialesPage').then((m) => ({ default: m.DatosComercialesPage })),
)
const PerfilComercialPage = lazy(() =>
  import('../features/prestador/PerfilComercialPage').then((m) => ({ default: m.PerfilComercialPage })),
)
const HistorialReservasPage = lazy(() =>
  import('../features/prestador/HistorialReservasPage').then((m) => ({ default: m.HistorialReservasPage })),
)
/** Iteración I (DEC-310 a DEC-313): oferta y disponibilidad (WF-38, RF19), historial de modificaciones (WF-39, RF41) y
 * asistentes (WF-40, RF42). La galería (RF48) va dentro de WF-18. */
const OfertaServicioPage = lazy(() =>
  import('../features/oferta/OfertaServicioPage').then((m) => ({ default: m.OfertaServicioPage })),
)
const HistorialModificacionesPage = lazy(() =>
  import('../features/oferta/HistorialModificacionesPage').then((m) => ({ default: m.HistorialModificacionesPage })),
)
const AsistentesPage = lazy(() =>
  import('../features/oferta/AsistentesPage').then((m) => ({ default: m.AsistentesPage })),
)
/**
 * «Atractivos y agenda» (iteración E, B1; DEC-220, DEC-233): el panel «Atractivos» (WF-24), el registro (WF-09) y la
 * edición, diferidas y solo para el Gestor.
 */
const AtractivosGestorPage = lazy(() =>
  import('../features/gestor/AtractivosGestorPage').then((m) => ({ default: m.AtractivosGestorPage })),
)
const RegistrarAtractivoPage = lazy(() =>
  import('../features/gestor/RegistrarAtractivoPage').then((m) => ({ default: m.RegistrarAtractivoPage })),
)
const EditarAtractivoPage = lazy(() =>
  import('../features/gestor/EditarAtractivoPage').then((m) => ({ default: m.EditarAtractivoPage })),
)
/**
 * «Atractivos y agenda» (iteración E, B2; DEC-245): la agenda de eventos pública (WF-26) y, solo para el Gestor, su
 * panel de eventos y el registro (WF-25); diferidas.
 */
const AgendaPage = lazy(() => import('../features/agenda/AgendaPage').then((m) => ({ default: m.AgendaPage })))
const EventosGestorPage = lazy(() =>
  import('../features/gestor/EventosGestorPage').then((m) => ({ default: m.EventosGestorPage })),
)
const RegistrarEventoPage = lazy(() =>
  import('../features/gestor/RegistrarEventoPage').then((m) => ({ default: m.RegistrarEventoPage })),
)

/** Una página diferida: estado de carga mientras llega y aviso con «Reintentar» si la descarga falla. */
function Diferida({ children }: { children: ReactNode }) {
  return (
    <LimiteErrores alFallar={<EstadoCatalogo tipo="seccionNoCargada" onReintentar={navegador.recargar} />}>
      <Suspense fallback={<EstadoCatalogo tipo="cargando" />}>{children}</Suspense>
    </LimiteErrores>
  )
}

/**
 * El router (BrowserRouter o MemoryRouter en pruebas) lo aporta quien monta la App.
 * Inicio (WF-04), exploración (WF-05), detalle (WF-06), reserva (WF-10), preguntas frecuentes (WF-13) y términos
 * (WF-15) son públicas, con el diseño de la plataforma (confirmar una reserva pide la sesión dentro de WF-10); «Mis
 * reservas» (WF-11) es privada; el perfil (WF-16) y «Mis favoritos» (WF-17), privadas y solo para el Turista; y
 * «Administrar cuentas» (WF-12), la gestión de preguntas frecuentes (WF-13) y la de avisos (WF-14), privadas y solo
 * para el Administrador; y «Mis servicios» (WF-18) y el registro de servicios (WF-08), privadas y solo para el
 * Prestador, como la edición del precio y de los datos comerciales (WF-20, WF-21), el perfil comercial (WF-22) y el
 * historial de reservas (WF-23); la ficha de un atractivo (WF-06 para atractivos) es pública, y el panel «Atractivos»
 * (WF-24), su registro y su edición (WF-09), privados y solo para el Gestor, como su panel de eventos y el registro
 * (WF-25); la agenda de eventos (WF-26) es pública. El inicio de sesión (WF-01) y el registro (WF-02, WF-03) usan el diseño de acceso. Los
 * favoritos del turista (DEC-93) se comparten entre pantallas, dentro de la sesión.
 */
export function App() {
  return (
    <SesionProvider>
      <FavoritosProvider>
        <Routes>
          <Route element={<DisenoPrincipal />}>
            <Route index element={<InicioPage />} />
            <Route
              path="/explorar"
              element={
                <Diferida>
                  <ExplorarPage />
                </Diferida>
              }
            />
            <Route
              path="/servicios/:id"
              element={
                <Diferida>
                  <DetalleServicioPage />
                </Diferida>
              }
            />
            <Route
              path="/servicios/:id/reservar"
              element={
                <Diferida>
                  <ReservaPage />
                </Diferida>
              }
            />
            <Route
              path="/atractivos/:id"
              element={
                <Diferida>
                  <DetalleAtractivoPage />
                </Diferida>
              }
            />
            <Route
              path="/preguntas-frecuentes"
              element={
                <Diferida>
                  <PreguntasFrecuentesPage />
                </Diferida>
              }
            />
            <Route
              path="/terminos"
              element={
                <Diferida>
                  <TerminosPage />
                </Diferida>
              }
            />
            <Route
              path="/agenda"
              element={
                <Diferida>
                  <AgendaPage />
                </Diferida>
              }
            />
            <Route element={<RutaProtegida />}>
              <Route
                path="/mis-reservas"
                element={
                  <Diferida>
                    <MisReservasPage />
                  </Diferida>
                }
              />
            </Route>
            <Route element={<RutaProtegida rol="Turista" />}>
              <Route
                path="/perfil"
                element={
                  <Diferida>
                    <PerfilPage />
                  </Diferida>
                }
              />
              <Route
                path="/perfil/favoritos"
                element={
                  <Diferida>
                    <MisFavoritosPage />
                  </Diferida>
                }
              />
              {/* WF-07 «Asistente virtual IA» y WF-31 «Mis itinerarios» (RF04, RF35, RF36, RF59; DEC-298). */}
              <Route
                path="/asistente"
                element={
                  <Diferida>
                    <AsistentePage />
                  </Diferida>
                }
              />
              <Route
                path="/perfil/itinerarios"
                element={
                  <Diferida>
                    <MisItinerariosPage />
                  </Diferida>
                }
              />
            </Route>
            {/* WF-35 «Tableros» (RF14): el Administrador y el Gestor (respuesta 4 del equipo, DEC-308). */}
            <Route element={<RutaProtegida rol={['Administrador', 'Gestor']} />}>
              <Route
                path="/tableros"
                element={
                  <Diferida>
                    <TableroGeneralPage />
                  </Diferida>
                }
              />
            </Route>
            <Route element={<RutaProtegida rol="Administrador" />}>
              {/* WF-34 «Moderación» (RF29, RF57; DEC-303). */}
              <Route
                path="/admin/moderacion"
                element={
                  <Diferida>
                    <ModeracionPage />
                  </Diferida>
                }
              />
              {/* WF-32 «Base de conocimientos» (RF05, DEC-297). */}
              <Route
                path="/admin/conocimiento"
                element={
                  <Diferida>
                    <ConocimientoAdminPage />
                  </Diferida>
                }
              />
              {/* WF-30 «Auditoría» (RF13, DEC-294). */}
              <Route
                path="/admin/auditoria"
                element={
                  <Diferida>
                    <AuditoriaPage />
                  </Diferida>
                }
              />
              <Route
                path="/admin/cuentas"
                element={
                  <Diferida>
                    <AdministrarCuentasPage />
                  </Diferida>
                }
              />
              <Route
                path="/admin/preguntas-frecuentes"
                element={
                  <Diferida>
                    <AdministrarPreguntasPage />
                  </Diferida>
                }
              />
              <Route
                path="/admin/avisos"
                element={
                  <Diferida>
                    <AdministrarAvisosPage />
                  </Diferida>
                }
              />
              <Route
                path="/admin/servicios"
                element={
                  <Diferida>
                    <AprobarServiciosPage />
                  </Diferida>
                }
              />
              <Route
                path="/admin/servicios/:id"
                element={
                  <Diferida>
                    <RevisarServicioPage />
                  </Diferida>
                }
              />
            </Route>
            <Route element={<RutaProtegida rol="Prestador" />}>
              <Route
                path="/prestador/servicios"
                element={
                  <Diferida>
                    <MisServiciosPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/servicios/nuevo"
                element={
                  <Diferida>
                    <RegistrarServicioPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/servicios/:id"
                element={
                  <Diferida>
                    <DetalleMiServicioPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/servicios/:id/precio"
                element={
                  <Diferida>
                    <ActualizarPrecioPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/servicios/:id/datos-comerciales"
                element={
                  <Diferida>
                    <DatosComercialesPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/perfil"
                element={
                  <Diferida>
                    <PerfilComercialPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/reservas"
                element={
                  <Diferida>
                    <HistorialReservasPage />
                  </Diferida>
                }
              />
              {/* WF-36 «Mi panel» (RF31, DEC-308). */}
              <Route
                path="/prestador/panel"
                element={
                  <Diferida>
                    <PanelPrestadorPage />
                  </Diferida>
                }
              />
              {/* WF-38, WF-39 y WF-40 (RF19, RF41, RF42; DEC-310 a DEC-312). */}
              <Route
                path="/prestador/servicios/:id/oferta"
                element={
                  <Diferida>
                    <OfertaServicioPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/historial-modificaciones"
                element={
                  <Diferida>
                    <HistorialModificacionesPage />
                  </Diferida>
                }
              />
              <Route
                path="/prestador/asistentes"
                element={
                  <Diferida>
                    <AsistentesPage />
                  </Diferida>
                }
              />
              {/* WF-33 «Calificaciones recibidas» (RF56, DEC-303). */}
              <Route
                path="/prestador/calificaciones"
                element={
                  <Diferida>
                    <CalificacionesRecibidasPage />
                  </Diferida>
                }
              />
            </Route>
            <Route element={<RutaProtegida rol="Gestor" />}>
              <Route
                path="/gestor/atractivos"
                element={
                  <Diferida>
                    <AtractivosGestorPage />
                  </Diferida>
                }
              />
              <Route
                path="/gestor/atractivos/nuevo"
                element={
                  <Diferida>
                    <RegistrarAtractivoPage />
                  </Diferida>
                }
              />
              <Route
                path="/gestor/atractivos/:id"
                element={
                  <Diferida>
                    <EditarAtractivoPage />
                  </Diferida>
                }
              />
              <Route
                path="/gestor/eventos"
                element={
                  <Diferida>
                    <EventosGestorPage />
                  </Diferida>
                }
              />
              <Route
                path="/gestor/eventos/nuevo"
                element={
                  <Diferida>
                    <RegistrarEventoPage />
                  </Diferida>
                }
              />
              {/* WF-37 «Reporte de afluencia» (RF64, DEC-308). */}
              <Route
                path="/gestor/afluencia"
                element={
                  <Diferida>
                    <ReporteAfluenciaPage />
                  </Diferida>
                }
              />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
          <Route element={<DisenoAcceso />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/registro" element={<RegistroPage />} />
            {/* RF34 (DEC-285): pedir el enlace (WF-27) y restablecer la contraseña con el token del correo (WF-28). */}
            <Route path="/recuperar" element={<RecuperarPage />} />
            <Route path="/restablecer" element={<RestablecerPage />} />
          </Route>
        </Routes>
      </FavoritosProvider>
    </SesionProvider>
  )
}
