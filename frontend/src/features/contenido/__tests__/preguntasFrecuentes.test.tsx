import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  PREGUNTA_ACCESIBLES,
  PREGUNTA_CANCELAR,
  PREGUNTA_TOUR_CAFE,
  USUARIO_TURISTA,
  listaElementos,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const FAQ = 'GET /api/v1/catalogo/faq'
const PREGUNTAS = [PREGUNTA_ACCESIBLES, PREGUNTA_CANCELAR, PREGUNTA_TOUR_CAFE]

function montar(manejador = () => respuestaJson(200, listaElementos(PREGUNTAS))) {
  const simulado = simularFetch({ [FAQ]: manejador })
  return { ...simulado, ...renderApp('/preguntas-frecuentes') }
}

/** Títulos de las preguntas visibles (los h2 del contenido principal; el pie tiene los suyos). */
const titulosVisibles = () =>
  within(screen.getByRole('main'))
    .queryAllByRole('heading', { level: 2 })
    .map((encabezado) => encabezado.textContent?.replace(/[+−]$/, ''))

describe('WF-13, sección pública de preguntas frecuentes (RF26)', () => {
  it('se llega desde el pie y muestra todas las preguntas en el orden del servidor, cerradas', async () => {
    simularFetch({
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])),
      [FAQ]: () => respuestaJson(200, listaElementos(PREGUNTAS)),
    })
    const { usuario } = renderApp('/')
    await usuario.click(within(screen.getByRole('contentinfo')).getByRole('link', { name: 'Preguntas frecuentes' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Preguntas frecuentes' })).toBeInTheDocument()
    expect(ubicacionActual()).toBe('/preguntas-frecuentes')
    await waitFor(() =>
      expect(titulosVisibles()).toEqual([PREGUNTA_ACCESIBLES.titulo, PREGUNTA_CANCELAR.titulo, PREGUNTA_TOUR_CAFE.titulo]),
    )
    for (const boton of screen.getAllByRole('button', { expanded: false })) {
      expect(boton).toHaveAttribute('aria-controls')
    }
    expect(screen.queryByText(PREGUNTA_CANCELAR.cuerpo)).not.toBeVisible()
  })

  it('el acordeón abre y cierra cada pregunta; se pueden abrir varias', async () => {
    const { usuario } = montar()
    const cancelar = await screen.findByRole('button', { name: /Hasta cuándo puedo cancelar/ })

    await usuario.click(cancelar)
    expect(cancelar).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText(PREGUNTA_CANCELAR.cuerpo)).toBeVisible()

    const cafe = screen.getByRole('button', { name: /Tour de Café/ })
    await usuario.click(cafe)
    expect(screen.getByText(PREGUNTA_CANCELAR.cuerpo)).toBeVisible()
    expect(screen.getByText(PREGUNTA_TOUR_CAFE.cuerpo)).toBeVisible()

    await usuario.click(cancelar)
    expect(cancelar).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByText(PREGUNTA_CANCELAR.cuerpo)).not.toBeVisible()
  })

  it('la búsqueda filtra en tiempo real, sin tildes, en título o cuerpo, y anuncia el número de resultados (RF47)', async () => {
    const { usuario, solicitudes } = montar()
    const buscador = await screen.findByRole('searchbox', { name: 'Buscar' })

    await usuario.type(buscador, 'cafe')
    expect(titulosVisibles()).toEqual([PREGUNTA_TOUR_CAFE.titulo])
    expect(screen.getByRole('status')).toHaveTextContent('Se muestran 1 de 3 preguntas.')

    await usuario.clear(buscador)
    await usuario.type(buscador, 'HORAS')
    expect(titulosVisibles()).toEqual([PREGUNTA_CANCELAR.titulo])

    await usuario.clear(buscador)
    expect(titulosVisibles()).toHaveLength(3)
    // El filtro es del cliente: la lista se pidió una sola vez (DEC-62).
    expect(solicitudes.filter((s) => s.url.startsWith('/api/v1/catalogo/faq'))).toHaveLength(1)
  })

  it('sin coincidencias lo dice y «Borrar búsqueda» devuelve la lista y el foco al buscador', async () => {
    const { usuario } = montar()
    const buscador = await screen.findByRole('searchbox', { name: 'Buscar' })

    await usuario.type(buscador, 'parqueadero')
    expect(titulosVisibles()).toEqual([])
    expect(screen.getByRole('status')).toHaveTextContent('No hay preguntas con «parqueadero».')

    await usuario.click(screen.getByRole('button', { name: 'Borrar búsqueda' }))
    expect(buscador).toHaveValue('')
    expect(buscador).toHaveFocus()
    expect(titulosVisibles()).toHaveLength(3)
  })

  it('sin preguntas muestra el estado vacío, sin buscador', async () => {
    montar(() => respuestaJson(200, listaElementos([])))

    expect(await screen.findByText('Todavía no hay preguntas frecuentes.')).toBeInTheDocument()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
  })

  it('un error de red muestra el aviso y «Reintentar» vuelve a pedir la lista', async () => {
    let intentos = 0
    const { usuario } = montar(() => {
      intentos += 1
      if (intentos === 1) throw new TypeError('Failed to fetch')
      return respuestaJson(200, listaElementos(PREGUNTAS))
    })

    const aviso = await screen.findByRole('alert')
    expect(aviso).toHaveTextContent('No fue posible conectar con el servidor. Revisa tu conexión.')
    await usuario.click(within(aviso).getByRole('button', { name: 'Reintentar' }))

    await waitFor(() => expect(titulosVisibles()).toHaveLength(3))
  })

  it('un 503 muestra que las preguntas no están disponibles', async () => {
    montar(() => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Las preguntas frecuentes no están disponibles')
  })

  it('GET /faq y GET /avisos salen sin Authorization aun con la sesión iniciada (DEC-47)', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    const { solicitudes } = simularFetch({
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_TURISTA),
      [FAQ]: () => respuestaJson(200, listaElementos(PREGUNTAS)),
    })
    renderApp('/preguntas-frecuentes')

    await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })
    await waitFor(() => expect(titulosVisibles()).toHaveLength(3))
    const publicas = solicitudes.filter((s) => s.url === '/api/v1/catalogo/faq' || s.url === '/api/v1/catalogo/avisos')
    expect(publicas.map((s) => s.url).sort()).toEqual(['/api/v1/catalogo/avisos', '/api/v1/catalogo/faq'])
    for (const solicitud of publicas) expect(solicitud.cabeceras.Authorization).toBeUndefined()
    // La de la sesión sí lleva el token.
    expect(solicitudes.find((s) => s.url === '/api/v1/auth/me')?.cabeceras.Authorization).toBe('Bearer token-turista')
  })

  it('en inglés traduce la interfaz; las preguntas siguen como se escribieron (DEC-69)', async () => {
    const { usuario } = montar()
    await screen.findByRole('searchbox', { name: 'Buscar' })

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Frequently asked questions' })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Hasta cuándo puedo cancelar/ })).toBeInTheDocument()
  })
})
