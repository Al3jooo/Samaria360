import { solicitar } from '../../shared/api/httpClient'
import type { Categoria } from '../catalogo/catalogoApi'
import type { EstadoPublicacion, ServicioGestion } from '../servicios/servicioGestion'

/**
 * Contrato de `/api/v1/catalogo/prestador/servicios` (ms-catalog, iteración C, A1; docs/sad/api/ms-catalog.md): solo
 * el rol Prestador y siempre los servicios del `sub` del token. Peticiones con token (no son públicas, DEC-47).
 * Los estados y la forma de gestión son comunes con la revisión del Administrador (features/servicios, DEC-176).
 */
export { ESTADOS_PUBLICACION } from '../servicios/servicioGestion'
export type { EstadoPublicacion, ServicioGestion } from '../servicios/servicioGestion'

/** Un servicio en el listado de «Mis servicios» (WF-18). */
export interface ServicioResumen {
  id: string
  nombreComercial: string
  categoria: Categoria
  estadoPublicacion: EstadoPublicacion
  /** Solo en los Rechazados (DEC-143). */
  motivoRechazo: string | null
  /** ISO 8601 en UTC. */
  fechaRegistro: string
}

export interface PaginaServicios {
  elementos: ServicioResumen[]
  /** Desde 0, como la API. */
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

const RUTA = '/api/v1/catalogo/prestador/servicios'

/** RF09, RF10, RF37: el servicio nace Pendiente. 201 con Location y la forma de gestión. */
/** Multipart (DEC-318), como el registro de atractivos: la parte `datos` (el JSON) y de 1 a 5 partes `imagenes`. */
export function registrarServicio(cuerpo: unknown, imagenes: File[]): Promise<ServicioGestion> {
  const formulario = new FormData()
  formulario.append('datos', new Blob([JSON.stringify(cuerpo)], { type: 'application/json' }))
  for (const imagen of imagenes) formulario.append('imagenes', imagen, imagen.name)
  return solicitar<ServicioGestion>(RUTA, { method: 'POST', formulario })
}

/** «Consultar mis servicios» (DEC-129): `pagina` desde 1 en la pantalla, desde 0 en la API. Sin estado, todos. */
export function listarMisServicios(estado: string, pagina: number): Promise<PaginaServicios> {
  const parametros = new URLSearchParams({ pagina: String(pagina - 1) })
  if (estado) parametros.set('estado', estado)
  return solicitar<PaginaServicios>(`${RUTA}?${parametros}`)
}

/** Un servicio propio, en cualquier estado; uno ajeno o inexistente responde 404 SERVICIO_NO_ENCONTRADO. */
export function consultarMiServicio(id: string): Promise<ServicioGestion> {
  return solicitar<ServicioGestion>(`${RUTA}/${encodeURIComponent(id)}`)
}

/**
 * RF32 «Actualizar tarifas» (iteración D, DEC-184 a DEC-187): `{precioBase, version}` en Tour, Gastronomía y Alquiler;
 * `{unidades:[{idUnidad, precioNoche}], version}` en Alojamiento (todas las unidades). 200 con la forma de gestión y la
 * versión nueva; 409 SERVICIO_MODIFICADO (`versionActual`) o ESTADO_CAMBIADO (`estadoActual`).
 */
export function actualizarPrecio(id: string, cuerpo: Record<string, unknown>): Promise<ServicioGestion> {
  return solicitar<ServicioGestion>(`${RUTA}/${encodeURIComponent(id)}/precio`, { method: 'PUT', cuerpo })
}

/** RF43, datos comerciales del servicio: `{nombreComercial, contacto{telefono, correo}, version}`; mismos estados y errores. */
export function actualizarDatosComerciales(id: string, cuerpo: Record<string, unknown>): Promise<ServicioGestion> {
  return solicitar<ServicioGestion>(`${RUTA}/${encodeURIComponent(id)}/datos-comerciales`, { method: 'PUT', cuerpo })
}

/** Tope del selector «Servicio» del historial (DEC-204, C6): 20 páginas de 10, es decir, 200 servicios. */
export const MAXIMO_PAGINAS_SELECTOR = 20

/**
 * Los servicios del Prestador para el filtro del historial (RF12, DEC-204): las páginas en secuencia, del más reciente
 * al más antiguo (`fechaRegistro`, como la API), hasta `MAXIMO_PAGINAS_SELECTOR`. `truncado` indica que hay más.
 */
export async function listarServiciosParaFiltro(): Promise<{ servicios: ServicioResumen[]; truncado: boolean }> {
  const servicios: ServicioResumen[] = []
  for (let pagina = 1; pagina <= MAXIMO_PAGINAS_SELECTOR; pagina += 1) {
    const actual = await listarMisServicios('', pagina)
    servicios.push(...actual.elementos)
    if (pagina >= actual.totalPaginas) return { servicios, truncado: false }
  }
  return { servicios, truncado: true }
}
