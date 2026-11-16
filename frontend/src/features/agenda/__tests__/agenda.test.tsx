import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  USUARIO_TURISTA,
  eventoAgenda,
  paginaEventos,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

/** WF-26 «Agenda de eventos» (CU «Consultar agenda de eventos», RF25; DEC-239, DEC-246, DEC-247, DEC-249). */
const AGENDA = 'GET /api/v1/catalogo/eventos'
const LENTO = { timeout: 15_000 }
const plano = (texto: string | null) => (texto ?? '').replace(/\s+/g, ' ')

describe('WF-26 «Agenda de eventos»', () => {
  it('se llega desde el pie y se pide sin token aunque haya sesión (DEC-47)', async () => {
    sessionStorage.setItem('samaria360.token', 'token-guardado')
    const { solicitudes } = simularFetch({
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_TURISTA),
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])),
      [AGENDA]: () => respuestaJson(200, paginaEventos([eventoAgenda()])),
    })
    const { usuario } = renderApp('/')

    const pie = screen.getByRole('contentinfo')
    await usuario.click(within(pie).getByRole('link', { name: 'Agenda de eventos' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Agenda de eventos' }, LENTO)).toBeInTheDocument()
    await screen.findByRole('heading', { name: 'Fiestas del Mar' })
    expect(ubicacionActual()).toBe('/agenda')
    const consulta = solicitudes.find((s) => s.url.startsWith('/api/v1/catalogo/eventos'))!
    expect(consulta.url).toBe('/api/v1/catalogo/eventos?pagina=0')
    expect(consulta.cabeceras.Authorization).toBeUndefined()
  })

  it('muestra los eventos en el orden del servidor, con fechas de día sin corrimiento y la descripción con sus saltos', async () => {
    const despues = eventoAgenda({
      id: '6b3000000000000000000002',
      nombre: 'Festival de Cine',
      fechaInicio: '2026-12-01',
      fechaFin: '2026-12-01',
      ubicacion: 'Teatro Santa Marta',
      descripcion: 'Proyecciones al aire libre.',
    })
    simularFetch({ [AGENDA]: () => respuestaJson(200, paginaEventos([eventoAgenda(), despues])) })
    renderApp('/agenda')

    const lista = await screen.findByRole('list', { name: 'Eventos de la agenda' }, LENTO)
    const tarjetas = within(lista).getAllByRole('article')
    expect(tarjetas.map((tarjeta) => within(tarjeta).getByRole('heading').textContent)).toEqual([
      'Fiestas del Mar',
      'Festival de Cine',
    ])
    expect(screen.getByText('2 eventos próximos')).toBeInTheDocument()
    const primera = within(tarjetas[0])
    expect(plano(primera.getByText(/22 de nov de 2026/).textContent)).toBe('20 a 22 de nov de 2026')
    expect(primera.getByText('Bahía de Santa Marta')).toBeInTheDocument()
    expect(primera.getByText(/Regatas y desfile náutico/).textContent).toBe('Regatas y desfile náutico.\nEntrada libre.')
    expect(within(tarjetas[1]).getByText('1 de dic de 2026')).toBeInTheDocument()
    expect(tarjetas[1].textContent).toContain('dic')
  })

  it('sin eventos vigentes lo dice y ofrece explorar (FA1)', async () => {
    simularFetch({ [AGENDA]: () => respuestaJson(200, paginaEventos([])) })
    renderApp('/agenda')

    expect(await screen.findByText('No hay eventos próximos en la agenda.', {}, LENTO)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Explorar la oferta turística' })).toHaveAttribute('href', '/explorar')
  })

  it('pagina en la URL (desde 1); una página fuera de rango vuelve a la última (FA2) y una inválida se lee como 1 (FA3)', async () => {
    const elementos = Array.from({ length: 10 }, (_, i) =>
      eventoAgenda({ id: `6b30000000000000000000${String(i).padStart(2, '0')}`, nombre: `Evento ${i}` }),
    )
    const { solicitudes } = simularFetch({
      [AGENDA]: (solicitud) => {
        const pagina = Number(new URL(solicitud.url, 'http://x').searchParams.get('pagina'))
        return respuestaJson(200, paginaEventos(pagina === 0 ? elementos : pagina === 1 ? [eventoAgenda()] : [], pagina, 11))
      },
    })
    const { usuario } = renderApp('/agenda?pagina=9')

    await waitFor(() => expect(ubicacionActual()).toBe('/agenda?pagina=2'), LENTO)
    await screen.findByRole('heading', { name: 'Fiestas del Mar' })
    await usuario.click(screen.getByRole('button', { name: /Página 1/ }))
    await waitFor(() => expect(ubicacionActual()).toBe('/agenda'))
    expect(solicitudes.map((s) => s.url)).toContain('/api/v1/catalogo/eventos?pagina=8')
    expect(solicitudes.some((s) => s.url.includes('pagina=-'))).toBe(false)
  })

  it('una página inválida en la URL se lee como 1, sin pedir una página negativa', async () => {
    const { solicitudes } = simularFetch({ [AGENDA]: () => respuestaJson(200, paginaEventos([eventoAgenda()])) })
    renderApp('/agenda?pagina=abc')

    await screen.findByRole('heading', { name: 'Fiestas del Mar' }, LENTO)
    expect(ubicacionActual()).toBe('/agenda')
    expect(solicitudes.filter((s) => s.url.startsWith('/api/v1/catalogo/eventos')).map((s) => s.url)).toEqual([
      '/api/v1/catalogo/eventos?pagina=0',
    ])
  })

  it('el catálogo caído muestra el aviso con «Reintentar»', async () => {
    let intentos = 0
    simularFetch({
      [AGENDA]: () =>
        ++intentos === 1
          ? respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-catalog' })
          : respuestaJson(200, paginaEventos([eventoAgenda()])),
    })
    const { usuario } = renderApp('/agenda')

    expect(await screen.findByText('La agenda no responde en este momento.', {}, LENTO)).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { name: 'Fiestas del Mar' })).toBeInTheDocument()
  })

  it('en inglés', async () => {
    simularFetch({ [AGENDA]: () => respuestaJson(200, paginaEventos([eventoAgenda()])) })
    const { usuario } = renderApp('/agenda')

    await screen.findByRole('heading', { name: 'Fiestas del Mar' }, LENTO)
    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Events calendar' })).toBeInTheDocument()
    expect(plano(screen.getByText(/Nov 20/).textContent)).toBe('Nov 20 – 22, 2026')
  })
})
