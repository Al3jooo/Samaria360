import { solicitar } from '../../shared/api/httpClient'

/** Contrato de ms-chatbot (`docs/sad/api/ms-chatbot.md`; DEC-297, DEC-298). Sin historial en el servidor. */

const BASE = '/api/v1/chatbot'

/** Límites de la decisión 5 del equipo (DEC-297), los mismos del servidor. */
export const MENSAJE_MAXIMO = 500
export const MENSAJES_POR_DIA = 20
export const NOMBRE_ITINERARIO_MAXIMO = 100
export const ITINERARIOS_MAXIMOS = 20
export const CONOCIMIENTO_MAXIMO = 2000
export const CATEGORIA_CONOCIMIENTO_MAXIMA = 50

/** RF36: el criterio principal de cada sugerencia, calculado con datos (DEC-298). */
export type CriterioSugerencia = 'PREFERENCIAS' | 'POPULARIDAD' | 'DISPONIBILIDAD'

export interface Sugerencia {
  tipo: 'Servicio' | 'Atractivo'
  id: string
  nombre: string
  categoria: string | null
  criterio: CriterioSugerencia
  justificacion: string
}

export interface ItemItinerario {
  orden: number
  tipo: 'servicio' | 'atractivo'
  refId: string
  tituloActividad: string
  diaSugerido: number
}

export interface RespuestaAsistente {
  respuesta: string
  sugerencias: Sugerencia[]
  itinerario: ItemItinerario[]
  /** Categorías de la base de conocimientos usadas (RF05). */
  fuentes: string[]
  catalogoDisponible: boolean
  mensajesRestantes: number
}

/** RF04 y RF59: el idioma es el de la interfaz; los intereses, los del perfil (no se guardan en ms-chatbot). */
export function enviarMensaje(mensaje: string, idioma: string, intereses: string[]): Promise<RespuestaAsistente> {
  return solicitar<RespuestaAsistente>(`${BASE}/mensajes`, { method: 'POST', cuerpo: { mensaje, idioma, intereses } })
}

export interface Itinerario {
  id: string
  nombre: string
  items: ItemItinerario[]
  fechaCreacion: string
  fechaActualizacion: string
}

export interface CambiosItinerario {
  nombre: string
  items: ItemItinerario[]
}

/** RF35: los itinerarios guardados del turista del token. */
export async function listarItinerarios(): Promise<Itinerario[]> {
  return (await solicitar<{ elementos: Itinerario[] }>(`${BASE}/itinerarios`)).elementos
}

export function guardarItinerario(cambios: CambiosItinerario): Promise<Itinerario> {
  return solicitar<Itinerario>(`${BASE}/itinerarios`, { method: 'POST', cuerpo: cambios })
}

export function actualizarItinerario(id: string, cambios: CambiosItinerario): Promise<Itinerario> {
  return solicitar<Itinerario>(`${BASE}/itinerarios/${encodeURIComponent(id)}`, { method: 'PUT', cuerpo: cambios })
}

export function eliminarItinerario(id: string): Promise<void> {
  return solicitar<void>(`${BASE}/itinerarios/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/** RF05: base de conocimientos, solo el Administrador. */
export interface EntradaConocimiento {
  id: string
  contenido: string
  categoria: string
  fechaActualizacion: string
}

export interface PaginaConocimiento {
  elementos: EntradaConocimiento[]
  pagina: number
  tamano: number
  totalElementos: number
}

export function listarConocimiento(pagina: number): Promise<PaginaConocimiento> {
  return solicitar<PaginaConocimiento>(`${BASE}/admin/conocimiento?pagina=${Math.max(0, pagina)}`)
}

export function crearConocimiento(contenido: string, categoria: string): Promise<EntradaConocimiento> {
  return solicitar<EntradaConocimiento>(`${BASE}/admin/conocimiento`, {
    method: 'POST',
    cuerpo: { contenido, categoria },
  })
}

export function actualizarConocimiento(
  id: string,
  contenido: string,
  categoria: string,
): Promise<EntradaConocimiento> {
  return solicitar<EntradaConocimiento>(`${BASE}/admin/conocimiento/${encodeURIComponent(id)}`, {
    method: 'PUT',
    cuerpo: { contenido, categoria },
  })
}

export function eliminarConocimiento(id: string): Promise<void> {
  return solicitar<void>(`${BASE}/admin/conocimiento/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/** Agrupa los pasos por día sugerido, en orden. */
export function porDia(items: ItemItinerario[]): { dia: number; items: ItemItinerario[] }[] {
  const dias = new Map<number, ItemItinerario[]>()
  ;[...items]
    .sort((a, b) => a.diaSugerido - b.diaSugerido || a.orden - b.orden)
    .forEach((item) => dias.set(item.diaSugerido, [...(dias.get(item.diaSugerido) ?? []), item]))
  return [...dias.entries()].map(([dia, pasos]) => ({ dia, items: pasos }))
}

/** Ruta de la ficha pública de cada paso (WF-06 o la ficha del atractivo). */
export function rutaPaso(tipo: string, id: string): string {
  return tipo.toLowerCase() === 'atractivo'
    ? `/atractivos/${encodeURIComponent(id)}`
    : `/servicios/${encodeURIComponent(id)}`
}
