import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { listarPrestadores } from '../../admin/adminApi'
import { buscarOferta } from '../../catalogo/catalogoApi'
import { leerCriterios } from '../../catalogo/criteriosUrl'
import { consultarServicio } from '../../catalogo/detalle/detalleApi'
import { consultarOpcionesFiltro } from '../../catalogo/opcionesFiltroApi'
import { consultarDisponibilidad, consultarMisReservas, consultarResumen } from '../../reservas/reservasApi'
import {
  consultarPaises,
  consultarUsuarioActual,
  iniciarSesion,
  registrarPrestador,
  registrarTurista,
  type DatosRegistroPrestador,
  type DatosRegistroTurista,
} from '../authApi'
import {
  DETALLE_HOTEL,
  USUARIO_ADMIN,
  USUARIO_TURISTA,
  paginaAdmin,
  paginaOferta,
  paginaReservas,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const LOGIN = 'POST /api/v1/auth/login'
const CATALOGO = { 'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])) }
const AHORA = new Date('2026-10-21T12:00:00Z')
const MINUTO = 60_000

const token = () => sessionStorage.getItem('samaria360.token')
const vencimiento = () => sessionStorage.getItem('samaria360.expiraEn')

function sesionGuardada(valor = 'token-guardado', venceEn?: number) {
  sessionStorage.setItem('samaria360.token', valor)
  if (venceEn !== undefined) sessionStorage.setItem('samaria360.expiraEn', String(venceEn))
}

afterEach(() => {
  vi.useRealTimers()
})

describe('N11: los endpoints públicos salen sin token (DEC-47)', () => {
  it('las 9 llamadas públicas no llevan Authorization y las autenticadas sí', async () => {
    sesionGuardada()
    const ok = () => respuestaJson(200, {})
    const { solicitudes } = simularFetch({
      [LOGIN]: () => respuestaJson(200, { token: 't', tipo: 'Bearer', expiraEn: '2026-10-21T12:15:00Z', idUsuario: 'x', rol: 'Turista' }),
      'POST /api/v1/auth/registro/turista': () => respuestaJson(202, { estado: 'RECIBIDO' }),
      'POST /api/v1/auth/registro/prestador': () => respuestaJson(202, { estado: 'RECIBIDO' }),
      'GET /api/v1/auth/paises': () => respuestaJson(200, ['CO']),
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])),
      'GET /api/v1/catalogo/filtros': ok,
      [`GET /api/v1/catalogo/servicios/${DETALLE_HOTEL.id}`]: () => respuestaJson(200, DETALLE_HOTEL),
      'GET /api/v1/reservas/disponibilidad': ok,
      'GET /api/v1/reservas/disponibilidad/resumen': ok,
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      'GET /api/v1/reservas/mias': () => respuestaJson(200, paginaReservas([])),
      'GET /api/v1/auth/admin/prestadores': () => respuestaJson(200, paginaAdmin([])),
    })

    await iniciarSesion('turista@samaria360.test', 'x')
    await registrarTurista({} as DatosRegistroTurista)
    await registrarPrestador({} as DatosRegistroPrestador)
    await consultarPaises()
    await buscarOferta(leerCriterios(new URLSearchParams()))
    await consultarOpcionesFiltro('Alojamiento')
    await consultarServicio(DETALLE_HOTEL.id)
    await consultarDisponibilidad(DETALLE_HOTEL.id, '2026-12-01', '2026-12-02')
    await consultarResumen([DETALLE_HOTEL.id], '2026-12-01', '2026-12-02')
    await consultarUsuarioActual()
    await consultarMisReservas({ estados: [] }, 1)
    await listarPrestadores({}, 1)

    const publicas = solicitudes.slice(0, 9)
    expect(publicas.map((s) => s.url.split('?')[0])).toEqual([
      '/api/v1/auth/login',
      '/api/v1/auth/registro/turista',
      '/api/v1/auth/registro/prestador',
      '/api/v1/auth/paises',
      '/api/v1/catalogo/oferta',
      '/api/v1/catalogo/filtros',
      `/api/v1/catalogo/servicios/${DETALLE_HOTEL.id}`,
      '/api/v1/reservas/disponibilidad',
      '/api/v1/reservas/disponibilidad/resumen',
    ])
    for (const solicitud of publicas) expect(solicitud.cabeceras.Authorization).toBeUndefined()
    for (const solicitud of solicitudes.slice(9)) expect(solicitud.cabeceras.Authorization).toBe('Bearer token-guardado')
  })

  it('un 401 CREDENCIALES_INVALIDAS en el login no deja ningún aviso de sesión (B1)', async () => {
    // Token guardado sin confirmar (/me con 503): antes, el login lo enviaba y su 401 avisaba «Tu sesión expiró».
    sesionGuardada()
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE'),
      [LOGIN]: () => respuestaProblema(401, 'CREDENCIALES_INVALIDAS'),
      ...CATALOGO,
    })
    const { usuario } = renderApp('/login')

    await usuario.type(await screen.findByLabelText('Correo electrónico'), 'ana@correo.com')
    await usuario.type(screen.getByLabelText('Contraseña'), 'incorrecta')
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByText('Correo o contraseña incorrectos.')).toBeInTheDocument()
    const login = solicitudes.find((s) => s.url === '/api/v1/auth/login')
    expect(login?.cabeceras.Authorization).toBeUndefined()
    expect(token()).toBe('token-guardado')

    await usuario.click(screen.getByRole('link', { name: 'Samaria360, ir al inicio' }))
    await waitFor(() => expect(ubicacionActual()).toBe('/'))
    expect(screen.queryByText(/Tu sesión/)).not.toBeInTheDocument()
  })
})

describe('Cierre por una petición autenticada', () => {
  it('un 401 de /mias cierra la sesión y el login dice por qué (DEC-50)', async () => {
    sesionGuardada()
    simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      'GET /api/v1/reservas/mias': () => respuestaProblema(401, 'NO_AUTENTICADO'),
    })
    renderApp('/mis-reservas')

    await waitFor(() => expect(ubicacionActual()).toBe('/login'))
    expect(await screen.findByText('Tu sesión expiró.')).toBeInTheDocument()
    expect(token()).toBeNull()
  })

  it('un 401 SESION_INVALIDADA de /admin muestra su mensaje propio en el login (DEC-49)', async () => {
    sesionGuardada()
    simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_ADMIN),
      'GET /api/v1/auth/admin/prestadores': () => respuestaProblema(401, 'SESION_INVALIDADA'),
    })
    renderApp('/admin/cuentas')

    await waitFor(() => expect(ubicacionActual()).toBe('/login'))
    expect(
      await screen.findByText('Tu sesión se cerró porque cambió el estado o los permisos de tu cuenta.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Tu sesión expiró.')).not.toBeInTheDocument()
    expect(token()).toBeNull()
  })

  it('SESION_INVALIDADA en una página pública: aviso propio con enlace al login', async () => {
    sesionGuardada()
    simularFetch({ [ME]: () => respuestaProblema(401, 'SESION_INVALIDADA'), ...CATALOGO })
    renderApp('/')

    const aviso = (
      await screen.findByText(/Tu sesión se cerró porque cambió el estado o los permisos de tu cuenta\./)
    ).closest('[role="status"]')
    expect(aviso).not.toBeNull()
    expect(screen.getByRole('link', { name: 'Inicia sesión de nuevo' })).toHaveAttribute('href', '/login')
    expect(ubicacionActual()).toBe('/')
  })
})

describe('Vencimiento por expiraEn (DEC-48)', () => {
  it('al llegar a expiraEn la sesión se cierra con «Tu sesión expiró»', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AHORA)
    simularFetch({
      [LOGIN]: () =>
        respuestaJson(200, {
          token: 'token-nuevo',
          tipo: 'Bearer',
          expiraEn: new Date(AHORA.getTime() + 15 * MINUTO).toISOString(),
          idUsuario: USUARIO_TURISTA.idUsuario,
          rol: 'Turista',
        }),
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      ...CATALOGO,
    })
    renderApp('/login')

    fireEvent.change(await screen.findByLabelText('Correo electrónico'), { target: { value: 'turista@samaria360.test' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'x' } })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()
    expect(vencimiento()).toBe(String(AHORA.getTime() + 15 * MINUTO))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15 * MINUTO - 1000)
    })
    expect(screen.getByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    expect(await screen.findByText(/Tu sesión expiró\./)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Menú de usuario/ })).not.toBeInTheDocument()
    expect(token()).toBeNull()
    expect(vencimiento()).toBeNull()
  })

  it('al volver a la pestaña después del vencimiento (temporizador retrasado), la sesión se cierra', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AHORA)
    sesionGuardada('token-guardado', AHORA.getTime() + 15 * MINUTO)
    simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA), ...CATALOGO })
    renderApp('/')
    await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })

    // El reloj avanza sin que se dispare el temporizador (pestaña en segundo plano).
    vi.setSystemTime(AHORA.getTime() + 20 * MINUTO)
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'))
    })

    expect(await screen.findByText(/Tu sesión expiró\./)).toBeInTheDocument()
    expect(token()).toBeNull()
  })

  it('un token guardado ya vencido no se valida con /me', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AHORA)
    sesionGuardada('token-guardado', AHORA.getTime() - MINUTO)
    const { solicitudes } = simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA), ...CATALOGO })
    renderApp('/')

    expect(await screen.findByText(/Tu sesión expiró\./)).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url === '/api/v1/auth/me')).toBe(false)
    expect(token()).toBeNull()
  })

  it('si el vencimiento guardado cambió (nuevo inicio de sesión), el temporizador se reprograma y no borra el token nuevo', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AHORA)
    sesionGuardada('token-1', AHORA.getTime() + 10 * MINUTO)
    simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA), ...CATALOGO })
    renderApp('/')
    await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })

    // Otro inicio de sesión guarda token-2 con un vencimiento posterior antes de que dispare el temporizador de token-1.
    sesionGuardada('token-2', AHORA.getTime() + 30 * MINUTO)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10 * MINUTO)
    })
    expect(token()).toBe('token-2')
    expect(screen.getByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()
    expect(screen.queryByText(/Tu sesión expiró\./)).not.toBeInTheDocument()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(20 * MINUTO)
    })
    expect(await screen.findByText(/Tu sesión expiró\./)).toBeInTheDocument()
    expect(token()).toBeNull()
  })

  it('con un expiraEn ya pasado al recibirlo (reloj adelantado), no se guarda: el cierre lo dará el 401', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AHORA)
    simularFetch({
      [LOGIN]: () =>
        respuestaJson(200, {
          token: 'token-nuevo',
          tipo: 'Bearer',
          expiraEn: new Date(AHORA.getTime() - MINUTO).toISOString(),
          idUsuario: USUARIO_TURISTA.idUsuario,
          rol: 'Turista',
        }),
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      ...CATALOGO,
    })
    renderApp('/login')

    fireEvent.change(await screen.findByLabelText('Correo electrónico'), { target: { value: 'turista@samaria360.test' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'x' } })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()
    expect(token()).toBe('token-nuevo')
    expect(vencimiento()).toBeNull()
  })
})

describe('Renovación de la sesión (DEC-317)', () => {
  const RENOVACION = 'POST /api/v1/auth/sesion/renovacion'
  const sesion = (valor: string, venceEn: number) => ({
    token: valor,
    tipo: 'Bearer',
    expiraEn: new Date(venceEn).toISOString(),
    idUsuario: USUARIO_TURISTA.idUsuario,
    rol: 'Turista',
  })

  async function iniciar(manejadores: Record<string, () => Response>) {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AHORA)
    const simulacion = simularFetch({
      [LOGIN]: () => respuestaJson(200, sesion('token-1', AHORA.getTime() + 15 * MINUTO)),
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      ...CATALOGO,
      ...manejadores,
    })
    renderApp('/login')
    fireEvent.change(await screen.findByLabelText('Correo electrónico'), { target: { value: 'turista@samaria360.test' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'x' } })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()
    return simulacion
  }

  it('con actividad del usuario, en el último minuto pide otro token y la sesión sigue después del vencimiento', async () => {
    const { solicitudes } = await iniciar({
      [RENOVACION]: () => respuestaJson(200, sesion('token-2', AHORA.getTime() + 29 * MINUTO)),
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5 * MINUTO)
    })
    fireEvent.pointerDown(document.body)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(9 * MINUTO + 1000)
    })

    const renovaciones = solicitudes.filter((s) => s.metodo === 'POST' && s.url === '/api/v1/auth/sesion/renovacion')
    expect(renovaciones).toHaveLength(1)
    expect(renovaciones[0].cabeceras.Authorization).toBe('Bearer token-1')
    expect(token()).toBe('token-2')
    expect(vencimiento()).toBe(String(AHORA.getTime() + 29 * MINUTO))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2 * MINUTO)
    })
    expect(screen.getByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()
  })

  it('sin actividad desde el inicio de sesión no renueva: vence a los 15 minutos', async () => {
    const { solicitudes } = await iniciar({
      [RENOVACION]: () => respuestaJson(200, sesion('token-2', AHORA.getTime() + 29 * MINUTO)),
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15 * MINUTO)
    })

    expect(await screen.findByText(/Tu sesión expiró\./)).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url === '/api/v1/auth/sesion/renovacion')).toBe(false)
  })

  it('si la sesión pasó de 8 horas (401 SESION_VENCIDA), se cierra con «Tu sesión expiró»', async () => {
    await iniciar({ [RENOVACION]: () => respuestaProblema(401, 'SESION_VENCIDA') })

    fireEvent.keyDown(document.body, { key: 'a' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(14 * MINUTO + 1000)
    })

    expect(await screen.findByText(/Tu sesión expiró\./)).toBeInTheDocument()
    expect(token()).toBeNull()
  })
})
