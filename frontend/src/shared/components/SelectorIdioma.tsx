import { useTranslation } from 'react-i18next'
import { cambiarIdioma, type Idioma } from '../../i18n'

const OPCIONES: { idioma: Idioma; corto: string; nombre: 'encabezado.espanol' | 'encabezado.ingles' }[] = [
  { idioma: 'es', corto: 'ES', nombre: 'encabezado.espanol' },
  { idioma: 'en', corto: 'EN', nombre: 'encabezado.ingles' },
]

/** RF03 / RNF08: cambia el idioma sin recargar, así que se conserva lo escrito en pantalla. */
export function SelectorIdioma() {
  const { t, i18n } = useTranslation()
  return (
    <div className="selector-idioma" role="group" aria-label={t('encabezado.idioma')}>
      {OPCIONES.map(({ idioma, corto, nombre }) => (
        <button
          key={idioma}
          type="button"
          lang={idioma}
          aria-pressed={i18n.language === idioma}
          aria-label={`${corto} · ${t(nombre)}`}
          onClick={() => void cambiarIdioma(idioma)}
        >
          {corto}
        </button>
      ))}
    </div>
  )
}
