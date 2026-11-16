import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { HOTEL, USUARIO_TURISTA, paginaOferta, respuestaJson, respuestaProblema, simularFetch } from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

describe('Cambio de idioma (RF03, RNF08)', () => {
  // Límite de 15 s: medida en más de 2 s (primer render del archivo, 23 pulsaciones de usuario.type y cambio de idioma).
  it('traduce los textos y conserva el correo escrito', async () => {
    simularFetch({})
    const { usuario } = renderApp('/login')

    await usuario.type(screen.getByLabelText('Correo electrónico'), 'turista@samaria360.test')
    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))

    expect(screen.getByLabelText('Email')).toHaveValue('turista@samaria360.test')
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'EN · English' })).toHaveAttribute('aria-pressed', 'true')
    expect(document.documentElement.lang).toBe('en')
  }, 15_000)

  // Límite de 15 s: medida en más de 2 s (33 pulsaciones de usuario.type y cambio de idioma).
  it('el mensaje de error mostrado también se traduce', async () => {
    simularFetch({ 'POST /api/v1/auth/login': () => respuestaProblema(401, 'CREDENCIALES_INVALIDAS') })
    const { usuario } = renderApp('/login')

    await usuario.type(screen.getByLabelText('Correo electrónico'), 'turista@samaria360.test')
    await usuario.type(screen.getByLabelText('Contraseña'), 'incorrecta')
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByText('Correo o contraseña incorrectos.')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))

    expect(screen.getByText('Incorrect email or password.')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveValue('incorrecta')
  }, 15_000)

  // RNF08 completo: «sin perder el estado de su sesión ni los datos en pantalla». Con un turista en /explorar, con
  // resultados, filtros aplicados (en la URL) y un filtro escrito sin aplicar, el cambio de idioma no recarga: la sesión
  // sigue (sin volver a consultar /me ni pedir login), la URL no cambia y los resultados y lo escrito siguen en pantalla.
  it('con sesión, conserva la sesión, los filtros aplicados, lo escrito sin aplicar y los resultados', async () => {
    sessionStorage.setItem('samaria360.token', 'token-guardado')
    const { solicitudes } = simularFetch({
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_TURISTA),
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([HOTEL], { totalElementos: 15 })),
    })
    const { usuario } = renderApp('/explorar?texto=playa&accesibilidad=MovilidadReducida')
    await screen.findByRole('heading', { level: 2, name: /15 lugares encontrados/ }, { timeout: 15_000 })
    await screen.findByRole('button', { name: /Ana Pérez/ })
    fireEvent.change(screen.getByLabelText('Precio máximo'), { target: { value: '150000' } })

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))

    expect(await screen.findByRole('heading', { level: 2, name: /15 places found/ })).toBeInTheDocument()
    expect(screen.getByText(HOTEL.nombre)).toBeInTheDocument()
    expect(ubicacionActual()).toBe('/explorar?texto=playa&accesibilidad=MovilidadReducida')
    expect(screen.getByLabelText('Name, area or activity')).toHaveValue('playa')
    expect(screen.getByLabelText('Reduced mobility')).toBeChecked()
    expect(screen.getByLabelText('Maximum price')).toHaveValue('150000')
    expect(screen.getByRole('button', { name: /Ana Pérez/ })).toBeInTheDocument()
    expect(sessionStorage.getItem('samaria360.token')).toBe('token-guardado')
    expect(solicitudes.filter((s) => s.url === '/api/v1/auth/me')).toHaveLength(1)
  }, 20_000)
})
