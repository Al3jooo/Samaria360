import { solicitar } from '../../shared/api/httpClient'

const BASE = '/api/v1/auth'

export interface SesionIniciada {
  token: string
  tipo: 'Bearer'
  /** Instante ISO 8601 (UTC) del `exp` del token: 15 minutos después del inicio de sesión (DEC-29). */
  expiraEn: string
  idUsuario: string
  rol: string
}

export interface UsuarioActual {
  idUsuario: string
  correo: string
  rol: string
  estadoCuenta: string
  nombreVisible: string
}

export function iniciarSesion(correo: string, contrasena: string): Promise<SesionIniciada> {
  return solicitar<SesionIniciada>(`${BASE}/login`, { method: 'POST', cuerpo: { correo, contrasena }, publica: true })
}

/**
 * Renueva la sesión con el token vigente (DEC-317): devuelve otro token con la misma forma que el del login. La sesión
 * no pasa de 8 horas desde el inicio (401 SESION_VENCIDA) y una cuenta inactiva recibe 403.
 */
export function renovarSesion(): Promise<SesionIniciada> {
  return solicitar<SesionIniciada>(`${BASE}/sesion/renovacion`, { method: 'POST' })
}

export function consultarUsuarioActual(): Promise<UsuarioActual> {
  return solicitar<UsuarioActual>(`${BASE}/me`)
}

/** Cuerpo de POST /api/v1/auth/registro/turista (RF01, WF-02). Los nombres son los que usa `campos` en DATOS_INVALIDOS. */
export interface DatosRegistroTurista {
  primerNombre: string
  segundoNombre: string
  primerApellido: string
  segundoApellido: string
  correo: string
  contrasena: string
  paisOrigen: string
  categoriasInteres: string[]
  aceptoPoliticas: boolean
}

/** Cuerpo de POST /api/v1/auth/registro/prestador (RF02, WF-03). */
export interface DatosRegistroPrestador {
  nombreRepresentanteLegal: string
  /** Opcional (decisión D2, V5). WF-03 no lo pide, así que el formulario no lo envía. */
  razonSocial?: string
  rnt: string
  correoCorporativo: string
  telefono: string
  contrasena: string
  aceptoPoliticas: boolean
}

/**
 * El registro responde siempre 202 con el mismo cuerpo, exista o no el correo (ADR-006): no hay nada que leer de la
 * respuesta ni se inicia sesión.
 */
export async function registrarTurista(datos: DatosRegistroTurista): Promise<void> {
  await solicitar<unknown>(`${BASE}/registro/turista`, { method: 'POST', cuerpo: datos, publica: true })
}

export async function registrarPrestador(datos: DatosRegistroPrestador): Promise<void> {
  await solicitar<unknown>(`${BASE}/registro/prestador`, { method: 'POST', cuerpo: datos, publica: true })
}

/**
 * RF34, primer paso: responde siempre 202 con el mismo cuerpo, exista o no la cuenta (ADR-006). El idioma de la
 * interfaz (RF03) elige el del correo.
 */
export async function solicitarRecuperacion(correo: string, idioma: string): Promise<void> {
  await solicitar<unknown>(`${BASE}/recuperacion`, { method: 'POST', cuerpo: { correo, idioma }, publica: true })
}

/** RF34, segundo paso: 204, o 400 TOKEN_INVALIDO si el enlace no existe, ya se usó o venció (DEC-285). */
export async function restablecerContrasena(token: string, contrasena: string): Promise<void> {
  await solicitar<unknown>(`${BASE}/recuperacion/restablecimiento`, {
    method: 'POST',
    cuerpo: { token, contrasena },
    publica: true,
  })
}

/** Códigos ISO 3166-1 alfa-2 (decisión D5): la única lista de países; los nombres los pone el navegador. */
export function consultarPaises(): Promise<string[]> {
  return solicitar<string[]>(`${BASE}/paises`, { publica: true })
}
