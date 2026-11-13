import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  USUARIO_GESTOR,
  USUARIO_TURISTA,
  paginaOferta,
  respuestaJson,
  simularFetch,
} from '../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../test/renderApp'

const CATALOGO = { 'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])) }

/** Elementos comunes de docs/wireframes/README.md, versión 2. */
describe('Pantallas de la plataforma (WF-04 a WF-10)', () => {
  it('el encabezado tiene Búsqueda, Servicios y Asistente IA, y ya no el icono de búsqueda', () => {
    simularFetch(CATALOGO)
    renderApp('/')

    const encabezado = screen.getByRole('banner')
    const navegacion = within(encabezado).getByRole('navigation', { name: 'Navegación principal' })
    expect(within(navegacion).getByRole('link', { name: 'Búsqueda' })).toHaveAttribute('href', '/explorar')
    expect(within(navegacion).getByRole('button', { name: /^Servicios/ })).toHaveAttribute('aria-disabled', 'true')
    expect(within(navegacion).getByRole('button', { name: /^Asistente IA/ })).toHaveAttribute('aria-disabled', 'true')
    // Sin sesión, el prototipo agrega «Inicio de sesión» (los wireframes solo dibujan el estado con sesión).
    expect(within(navegacion).getByRole('link', { name: 'Inicio de sesión' })).toBeInTheDocument()
    // La v1 tenía un icono de búsqueda deshabilitado junto a ES / EN; la v2 no.
    expect(within(encabezado).queryByRole('button', { name: /^Buscar/ })).not.toBeInTheDocument()
    expect(within(encabezado).getByRole('group', { name: 'Idioma' })).toBeInTheDocument()
  })

  it('el pie tiene las cuatro columnas de la v2 y el texto inferior', () => {
    simularFetch(CATALOGO)
    renderApp('/')

    const pie = screen.getByRole('contentinfo')
    const columna = (nombre: string) => within(pie).getByRole('region', { name: nombre })
    expect(within(pie).getAllByRole('region').map((region) => within(region).getByRole('heading').textContent)).toEqual([
      'Explorar',
      'Municipio',
      'Prestadores',
      'Legal y Soporte',
    ])
    expect(within(columna('Municipio')).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Noticias (No disponible)',
      'Agenda de eventos',
    ])
    // DEC-246: «Agenda de eventos» lleva a la agenda pública (WF-26).
    expect(within(columna('Municipio')).getByRole('link', { name: 'Agenda de eventos' })).toHaveAttribute('href', '/agenda')
    // DEC-270: «Registrar servicio» lleva al registro del Prestador (WF-08); sin sesión de Gestor no hay «Panel del gestor».
    expect(within(columna('Prestadores')).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Registrar servicio',
    ])
    expect(within(columna('Prestadores')).getByRole('link', { name: 'Registrar servicio' })).toHaveAttribute(
      'href',
      '/prestador/servicios/nuevo',
    )
    // RF46 y RF26 (DEC-76): los tres enlaces ya tienen destino.
    const legal = within(columna('Legal y Soporte'))
    expect(legal.getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Términos y condiciones',
      'Políticas de privacidad',
      'Preguntas frecuentes',
    ])
    expect(legal.getByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/terminos')
    expect(legal.getByRole('link', { name: 'Políticas de privacidad' })).toHaveAttribute('href', '/terminos#privacidad')
    expect(legal.getByRole('link', { name: 'Preguntas frecuentes' })).toHaveAttribute('href', '/preguntas-frecuentes')
    expect(within(pie).getByText('© 2026 Alcaldía de Santa Marta · Samaria360')).toBeInTheDocument()
    expect(within(pie).getByText(/^Prototipo académico/)).toBeInTheDocument()
  })

  // DEC-270: «Panel del gestor» (WF-24, DEC-233) solo aparece con la sesión de un Gestor.
  it.each([
    ['Gestor', USUARIO_GESTOR, ['Registrar servicio', 'Panel del gestor']],
    ['Turista', USUARIO_TURISTA, ['Registrar servicio']],
  ])('con la sesión de un %s, la columna Prestadores muestra %j', async (_rol, usuarioSesion, esperados) => {
    sessionStorage.setItem('samaria360.token', 'token-de-prueba')
    simularFetch({ ...CATALOGO, 'GET /api/v1/auth/me': () => respuestaJson(200, usuarioSesion) })
    renderApp('/')

    const prestadores = within(screen.getByRole('contentinfo')).getByRole('region', { name: 'Prestadores' })
    await waitFor(
      () => expect(within(prestadores).getAllByRole('listitem').map((li) => li.textContent)).toEqual(esperados),
      { timeout: 15_000 },
    )
    if (esperados.includes('Panel del gestor')) {
      expect(within(prestadores).getByRole('link', { name: 'Panel del gestor' })).toHaveAttribute('href', '/gestor/atractivos')
    }
  }, 20_000)

  it.each([
    ['Alojamiento', 'Alojamiento'],
    ['Gastronomía', 'Gastronomia'],
    ['Tours', 'Tour'],
    ['Alquiler de equipos', 'AlquilerEquipo'],
  ])('«%s» del pie lleva a la exploración con la categoría %s', async (etiqueta, categoria) => {
    simularFetch(CATALOGO)
    const { usuario } = renderApp('/')

    const explorar = within(screen.getByRole('contentinfo')).getByRole('region', { name: 'Explorar' })
    await usuario.click(within(explorar).getByRole('link', { name: etiqueta }))

    expect(ubicacionActual()).toBe(`/explorar?categoria=${categoria}`)
  })
})

// Decisión del equipo (diferencia con WF-04 v2): sin botón flotante del asistente; RF04 queda en la navegación.
describe('Sin botón flotante del asistente', () => {
  it.each(['/', '/explorar', '/login'])('no aparece en %s', async (ruta) => {
    simularFetch(CATALOGO)
    renderApp(ruta)
    // /explorar se carga de forma diferida: se espera a la página antes de comprobar.
    await screen.findByRole('main')
    if (ruta === '/explorar') await screen.findByRole('heading', { level: 1, name: /Exploración de Atractivos/ })

    expect(screen.queryByRole('button', { name: /Asistente virtual IA/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/Asistente virtual IA/)).not.toBeInTheDocument()
    expect(document.querySelector('.boton-asistente')).toBeNull()
    const navegacion = screen.queryByRole('navigation', { name: 'Navegación principal' })
    if (ruta === '/login') {
      expect(navegacion).not.toBeInTheDocument()
    } else {
      expect(within(navegacion!).getByRole('button', { name: /^Asistente IA/ })).toHaveAttribute('aria-disabled', 'true')
    }
  })
})

describe('Pantallas de acceso (WF-01 a WF-03)', () => {
  it('el encabezado reducido solo tiene el logo y ES / EN', () => {
    simularFetch({})
    renderApp('/login')

    const encabezado = screen.getByRole('banner')
    expect(within(encabezado).getByRole('link', { name: 'Samaria360, ir al inicio' })).toHaveAttribute('href', '/')
    expect(within(encabezado).getByRole('group', { name: 'Idioma' })).toBeInTheDocument()
    expect(within(encabezado).queryByRole('navigation')).not.toBeInTheDocument()
    expect(within(encabezado).getAllByRole('link')).toHaveLength(1)
  })

  it('el pie reducido tiene los derechos, Términos y Privacidad con destino y Ayuda pendiente, sin las columnas', async () => {
    simularFetch({})
    const { usuario } = renderApp('/login')

    const pie = screen.getByRole('contentinfo')
    expect(within(pie).getByText('© 2026 Samaria360 · Plataforma turística verificada')).toBeInTheDocument()
    expect(within(pie).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Términos y condiciones',
      'Políticas de privacidad',
      'Ayuda / Soporte (No disponible)',
    ])
    expect(within(pie).getByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/terminos')
    expect(within(pie).getByRole('link', { name: 'Políticas de privacidad' })).toHaveAttribute('href', '/terminos#privacidad')
    expect(within(pie).queryByRole('region')).not.toBeInTheDocument()
    expect(within(pie).getByText(/^Prototipo académico/)).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Inglés' }))
    expect(within(pie).getByText('© 2026 Samaria360 · Verified tourism platform')).toBeInTheDocument()
    expect(within(pie).getByText('Help / Support')).toBeInTheDocument()
  })

  it('conserva el enlace para saltar al contenido', () => {
    simularFetch({})
    renderApp('/login')

    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveAttribute('href', '#contenido')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido')
  })
})
