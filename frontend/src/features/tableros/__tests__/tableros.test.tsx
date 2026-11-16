import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  USUARIO_ADMIN,
  USUARIO_GESTOR,
  USUARIO_PRESTADOR,
  USUARIO_TURISTA,
  respuestaJson,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'

function sesion(usuario: object, rutas: Parameters<typeof simularFetch>[0] = {}) {
  sessionStorage.setItem('samaria360.token', 'token-guardado')
  return simularFetch({ [ME]: () => respuestaJson(200, usuario), ...rutas })
}

const GENERAL = {
  desde: '2030-02-10',
  hasta: '2030-03-11',
  reservasPorDia: [
    { dia: '2030-03-10', reservas: 2 },
    { dia: '2030-03-11', reservas: 5 },
  ],
  totalReservas: 7,
  atractivosMasConsultados: [{ id: 'a1', nombre: 'Playa Cristal', consultas: 40 }],
  visitantesPorZona: [
    { pais: 'CO', turistas: 5 },
    { pais: null, turistas: 1 },
  ],
}

describe('WF-35 «Tableros» (RF14)', () => {
  it('el Administrador ve el total, las reservas por día, el top 3 y los turistas por país, y filtra por fechas', async () => {
    const { solicitudes } = sesion(USUARIO_ADMIN, {
      'GET /api/v1/analytics/tableros/general': () => respuestaJson(200, GENERAL),
    })
    const { usuario } = renderApp('/tableros')

    expect(await screen.findByText('Reservas en el periodo')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    const top = screen.getByRole('figure', { name: 'Top 3 de atractivos más consultados' })
    expect(within(top).getByText('Playa Cristal')).toBeInTheDocument()
    expect(within(top).getByText('40 consultas')).toBeInTheDocument()
    const zonas = screen.getByRole('figure', { name: 'Turistas registrados por país' })
    expect(within(zonas).getByText('Colombia')).toBeInTheDocument()
    expect(within(zonas).getByText('Sin país')).toBeInTheDocument()
    const serie = screen.getByRole('figure', { name: 'Reservas por día' })
    expect(within(serie).getByRole('rowheader', { name: '11/03' })).toBeInTheDocument()

    await usuario.type(screen.getByLabelText('Desde'), '2030-03-01')
    await usuario.click(screen.getByRole('button', { name: 'Aplicar' }))
    expect(ubicacionActual()).toContain('desde=2030-03-01')
    expect(solicitudes.at(-1)?.url).toContain('desde=2030-03-01')
  })

  it('el Gestor también lo ve; el Turista ve «Sin permiso»', async () => {
    sesion(USUARIO_GESTOR, { 'GET /api/v1/analytics/tableros/general': () => respuestaJson(200, GENERAL) })
    const { unmount } = renderApp('/tableros')
    expect(await screen.findByRole('heading', { level: 1, name: 'Tableros' })).toBeInTheDocument()
    unmount()

    const { solicitudes } = sesion(USUARIO_TURISTA)
    renderApp('/tableros')
    expect(await screen.findByRole('heading', { level: 1, name: 'Sin permiso' })).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url.includes('/tableros/'))).toBe(false)
  })
})

describe('WF-36 «Mi panel» (RF31)', () => {
  it('el Prestador ve consultas y reservas por servicio y cambia a 30 días', async () => {
    const { solicitudes } = sesion(USUARIO_PRESTADOR, {
      'GET /api/v1/analytics/prestador/panel': (s) =>
        respuestaJson(200, {
          dias: s.url.includes('dias=30') ? 30 : 7,
          desde: '2030-03-05',
          hasta: '2030-03-11',
          servicios: [{ idServicio: 's1', nombre: 'Tour Tayrona', consultas: 12, reservas: 3 }],
        }),
    })
    const { usuario } = renderApp('/prestador/panel')

    const tabla = await screen.findByRole('table', { name: 'Consultas y reservas por servicio' })
    const fila = within(tabla).getByRole('row', { name: /Tour Tayrona/ })
    expect(within(fila).getByText('12')).toBeInTheDocument()
    expect(within(fila).getByText('3')).toBeInTheDocument()
    expect(solicitudes.at(-1)?.url).toContain('dias=7')

    await usuario.click(screen.getByRole('button', { name: 'Últimos 30 días' }))
    expect(screen.getByRole('button', { name: 'Últimos 30 días' })).toHaveAttribute('aria-pressed', 'true')
    expect(solicitudes.at(-1)?.url).toContain('dias=30')
  })
})

describe('WF-37 «Reporte de afluencia» (RF64)', () => {
  it('el Gestor ve la afluencia por atractivo y filtra por atractivo', async () => {
    const { solicitudes } = sesion(USUARIO_GESTOR, {
      'GET /api/v1/analytics/gestor/afluencia': () =>
        respuestaJson(200, {
          desde: '2030-02-10',
          hasta: '2030-03-11',
          atractivo: null,
          atractivos: [
            {
              idAtractivo: '6a0000000000000000000001',
              nombre: 'Playa Cristal',
              lecturas: 4,
              porcentajePromedio: 62.5,
              porcentajeMaximo: 90,
              ultimoNivel: 'Alto',
              ultimaLectura: '2030-03-11T15:00:00Z',
            },
          ],
          porDia: [{ dia: '2030-03-11', porcentajePromedio: 62.5, porcentajeMaximo: 90, lecturas: 4 }],
          atractivosDisponibles: [{ idAtractivo: '6a0000000000000000000001', nombre: 'Playa Cristal' }],
        }),
    })
    const { usuario } = renderApp('/gestor/afluencia')

    const tabla = await screen.findByRole('table', { name: 'Afluencia por atractivo' })
    expect(within(tabla).getByText('62.5 %')).toBeInTheDocument()
    expect(within(tabla).getByText('Afluencia alta')).toBeInTheDocument()

    await usuario.selectOptions(screen.getByLabelText('Atractivo'), '6a0000000000000000000001')
    await usuario.click(screen.getByRole('button', { name: 'Aplicar' }))
    expect(solicitudes.at(-1)?.url).toContain('atractivo=6a0000000000000000000001')
  })
})

describe('diaCorto', () => {
  it('pone el día primero en español y el mes primero en inglés', async () => {
    const { diaCorto } = await import('../TableroGeneralPage')
    expect(diaCorto('2030-03-11', 'es')).toBe('11/03')
    expect(diaCorto('2030-03-11', 'en')).toBe('03/11')
  })
})
