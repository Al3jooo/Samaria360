import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react'

/**
 * Base de los diálogos de WF-12: <dialog> nativo con showModal(), como DialogoCancelar (WF-11). El navegador atrapa el
 * foco y cierra con Esc; al abrirse, el foco va a `focoInicial`. `onCerrar` lo llaman Esc y los botones de cancelar;
 * quien lo usa devuelve el foco al botón que lo abrió. Mientras `bloqueado` (enviando), Esc no cierra.
 */
export function DialogoAdmin({
  titulo,
  bloqueado = false,
  focoInicial,
  onCerrar,
  children,
}: {
  titulo: string
  bloqueado?: boolean
  focoInicial: RefObject<HTMLElement | null>
  onCerrar: () => void
  children: (idTexto: string) => ReactNode
}) {
  const id = useId()
  const dialogo = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const elemento = dialogo.current
    if (elemento && !elemento.open) elemento.showModal()
    focoInicial.current?.focus()
    // Solo al abrirse.
  }, [focoInicial])

  return (
    <dialog
      ref={dialogo}
      className="dialogo dialogo-cuentas"
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-texto`}
      onClose={onCerrar}
      onCancel={(evento) => {
        if (bloqueado) evento.preventDefault()
      }}
    >
      <h2 id={`${id}-titulo`}>{titulo}</h2>
      {children(`${id}-texto`)}
    </dialog>
  )
}
