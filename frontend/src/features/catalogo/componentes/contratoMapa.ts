import type { ResultadoOferta } from '../catalogoApi'

/** Identidad compartida entre tarjetas y marcadores, sin cargar Leaflet. */
export function claveResultado(resultado: Pick<ResultadoOferta, 'tipo' | 'id'>): string {
  return `${resultado.tipo}-${resultado.id}`
}

/** Estado vigente del favorito al abrir el popup (RF54). */
export interface FavoritoPopup {
  modo: 'turista' | 'visitante'
  listo: boolean
  esFavorito: (resultado: ResultadoOferta) => boolean
  alternar: (resultado: ResultadoOferta) => Promise<boolean>
  irALogin: () => void
}
