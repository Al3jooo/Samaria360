import type { ReactNode, Ref } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Acción visible pero deshabilitada, con el estado único de DEC-22 (sin «Próximamente»): borde discontinuo,
 * aria-disabled (sigue siendo enfocable), sin acción y «No disponible» solo para lectores de pantalla. WF-06 y WF-12.
 */
export function BotonNoDisponible({
  children,
  className = '',
  ref,
  descripcion,
}: {
  children: ReactNode
  className?: string
  /** Para devolverle el foco (p. ej. después de inactivar, WF-12). */
  ref?: Ref<HTMLButtonElement>
  /** Id del texto visible con el motivo, si lo hay (p. ej. «La cuenta está desactivada», DEC-122). */
  descripcion?: string
}) {
  const { t } = useTranslation()
  return (
    <button
      ref={ref}
      type="button"
      className={`boton-secundario boton-no-disponible ${className}`.trim()}
      aria-disabled="true"
      aria-describedby={descripcion}
      onClick={(evento) => evento.preventDefault()}
    >
      {children}
      <span className="solo-lector"> ({t('comun.noDisponible')})</span>
    </button>
  )
}

/**
 * Variante en línea del mismo estado único (DEC-22, DEC-270): para una opción de navegación, un orden o un enlace sin
 * destino que conservan el aspecto de su contexto. Texto atenuado con subrayado discontinuo (la señal no depende solo
 * del color), aria-disabled (sigue siendo enfocable), sin acción y «No disponible» solo para lectores de pantalla.
 * Reemplaza a BotonProximamente: «Próximamente» prometía entregas que ninguna iteración tiene planificadas.
 */
export function AccionNoDisponible({ children, className = '' }: { children: ReactNode; className?: string }) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      className={`deshabilitado ${className}`.trim()}
      aria-disabled="true"
      onClick={(evento) => evento.preventDefault()}
    >
      {children}
      <span className="solo-lector"> ({t('comun.noDisponible')})</span>
    </button>
  )
}
