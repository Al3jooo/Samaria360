import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  USUARIO_ADMIN,
  USUARIO_TURISTA,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
// El inicio (WF-04) consulta los destacados del catálogo.
const CATALOGO = { 'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])) }

function sesionGuardada(token = 'token-guardado') {
  sessionStorage.setItem('samaria360.token', token)
}

describe('Sesión', () => {
  it('una sesión guardada se restaura con /me', async () => {
    sesionGuardada()
    const { solicitudes } = simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA), ...CATALOGO })
    renderApp('/')

    // Antes esperaba el título «Hola, Ana Pérez» del inicio provisional, que ya no existe.
    expect(await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toHaveTextContent('AP')
    const me = solicitudes.find((s) => s.url === '/api/v1/auth/me')
    expect(me?.cabeceras.Authorization).toBe('Bearer token-guardado')
  })

  // «/» ahora es pública: la sesión se cierra y el usuario se queda en la página, con un aviso (antes iba al login).
  it('un 401 de /me en una página pública cierra la sesión y avisa sin sacar al usuario', async () => {
    sesionGuardada()
    simularFetch({ [ME]: () => respuestaProblema(401, 'NO_AUTENTICADO'), ...CATALOGO })
    const { usuario } = renderApp('/')

    const aviso = (await screen.findByText(/Tu sesión expiró\./)).closest('[role="status"]')
    expect(aviso).not.toBeNull()
    expect(screen.getByRole('link', { name: 'Inicia sesión de nuevo' })).toHaveAttribute('href', '/login')
    expect(ubicacionActual()).toBe('/')
    expect(sessionStorage.getItem('samaria360.token')).toBeNull()
    expect(screen.queryByRole('button', { name: /Menú de usuario/ })).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Cerrar aviso' }))
    expect(screen.queryByText('Tu sesión expiró.', { exact: false })).not.toBeInTheDocument()
  })

  it('un 403 de /me (cuenta desactivada) cierra la sesión y avisa que la cuenta no está activa', async () => {
    sesionGuardada()
    simularFetch({ [ME]: () => respuestaProblema(403, 'CUENTA_INACTIVA'), ...CATALOGO })
    renderApp('/')

    expect(await screen.findByText('Tu cuenta no está activa.', { exact: false })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login')
    expect(ubicacionActual()).toBe('/')
    expect(sessionStorage.getItem('samaria360.token')).toBeNull()
  })

  it('Cerrar sesión desde el menú del avatar borra el token y vuelve al login (RF55)', async () => {
    sesionGuardada()
    simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA) })
    const { usuario } = renderApp('/')

    await usuario.click(await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' }))
    await usuario.click(screen.getByRole('menuitem', { name: 'Cerrar sesión' }))

    expect(await screen.findByLabelText('Correo electrónico')).toBeInTheDocument()
    expect(sessionStorage.getItem('samaria360.token')).toBeNull()
    expect(screen.queryByRole('button', { name: /Menú de usuario/ })).not.toBeInTheDocument()
  })

  it('el menú del avatar se abre con Enter y se cierra con Escape devolviendo el foco', async () => {
    sesionGuardada()
    simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA) })
    const { usuario } = renderApp('/')

    const avatar = await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' })
    avatar.focus()
    await usuario.keyboard('{Enter}')

    // Para un turista, la primera opción es «Mi perfil» (WF-16, DEC-92).
    const opcion = screen.getByRole('menuitem', { name: 'Mi perfil' })
    await waitFor(() => expect(opcion).toHaveFocus())
    expect(avatar).toHaveAttribute('aria-controls', screen.getByRole('menu').id)
    expect(avatar).toHaveAttribute('aria-expanded', 'true')

    await usuario.keyboard('{Escape}')

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(avatar).toHaveFocus()
    expect(avatar).toHaveAttribute('aria-expanded', 'false')
  })
})

describe('Menú del avatar', () => {
  const abrirMenu = async (usuarioActual = USUARIO_TURISTA) => {
    sesionGuardada()
    simularFetch({ [ME]: () => respuestaJson(200, usuarioActual), ...CATALOGO })
    const resultado = renderApp('/')
    const avatar = await screen.findByRole('button', { name: `Menú de usuario de ${usuarioActual.nombreVisible}` })
    return { ...resultado, avatar }
  }

  it('para un turista, «Mis reservas» va antes de «Cerrar sesión» y lleva a /mis-reservas', async () => {
    const { usuario, avatar } = await abrirMenu()
    await usuario.click(avatar)

    // DEC-92: «Mi perfil» y «Mis favoritos» van antes de «Mis reservas».
    const opciones = within(screen.getByRole('menu')).getAllByRole('menuitem')
    expect(opciones.map((opcion) => opcion.textContent)).toEqual([
      'Mi perfil',
      'Mis favoritos',
      'Mis itinerarios',
      'Mis reservas',
      'Cerrar sesión',
    ])
    // DEC-298: «Mis itinerarios» (WF-31) va con el perfil.
    expect(opciones[2]).toHaveAttribute('href', '/perfil/itinerarios')
    expect(opciones[3]).toHaveAttribute('href', '/mis-reservas')

    await usuario.click(opciones[3])
    // La navegación va en una transición: la ruta cambia cuando llega la página diferida.
    await waitFor(() => expect(ubicacionActual()).toBe('/mis-reservas'))
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('para otro rol no aparece «Mis reservas»', async () => {
    const { usuario, avatar } = await abrirMenu({ ...USUARIO_TURISTA, rol: 'Prestador', nombreVisible: 'Hotel Prueba' })
    await usuario.click(avatar)

    // DEC-161: el Prestador tiene sus propias pantallas (WF-18 y WF-08), pero no «Mis reservas»; DEC-197: su
    // historial de reservas (WF-23) y su perfil comercial (WF-22); DEC-311 y DEC-312: WF-39 y WF-40.
    const opciones = within(screen.getByRole('menu')).getAllByRole('menuitem')
    expect(opciones.map((opcion) => opcion.textContent)).toEqual([
      'Mis servicios',
      'Registrar servicio',
      'Historial de reservas',
      'Mi perfil comercial',
      'Calificaciones recibidas',
      'Mi panel',
      'Historial de modificaciones',
      'Asistentes',
      'Cerrar sesión',
    ])
    await waitFor(() => expect(opciones[0]).toHaveFocus())
  })

  it('para el Administrador, sus pantallas van antes de «Cerrar sesión» (WF-12 a WF-14, WF-19 y WF-30, DEC-70, DEC-174, DEC-294)', async () => {
    const { usuario, avatar } = await abrirMenu(USUARIO_ADMIN)
    await usuario.click(avatar)

    const opciones = within(screen.getByRole('menu')).getAllByRole('menuitem')
    expect(opciones.map((opcion) => opcion.textContent)).toEqual([
      'Administrar cuentas',
      'Aprobar servicios',
      'Preguntas frecuentes',
      'Avisos',
      'Moderación',
      'Base de conocimientos',
      'Auditoría',
      'Tableros',
      'Cerrar sesión',
    ])
    expect(opciones[0]).toHaveAttribute('href', '/admin/cuentas')
    expect(opciones[1]).toHaveAttribute('href', '/admin/servicios')
    expect(opciones[2]).toHaveAttribute('href', '/admin/preguntas-frecuentes')
    expect(opciones[3]).toHaveAttribute('href', '/admin/avisos')
    // DEC-297: la base de conocimientos del asistente (WF-32).
    // DEC-303: el panel de moderación (WF-34).
    expect(opciones[4]).toHaveAttribute('href', '/admin/moderacion')
    expect(opciones[5]).toHaveAttribute('href', '/admin/conocimiento')
    expect(opciones[6]).toHaveAttribute('href', '/admin/auditoria')
    // DEC-308: los tableros (WF-35, RF14).
    expect(opciones[7]).toHaveAttribute('href', '/tableros')
    // El foco inicial va a la primera opción, «Administrar cuentas».
    await waitFor(() => expect(opciones[0]).toHaveFocus())
  })

  it.each([USUARIO_TURISTA, { ...USUARIO_TURISTA, rol: 'Prestador', nombreVisible: 'Hotel Prueba' }])(
    'para el rol $rol no aparece ninguna pantalla del Administrador',
    async (usuarioActual) => {
      const { usuario, avatar } = await abrirMenu(usuarioActual)
      await usuario.click(avatar)
      const menu = within(screen.getByRole('menu'))
      for (const nombre of ['Administrar cuentas', 'Preguntas frecuentes', 'Avisos']) {
        expect(menu.queryByRole('menuitem', { name: nombre })).not.toBeInTheDocument()
      }
    },
  )

  it('se abre con la barra espaciadora y se cierra con un clic afuera', async () => {
    const { usuario, avatar } = await abrirMenu()
    avatar.focus()
    await usuario.keyboard(' ')
    expect(avatar).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('menu')).toBeInTheDocument()

    fireEvent.pointerDown(document.body)
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
    expect(avatar).toHaveAttribute('aria-expanded', 'false')
    expect(avatar).not.toHaveAttribute('aria-controls')
  })

  it('en el celular, un toque que quita el foco sin dárselo a la opción no cierra el menú antes del clic', async () => {
    const { usuario, avatar } = await abrirMenu()
    await usuario.click(avatar)
    const opcion = screen.getByRole('menuitem', { name: 'Mi perfil' })

    // Safari de iOS no enfoca enlaces ni botones al tocarlos: el blur llega sin relatedTarget.
    fireEvent.blur(opcion, { relatedTarget: null })
    expect(screen.getByRole('menu')).toBeInTheDocument()

    fireEvent.click(opcion)
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
  })
})
