import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import {
  CASCADA_CERRADA,
  ID_ATRACTIVO,
  USUARIO_GESTOR,
  atractivoPublico,
  elementoAtractivo,
  paginaAtractivos,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp } from '../../../test/renderApp'
import type { ResultadoOferta } from '../../catalogo/catalogoApi'
import { TarjetaOferta } from '../../catalogo/componentes/TarjetaOferta'

const FICHA = `GET /api/v1/catalogo/atractivos/${ID_ATRACTIVO}`
const AFLUENCIA = `GET /api/v1/catalogo/atractivos/${ID_ATRACTIVO}/afluencia`
const LENTO = { timeout: 15_000 }

const LLENO = {
  afluencia: {
    nivel: 'Alto',
    porcentaje: 100,
    ocupacion: 80,
    limite: 80,
    fecha: '2030-03-01T15:00:00Z',
    limiteAlcanzado: true,
  },
  alternativos: [
    { id: 'a2', nombre: 'Pozo Azul', categoria: 'Naturaleza', imagen: null, nivel: 'Bajo', porcentaje: 20, distanciaKm: 3.5 },
    { id: 'a3', nombre: 'Quebrada Valencia', categoria: 'Naturaleza', imagen: null, nivel: null, porcentaje: null, distanciaKm: 12 },
  ],
}

describe('Afluencia en la ficha del atractivo (RF39, RF08, RF51)', () => {
  it('muestra el nivel, la alerta del límite y los destinos alternativos', async () => {
    simularFetch({
      [FICHA]: () => respuestaJson(200, atractivoPublico()),
      [AFLUENCIA]: () => respuestaJson(200, LLENO),
    })
    renderApp(`/atractivos/${ID_ATRACTIVO}`)

    const alerta = await screen.findByRole('region', { name: 'Alerta de afluencia' }, LENTO)
    expect(await within(alerta).findByText('Afluencia alta')).toBeInTheDocument()
    expect(within(alerta).getByText(/100 % de la capacidad: 80 de 80 personas/)).toBeInTheDocument()
    expect(within(alerta).getByRole('alert')).toHaveTextContent('llegó a su límite de capacidad')
    expect(within(alerta).getByRole('link', { name: 'Ver destinos alternativos' })).toHaveAttribute('href', '#alternativos')

    const alternativos = screen.getByRole('region', { name: 'Destinos alternativos con menor afluencia' })
    expect(within(alternativos).getByRole('link', { name: 'Pozo Azul' })).toHaveAttribute('href', '/atractivos/a2')
    expect(within(alternativos).getByText(/Afluencia baja \(20 %\)/)).toBeInTheDocument()
    expect(within(alternativos).getByText(/Sin datos de afluencia hoy/)).toBeInTheDocument()
  })

  it('sin lectura del día lo dice y no muestra la alerta', async () => {
    simularFetch({
      [FICHA]: () => respuestaJson(200, atractivoPublico()),
      [AFLUENCIA]: () => respuestaJson(200, { afluencia: null, alternativos: [] }),
    })
    renderApp(`/atractivos/${ID_ATRACTIVO}`)

    const alerta = await screen.findByRole('region', { name: 'Alerta de afluencia' }, LENTO)
    expect(await within(alerta).findByText('Sin datos de afluencia hoy')).toBeInTheDocument()
    expect(within(alerta).queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('No hay destinos similares con menor afluencia en este momento.')).toBeInTheDocument()
  })
})

describe('Afluencia en la tarjeta (RF39)', () => {
  it('un atractivo con afluencia vigente muestra su nivel', () => {
    render(
      <MemoryRouter>
        <TarjetaOferta resultado={{ ...CASCADA_CERRADA, afluencia: 'Medio' } as unknown as ResultadoOferta} variante="lista" />
      </MemoryRouter>,
    )

    expect(screen.getByText('Afluencia media')).toHaveClass('nivel-afluencia--Medio')
  })
})

describe('Registrar ocupación en WF-24 (RF39)', () => {
  it('el Gestor registra la ocupación y ve el nivel calculado', async () => {
    sessionStorage.setItem('samaria360.token', 'token-guardado')
    const { solicitudes } = simularFetch({
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_GESTOR),
      'GET /api/v1/catalogo/gestor/atractivos': () => respuestaJson(200, paginaAtractivos([elementoAtractivo()])),
      [`POST /api/v1/catalogo/gestor/atractivos/${ID_ATRACTIVO}/ocupacion`]: () =>
        respuestaJson(200, { ...LLENO.afluencia, nivel: 'Medio', porcentaje: 50, ocupacion: 40, limiteAlcanzado: false }),
    })
    const { usuario } = renderApp('/gestor/atractivos')

    const campo = await screen.findByLabelText(/Personas ahora en/, {}, LENTO)
    await usuario.type(campo, '40')
    await usuario.click(screen.getByRole('button', { name: 'Registrar ocupación' }))

    expect(await screen.findByText('Afluencia media (50 %)')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'POST')?.cuerpo).toEqual({ ocupacion: 40 })
  })

  it('una ocupación inválida no se envía', async () => {
    sessionStorage.setItem('samaria360.token', 'token-guardado')
    const { solicitudes } = simularFetch({
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_GESTOR),
      'GET /api/v1/catalogo/gestor/atractivos': () => respuestaJson(200, paginaAtractivos([elementoAtractivo()])),
      [`POST /api/v1/catalogo/gestor/atractivos/${ID_ATRACTIVO}/ocupacion`]: () => respuestaProblema(400, 'DATOS_INVALIDOS'),
    })
    const { usuario } = renderApp('/gestor/atractivos')

    await usuario.type(await screen.findByLabelText(/Personas ahora en/, {}, LENTO), '-5')
    await usuario.click(screen.getByRole('button', { name: 'Registrar ocupación' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Escribe un número entero')
    expect(solicitudes.some((s) => s.metodo === 'POST')).toBe(false)
  })
})
