import type { NavegadorCompartir, ResultadoCompartir } from '../reservas/compartirReserva'

const esCancelacion = (error: unknown) => error instanceof DOMException && error.name === 'AbortError'

/**
 * «Compartir» del itinerario sugerido (WF-07, DEC-298), solo en el frontend, como RF49: la hoja de compartir del
 * sistema con el texto del itinerario o, si no existe, el portapapeles; si tampoco, la pantalla lo avisa.
 */
export async function compartirTexto(
  titulo: string,
  texto: string,
  navegador: NavegadorCompartir = globalThis.navigator,
): Promise<ResultadoCompartir> {
  if (typeof navegador?.share === 'function') {
    try {
      await navegador.share({ title: titulo, text: texto })
      return 'compartido'
    } catch (error) {
      if (esCancelacion(error)) return 'cancelado'
    }
  }
  if (typeof navegador?.clipboard?.writeText === 'function') {
    try {
      await navegador.clipboard.writeText(`${titulo}\n\n${texto}`)
      return 'copiado'
    } catch {
      // Sin permiso para el portapapeles.
    }
  }
  return 'manual'
}
