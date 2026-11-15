import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError, alExpirarSesion } from '../../shared/api/httpClient'
import { borrarToken, guardarToken, leerToken, leerVencimiento } from '../../shared/api/tokenSesion'
import { consultarUsuarioActual, iniciarSesion as solicitarInicioSesion, renovarSesion } from './authApi'
import { LIMITE_VALIDACION_MS, SesionContext, type AvisoSesion, type EstadoSesion, type ValorSesion } from './SesionContext'

/**
 * 401 SESION_INVALIDADA → la cuenta cambió después de iniciar sesión (DEC-49); otro 401 → la sesión expiró;
 * 403 → la cuenta dejó de estar activa. Otros errores no cierran la sesión.
 */
function avisoPara(error: unknown): AvisoSesion | null {
  if (!(error instanceof ApiError)) return null
  if (error.status === 401) return error.codigo === 'SESION_INVALIDADA' ? 'invalidada' : 'expirada'
  if (error.status === 403) return 'inactiva'
  return null
}

/** El token guardado tiene un vencimiento conocido y ya pasó (DEC-48). */
function tokenVencido(): boolean {
  const vencimiento = leerVencimiento()
  return vencimiento !== null && vencimiento <= Date.now()
}

/** setTimeout no admite esperas mayores (~24,8 días): si el vencimiento queda más lejos, se revisa de nuevo al llegar. */
const ESPERA_MAXIMA_MS = 2_147_483_647

/**
 * Renovación (DEC-317): en el último minuto del token, si el usuario interactuó con la página desde que se emitió, se
 * pide uno nuevo; sin actividad, la sesión vence como antes (DEC-48). En ese último minuto se revisa cada 10 s, para
 * que una actividad tardía también renueve.
 */
export const MARGEN_RENOVACION_MS = 60_000
const REVISION_FINAL_MS = 10_000

/** Guarda el token recibido con su vencimiento, salvo que expiraEn ya haya pasado según el reloj de este equipo. */
function guardarSesion(token: string, expiraEn: string) {
  const vencimiento = Date.parse(expiraEn)
  guardarToken(token, vencimiento > Date.now() ? vencimiento : null)
}

export function SesionProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<EstadoSesion>(() =>
    leerToken() && !tokenVencido() ? { estado: 'cargando' } : { estado: 'anonimo' },
  )
  const [aviso, setAviso] = useState<AvisoSesion | null>(null)
  // DEC-317: cuándo se emitió el token guardado y la última interacción del usuario (clic, toque o tecla).
  const tokenEmitidoEn = useRef(Date.now())
  const ultimaActividad = useRef(0)

  // Al cargar la app con un token guardado, se valida con /me; un 401 o 403 cierra la sesión.
  // Si /me no confirma la sesión (503, error de red o más de LIMITE_VALIDACION_MS sin respuesta), la sesión queda
  // como no confirmada: estado anónimo, pero el token se conserva, porque nada demostró que sea inválido; al recargar
  // se valida de nuevo. Una respuesta tardía solo vale si el token guardado sigue siendo el mismo con el que se pidió:
  // si hubo un nuevo inicio de sesión o un cierre de sesión mientras tanto, se descarta.
  useEffect(() => {
    const token = leerToken()
    if (!token) {
      return
    }
    // Un token guardado que ya venció no se valida con /me: se descarta con el aviso de sesión expirada.
    if (tokenVencido()) {
      borrarToken()
      setAviso('expirada')
      return
    }
    let vigente = true
    const mismoToken = () => vigente && leerToken() === token
    const limite = setTimeout(() => {
      if (vigente) setSesion((actual) => (actual.estado === 'cargando' ? { estado: 'anonimo' } : actual))
    }, LIMITE_VALIDACION_MS)
    consultarUsuarioActual()
      .then((usuario) => {
        if (mismoToken()) setSesion({ estado: 'autenticado', usuario })
      })
      .catch((error: unknown) => {
        // Un 401 o 403 ya lo atendió alExpirarSesion (borra el token y avisa); aquí solo queda lo que no lo es.
        if (!mismoToken()) return
        const motivo = avisoPara(error)
        if (motivo) {
          borrarToken()
        }
        setSesion({ estado: 'anonimo' })
        setAviso(motivo)
      })
      .finally(() => clearTimeout(limite))
    return () => {
      vigente = false
      clearTimeout(limite)
    }
  }, [])

  // Cualquier petición autenticada que reciba 401 (o 403 por cuenta inactiva) termina la sesión.
  useEffect(
    () =>
      alExpirarSesion((error) => {
        borrarToken()
        setSesion({ estado: 'anonimo' })
        setAviso(avisoPara(error))
      }),
    [],
  )

  // Vencimiento proactivo por expiraEn (DEC-48): un temporizador hasta el vencimiento y una revisión al volver a la
  // pestaña (los navegadores retrasan los temporizadores de las pestañas en segundo plano). Cada revisión vuelve a leer
  // el vencimiento guardado: si cambió (un nuevo inicio de sesión guardó otro token), se reprograma con el valor nuevo y
  // no se borra nada; la sesión se cierra solo si el vencimiento guardado ya pasó.
  // Renovación (DEC-317): en el último minuto, con actividad desde la emisión del token, se pide uno nuevo. Un 401 o
  // 403 de la renovación lo atiende alExpirarSesion; un error de red no cambia nada y la sesión vence a su hora.
  const autenticado = sesion.estado === 'autenticado'
  useEffect(() => {
    if (!autenticado) return
    let temporizador: ReturnType<typeof setTimeout> | undefined
    let renovando = false
    let vigente = true
    const renovar = async () => {
      renovando = true
      const token = leerToken()
      try {
        const nueva = await renovarSesion()
        if (vigente && leerToken() === token) {
          guardarSesion(nueva.token, nueva.expiraEn)
          tokenEmitidoEn.current = Date.now()
        }
      } catch {
        // 401 o 403: ya cerró la sesión alExpirarSesion. Red o 5xx: se intenta en la próxima revisión.
      } finally {
        renovando = false
        if (vigente) revisar()
      }
    }
    const revisar = () => {
      clearTimeout(temporizador)
      const vencimiento = leerVencimiento()
      if (vencimiento === null || !leerToken()) return
      const restante = vencimiento - Date.now()
      if (restante <= 0) {
        borrarToken()
        setSesion({ estado: 'anonimo' })
        setAviso('expirada')
        return
      }
      if (restante <= MARGEN_RENOVACION_MS) {
        if (renovando) return
        if (ultimaActividad.current > tokenEmitidoEn.current) {
          void renovar()
          return
        }
        temporizador = setTimeout(revisar, Math.min(restante, REVISION_FINAL_MS))
        return
      }
      temporizador = setTimeout(revisar, Math.min(restante - MARGEN_RENOVACION_MS, ESPERA_MAXIMA_MS))
    }
    const alCambiarVisibilidad = () => {
      if (document.visibilityState === 'visible') revisar()
    }
    const registrarActividad = () => {
      ultimaActividad.current = Date.now()
    }
    revisar()
    document.addEventListener('visibilitychange', alCambiarVisibilidad)
    window.addEventListener('focus', revisar)
    window.addEventListener('pointerdown', registrarActividad, { capture: true, passive: true })
    window.addEventListener('keydown', registrarActividad, { capture: true, passive: true })
    return () => {
      vigente = false
      clearTimeout(temporizador)
      document.removeEventListener('visibilitychange', alCambiarVisibilidad)
      window.removeEventListener('focus', revisar)
      window.removeEventListener('pointerdown', registrarActividad, { capture: true })
      window.removeEventListener('keydown', registrarActividad, { capture: true })
    }
  }, [autenticado])

  const iniciarSesion = useCallback(async (correo: string, contrasena: string) => {
    const { token, expiraEn } = await solicitarInicioSesion(correo, contrasena)
    // Si expiraEn ya pasó según el reloj de este equipo (adelantado más que la vigencia del token), no se usa: el cierre
    // lo dará el 401. Con el reloj adelantado k minutos (k < 15), la sesión se cierra k minutos antes (DEC-48).
    guardarSesion(token, expiraEn)
    tokenEmitidoEn.current = Date.now()
    try {
      const usuario = await consultarUsuarioActual()
      setSesion({ estado: 'autenticado', usuario })
      setAviso(null)
    } catch (error) {
      borrarToken()
      throw error
    }
  }, [])

  const cerrarSesion = useCallback(() => {
    borrarToken()
    setSesion({ estado: 'anonimo' })
    setAviso(null)
  }, [])

  const descartarAviso = useCallback(() => setAviso(null), [])

  // El 401 (o 403 por cuenta inactiva) ya cierra la sesión por alExpirarSesion; cualquier otro error deja todo igual.
  // Una respuesta tardía solo vale si el token guardado sigue siendo el mismo con el que se pidió.
  const refrescarUsuario = useCallback(async () => {
    const token = leerToken()
    if (!token) return
    try {
      const usuario = await consultarUsuarioActual()
      if (leerToken() === token) {
        setSesion((actual) => (actual.estado === 'autenticado' ? { estado: 'autenticado', usuario } : actual))
      }
    } catch {
      // Red, 5xx o un 401 ya atendido: no hay nada más que hacer.
    }
  }, [])

  const valor = useMemo<ValorSesion>(
    () => ({ sesion, aviso, descartarAviso, iniciarSesion, cerrarSesion, refrescarUsuario }),
    [sesion, aviso, descartarAviso, iniciarSesion, cerrarSesion, refrescarUsuario],
  )

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>
}
