import { leerToken } from './tokenSesion'

/** Cuerpo RFC 9457 que devuelven los microservicios, con el campo `codigo` estable. */
export interface ProblemDetail {
  status?: number
  title?: string
  detail?: string
  codigo?: string
  [propiedad: string]: unknown
}

export const ERROR_RED = 'ERROR_RED'

export class ApiError extends Error {
  readonly status: number
  readonly codigo: string
  readonly problema: ProblemDetail | null

  constructor(status: number, codigo: string, problema: ProblemDetail | null) {
    super(problema?.detail ?? codigo)
    this.name = 'ApiError'
    this.status = status
    this.codigo = codigo
    this.problema = problema
  }
}

type OyenteSesionExpirada = (error: ApiError) => void
const oyentes = new Set<OyenteSesionExpirada>()

/** Avisa cuando una petición autenticada revela que la sesión ya no es válida. Devuelve la función para desuscribirse. */
export function alExpirarSesion(oyente: OyenteSesionExpirada): () => void {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

interface OpcionesSolicitud {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
  /** JSON ya armado, que se envía tal cual (p. ej. con un número decimal exacto que JSON.stringify no conserva). */
  cuerpoTexto?: string
  /**
   * Cuerpo multipart (registro de atractivos, DEC-216, DEC-230): se envía tal cual y sin Content-Type, que lo pone el
   * navegador con su boundary.
   */
  formulario?: FormData
  /** Cabeceras adicionales (p. ej. Idempotency-Key). */
  cabeceras?: Record<string, string>
  /**
   * Endpoint público (DEC-47): sale sin Authorization y su respuesta nunca cierra la sesión. Los servicios validan
   * cualquier Bearer que llegue, también a un endpoint público: con el token vencido, la búsqueda respondería 401.
   */
  publica?: boolean
}

/**
 * Cliente HTTP de la aplicación. Las rutas son siempre relativas (/api/v1/...): en desarrollo las
 * atiende el proxy de Vite y en despliegue el API Gateway.
 */
export async function solicitar<T>(ruta: string, opciones: OpcionesSolicitud = {}): Promise<T> {
  const token = opciones.publica ? null : leerToken()
  const cabeceras: Record<string, string> = { ...opciones.cabeceras, Accept: 'application/json' }
  const json = opciones.cuerpoTexto ?? (opciones.cuerpo === undefined ? undefined : JSON.stringify(opciones.cuerpo))
  if (json !== undefined) {
    cabeceras['Content-Type'] = 'application/json'
  }
  const cuerpo: BodyInit | undefined = opciones.formulario ?? json
  if (token) {
    cabeceras.Authorization = `Bearer ${token}`
  }

  let respuesta: Response
  try {
    respuesta = await fetch(ruta, {
      method: opciones.method ?? 'GET',
      headers: cabeceras,
      body: cuerpo,
    })
  } catch {
    throw new ApiError(0, ERROR_RED, null)
  }

  if (!respuesta.ok) {
    const problema = await leerProblema(respuesta)
    const error = new ApiError(respuesta.status, problema?.codigo ?? `HTTP_${respuesta.status}`, problema)
    // Solo una petición que salió con token puede revelar que la sesión ya no vale.
    if (token && sesionInvalida(error)) {
      oyentes.forEach((oyente) => oyente(error))
    }
    throw error
  }

  // 204 No Content (p. ej. DELETE /admin/faq/{id}, DEC-73): no hay cuerpo que leer.
  if (respuesta.status === 204) {
    return undefined as T
  }
  // 202 puede llegar sin cuerpo (PUT de la plantilla de RF19, DEC-310) o con él (registro, ADR-006).
  if (respuesta.status === 202) {
    const texto = await respuesta.text()
    return (texto === '' ? undefined : JSON.parse(texto)) as T
  }
  return (await respuesta.json()) as T
}

/** Respuestas de un intermediario (nginx o el gateway) cuando el servicio de atrás no responde. */
const ESTADOS_NO_DISPONIBLE = new Set([502, 503, 504])

/**
 * Caída o arranque de un servicio: 502, 503 o 504, o el ProblemDetail SERVICIO_NO_DISPONIBLE (gateway o nginx).
 * Un 500 no cuenta: es un defecto del servidor, no una caída.
 */
export function servicioNoDisponible(error: ApiError): boolean {
  return ESTADOS_NO_DISPONIBLE.has(error.status) || error.codigo === 'SERVICIO_NO_DISPONIBLE'
}

/**
 * Servicio que no respondió, según el campo `servicio` del ProblemDetail (p. ej. el 503 de ms-booking con
 * `servicio: ms-catalog` cuando falta materializar la oferta, O14 y DEC-171); null si no lo trae.
 */
export function servicioDelProblema(error: ApiError): string | null {
  const servicio = error.problema?.servicio
  return typeof servicio === 'string' ? servicio : null
}

/** 401, o 403 por cuenta no activa (RF22, RF62). Un 403 por rol (ACCESO_DENEGADO) no cierra la sesión. */
export function sesionInvalida(error: ApiError): boolean {
  return error.status === 401 || (error.status === 403 && error.codigo === 'CUENTA_INACTIVA')
}

async function leerProblema(respuesta: Response): Promise<ProblemDetail | null> {
  try {
    return (await respuesta.json()) as ProblemDetail
  } catch {
    return null
  }
}
