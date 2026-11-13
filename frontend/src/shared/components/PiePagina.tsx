import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { RUTA_POLITICA_PRIVACIDAD, RUTA_PREGUNTAS_FRECUENTES, RUTA_TERMINOS } from '../rutasLegales'
import { Ola } from './Ilustraciones'
import { LogoSamaria } from './LogoSamaria'

/** Un enlace con `destino` lleva a una pantalla que ya existe; sin él, es un pendiente (docs/trazabilidad.md). */
interface EnlacePie {
  texto: string
  destino?: string
  /** Solo con la sesión de un Gestor (DEC-270). */
  soloGestor?: boolean
}

/** Las categorías van a la exploración (WF-05) con el filtro aplicado; los nombres son los de la API. */
const COLUMNAS: { titulo: string; enlaces: EnlacePie[] }[] = [
  {
    titulo: 'pie.explorar',
    enlaces: [
      { texto: 'pie.alojamiento', destino: '/explorar?categoria=Alojamiento' },
      { texto: 'pie.gastronomia', destino: '/explorar?categoria=Gastronomia' },
      { texto: 'pie.tours', destino: '/explorar?categoria=Tour' },
      { texto: 'pie.alquilerEquipos', destino: '/explorar?categoria=AlquilerEquipo' },
    ],
  },
  {
    titulo: 'pie.municipio',
    // RF25 (DEC-246): la agenda de eventos pública (WF-26); «Noticias» sigue pendiente.
    enlaces: [{ texto: 'pie.noticias' }, { texto: 'pie.agendaEventos', destino: '/agenda' }],
  },
  {
    titulo: 'pie.prestadores',
    // «Registrar servicio» lleva al registro del Prestador (WF-08): sin sesión, al login y de vuelta; con otro rol,
    // «Sin permiso». «Panel del gestor» (WF-24, DEC-233) solo se muestra con la sesión de un Gestor (DEC-270).
    enlaces: [
      { texto: 'pie.registrarServicio', destino: '/prestador/servicios/nuevo' },
      { texto: 'pie.panelGestor', destino: '/gestor/atractivos', soloGestor: true },
    ],
  },
  {
    titulo: 'pie.legal',
    // RF46 y RF26 (DEC-76): términos (WF-15), su sección 4 y las preguntas frecuentes (WF-13).
    enlaces: [
      { texto: 'pie.terminos', destino: RUTA_TERMINOS },
      { texto: 'pie.privacidad', destino: RUTA_POLITICA_PRIVACIDAD },
      { texto: 'pie.preguntasFrecuentes', destino: RUTA_PREGUNTAS_FRECUENTES },
    ],
  },
]

/** Pie reducido: «Políticas de privacidad» usa la misma ruta que la casilla del registro; «Ayuda / Soporte» sigue pendiente. */
const ENLACES_ACCESO: EnlacePie[] = [
  { texto: 'pie.terminos', destino: RUTA_TERMINOS },
  { texto: 'pie.privacidad', destino: RUTA_POLITICA_PRIVACIDAD },
  { texto: 'pie.ayuda' },
]

/** Enlace sin destino: el estado único de DEC-22 (DEC-270), con «No disponible» solo para lectores de pantalla. */
function Pendiente({ texto }: { texto: string }) {
  const { t } = useTranslation()
  return (
    <span className="enlace-deshabilitado">
      {t(texto)}
      <span className="solo-lector"> ({t('comun.noDisponible')})</span>
    </span>
  )
}

/** Las fotos del catálogo no tienen créditos verificados (CLAUDE.md, «Imágenes del catálogo»). */
function AvisoAcademico() {
  const { t } = useTranslation()
  return <p className="pie__aviso">{t('pie.avisoAcademico')}</p>
}

/** Pie de las pantallas de la plataforma (docs/wireframes/README.md, v2): logo y cuatro columnas. */
export function PiePagina({ esGestor = false }: { esGestor?: boolean }) {
  const { t } = useTranslation()
  return (
    <footer className="pie">
      <Ola className="pie__ola" />
      <div className="pie__contenido">
        <p className="logo logo--pie">
          {/* Sobre mar-900 la isla marrón y las olas se pierden: el ícono va en un círculo claro. */}
          <span className="logo__insignia">
            <LogoSamaria tamano={36} />
          </span>
          Samaria360
        </p>
        {COLUMNAS.map((columna) => (
          <section key={columna.titulo} className="pie__columna" aria-labelledby={`pie-${columna.titulo}`}>
            <h2 id={`pie-${columna.titulo}`}>{t(columna.titulo)}</h2>
            <ul>
              {columna.enlaces
                .filter((enlace) => !enlace.soloGestor || esGestor)
                .map((enlace) => (
                  <li key={enlace.texto}>
                    {enlace.destino ? <Link to={enlace.destino}>{t(enlace.texto)}</Link> : <Pendiente texto={enlace.texto} />}
                  </li>
                ))}
            </ul>
          </section>
        ))}
      </div>
      <div className="pie__derechos">
        <p>{t('pie.derechos')}</p>
        <AvisoAcademico />
      </div>
    </footer>
  )
}

/** Pie reducido de las pantallas de acceso (WF-01 a WF-03, v2): derechos y tres enlaces (Ayuda, pendiente). */
export function PieAcceso() {
  const { t } = useTranslation()
  return (
    <footer className="pie pie--acceso">
      <div className="pie__fila">
        <p>{t('pie.derechosAcceso')}</p>
        <ul aria-label={t('pie.enlacesAcceso')}>
          {ENLACES_ACCESO.map((enlace) => (
            <li key={enlace.texto}>
              {enlace.destino ? <Link to={enlace.destino}>{t(enlace.texto)}</Link> : <Pendiente texto={enlace.texto} />}
            </li>
          ))}
        </ul>
      </div>
      <div className="pie__derechos">
        <AvisoAcademico />
      </div>
    </footer>
  )
}
