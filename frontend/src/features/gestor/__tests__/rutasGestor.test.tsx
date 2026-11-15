import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  ID_ATRACTIVO,
  USUARIO_ADMIN,
  USUARIO_GESTOR,
  USUARIO_PRESTADOR,
  USUARIO_TURISTA,
  paginaAtractivos,
  paginaEventos,
  paginaOferta,
  respuestaJson,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

/** C6 (DEC-220, DEC-233): solo el Gestor ve su sección del menú y abre sus rutas. */
const ME = 'GET /api/v1/auth/me'
const RUTAS = [
  '/gestor/atractivos',
  '/gestor/atractivos/nuevo',
  `/gestor/atractivos/${ID_ATRACTIVO}`,
  // WF-25 (DEC-245, DEC-251).
  '/gestor/eventos',
  '/gestor/eventos/nuevo',
]
const LENTO = { timeout: 15_000 }

function conSesion(usuario: object) {
  sessionStorage.setItem('samaria360.token', 'token-guardado')
  return simularFetch({
    [ME]: () => respuestaJson(200, usuario),
    'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])),
    'GET /api/v1/catalogo/gestor/atractivos': () => respuestaJson(200, paginaAtractivos([])),
    'GET /api/v1/catalogo/gestor/eventos': () => respuestaJson(200, paginaEventos([])),
  })
}

describe('Rutas y menú del Gestor (C6)', () => {
  describe.each([
    ['Turista', USUARIO_TURISTA],
    ['Prestador', USUARIO_PRESTADOR],
    ['Administrador', USUARIO_ADMIN],
  ])('%s', (_rol, usuarioActual) => {
    it.each(RUTAS)('en %s ve «Sin permiso» y no llama a las APIs del Gestor', async (ruta) => {
      const { solicitudes } = conSesion(usuarioActual)
      renderApp(ruta)

      expect(await screen.findByRole('heading', { name: 'Sin permiso' }, LENTO)).toBeInTheDocument()
      expect(solicitudes.some((s) => s.url.startsWith('/api/v1/catalogo/gestor'))).toBe(false)
    })

    it('su menú no tiene la sección del Gestor', async () => {
      conSesion(usuarioActual)
      const { usuario } = renderApp('/')
      await usuario.click(await screen.findByRole('button', { name: `Menú de usuario de ${usuarioActual.nombreVisible}` }, LENTO))
      const menu = within(screen.getByRole('menu'))
      expect(menu.queryByRole('menuitem', { name: 'Atractivos' })).not.toBeInTheDocument()
      expect(menu.queryByRole('menuitem', { name: 'Registrar atractivo' })).not.toBeInTheDocument()
      expect(menu.queryByRole('menuitem', { name: 'Eventos' })).not.toBeInTheDocument()
      expect(menu.queryByRole('menuitem', { name: 'Registrar evento' })).not.toBeInTheDocument()
    })
  })

  it('el menú del Gestor tiene exactamente sus atractivos, sus eventos y «Cerrar sesión»', async () => {
    conSesion(USUARIO_GESTOR)
    const { usuario } = renderApp('/')
    await usuario.click(await screen.findByRole('button', { name: `Menú de usuario de ${USUARIO_GESTOR.nombreVisible}` }, LENTO))

    const menu = within(screen.getByRole('menu'))
    expect(menu.getAllByRole('menuitem').map((opcion) => opcion.textContent)).toEqual([
      'Atractivos',
      'Registrar atractivo',
      'Eventos',
      'Registrar evento',
      'Tableros',
      'Reporte de afluencia',
      'Cerrar sesión',
    ])
    expect(menu.getByRole('menuitem', { name: 'Atractivos' })).toHaveAttribute('href', '/gestor/atractivos')
    expect(menu.getByRole('menuitem', { name: 'Registrar atractivo' })).toHaveAttribute('href', '/gestor/atractivos/nuevo')
    expect(menu.getByRole('menuitem', { name: 'Eventos' })).toHaveAttribute('href', '/gestor/eventos')
    expect(menu.getByRole('menuitem', { name: 'Registrar evento' })).toHaveAttribute('href', '/gestor/eventos/nuevo')
    // DEC-308: RF14 y RF64.
    expect(menu.getByRole('menuitem', { name: 'Tableros' })).toHaveAttribute('href', '/tableros')
    expect(menu.getByRole('menuitem', { name: 'Reporte de afluencia' })).toHaveAttribute('href', '/gestor/afluencia')
  })

  it('el Gestor abre su panel', async () => {
    conSesion(USUARIO_GESTOR)
    renderApp('/gestor/atractivos')
    expect(await screen.findByRole('heading', { level: 1, name: 'Atractivos' }, LENTO)).toBeInTheDocument()
  })

  it('el Gestor abre su panel de eventos y el registro (WF-25)', async () => {
    conSesion(USUARIO_GESTOR)
    const { usuario } = renderApp('/gestor/eventos')
    expect(await screen.findByRole('heading', { level: 1, name: 'Eventos' }, LENTO)).toBeInTheDocument()
    await usuario.click(screen.getByRole('link', { name: 'Registrar evento' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Registrar evento' }, LENTO)).toBeInTheDocument()
  })

  it.each(RUTAS)('sin sesión, %s lleva al login', async (ruta) => {
    simularFetch({})
    renderApp(ruta)
    await waitFor(() => expect(ubicacionActual()).toBe('/login'))
  })
})
