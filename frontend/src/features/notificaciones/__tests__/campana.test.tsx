import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  USUARIO_ADMIN,
  USUARIO_PRESTADOR,
  USUARIO_TURISTA,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const CAMPANA = 'GET /api/v1/analytics/notificaciones'
const OFERTA = 'GET /api/v1/catalogo/oferta'

const NUEVA = {
  id: 'n1',
  tipo: 'NUEVA_RESERVA',
  mensaje: 'Nueva reserva…',
  datos: { nombreServicio: 'Tour a Playa Cristal', fecha: '2030-03-10', personas: '4' },
  leida: false,
  fecha: '2030-03-01T15:00:00Z',
}

function pagina(elementos: unknown[], noLeidas: number) {
  return { elementos, pagina: 0, tamano: 20, totalElementos: elementos.length, noLeidas }
}

describe('Campana de notificaciones (RF63, WF-29)', () => {
  it('el Prestador ve el contador y la reserva nueva con actividad, fecha y personas, y la marca leída', async () => {
    sessionStorage.setItem('samaria360.token', 'token-prestador')
    let leidas = false
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_PRESTADOR),
      [OFERTA]: () => respuestaJson(200, paginaOferta([])),
      [CAMPANA]: () => respuestaJson(200, pagina([{ ...NUEVA, leida: leidas }], leidas ? 0 : 1)),
      'POST /api/v1/analytics/notificaciones/lectura': () => {
        leidas = true
        return respuestaJson(200, { marcadas: 1 })
      },
    })
    const { usuario } = renderApp('/')

    const boton = await screen.findByRole('button', { name: 'Notificaciones: 1 sin leer' })
    await usuario.click(boton)
    const panel = screen.getByRole('region', { name: 'Notificaciones' })
    expect(within(panel).getByText(/Nueva reserva de «Tour a Playa Cristal» para el 10\/03\/2030: 4 personas\./))
      .toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: /Tour a Playa Cristal/ })).toHaveAttribute('href', '/prestador/reservas')

    await usuario.click(within(panel).getByRole('button', { name: 'Marcar todas como leídas' }))

    expect(await screen.findByRole('button', { name: 'Notificaciones' })).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url === '/api/v1/analytics/notificaciones/lectura')).toBe(true)
  }, 15_000)

  it('el Turista sin notificaciones ve el panel vacío', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      [OFERTA]: () => respuestaJson(200, paginaOferta([])),
    })
    const { usuario } = renderApp('/')

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones' }))

    expect(screen.getByText('No tienes notificaciones.')).toBeInTheDocument()
  })

  it('si ms-analytics no responde, la campana sigue visible con un aviso', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      [OFERTA]: () => respuestaJson(200, paginaOferta([])),
      [CAMPANA]: () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE'),
    })
    const { usuario } = renderApp('/')

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones' }))

    expect(await screen.findByText('Las notificaciones no responden en este momento.')).toBeInTheDocument()
  })

  it('el Administrador no tiene campana', async () => {
    sessionStorage.setItem('samaria360.token', 'token-admin')
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_ADMIN),
      [OFERTA]: () => respuestaJson(200, paginaOferta([])),
    })
    renderApp('/')

    await screen.findByRole('button', { name: /Menú de usuario/ })
    await waitFor(() => expect(screen.queryByRole('button', { name: /Notificaciones/ })).not.toBeInTheDocument())
    expect(solicitudes.some((s) => s.url.startsWith('/api/v1/analytics'))).toBe(false)
  })
})
