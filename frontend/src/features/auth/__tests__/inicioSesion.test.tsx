import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  SESION_TURISTA,
  USUARIO_TURISTA,
  paginaOferta,
  respuestaHtml,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const LOGIN = 'POST /api/v1/auth/login'
const ME = 'GET /api/v1/auth/me'

async function llenarYEnviar(usuario: ReturnType<typeof renderApp>['usuario'], correo: string, contrasena: string) {
  await usuario.type(screen.getByLabelText('Correo electrónico'), correo)
  await usuario.type(screen.getByLabelText('Contraseña'), contrasena)
  await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
}

describe('Inicio de sesión (RF15)', () => {
  // Límite de 15 s: medida en más de 2 s (primer render del archivo, 34 pulsaciones de usuario.type y navegación al inicio).
  it('un login exitoso redirige al inicio y muestra el avatar con las iniciales', async () => {
    const { solicitudes } = simularFetch({
      [LOGIN]: () => respuestaJson(200, SESION_TURISTA),
      [ME]: () => respuestaJson(200, USUARIO_TURISTA),
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])),
    })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'Samaria360*')

    // El inicio es la pantalla WF-04 (antes, el inicio provisional con «Hola, Ana Pérez»).
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Encuentra alojamiento, gastronomía y tours en Santa Marta' }),
    ).toBeInTheDocument()
    expect(ubicacionActual()).toBe('/')
    expect(screen.getByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toHaveTextContent('AP')
    expect(sessionStorage.getItem('samaria360.token')).toBe(SESION_TURISTA.token)

    const login = solicitudes.find((s) => s.url === '/api/v1/auth/login')
    expect(login?.cuerpo).toEqual({ correo: 'turista@samaria360.test', contrasena: 'Samaria360*' })
    const me = solicitudes.find((s) => s.url === '/api/v1/auth/me')
    expect(me?.cabeceras.Authorization).toBe(`Bearer ${SESION_TURISTA.token}`)
  }, 15_000)

  // Límite de 15 s: medida en más de 2 s (33 pulsaciones de usuario.type).
  it('un 401 muestra el mensaje de credenciales inválidas', async () => {
    simularFetch({ [LOGIN]: () => respuestaProblema(401, 'CREDENCIALES_INVALIDAS') })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'incorrecta')

    const alerta = screen.getByRole('alert')
    expect(await within(alerta).findByText('Correo o contraseña incorrectos.')).toBeInTheDocument()
    expect(alerta).toHaveAttribute('aria-live', 'assertive')
    expect(sessionStorage.getItem('samaria360.token')).toBeNull()
  }, 15_000)

  // Límite de 15 s: medida en más de 2 s (33 pulsaciones de usuario.type).
  it('un 423 muestra la hora de desbloqueo en la hora local del navegador', async () => {
    const bloqueadoHasta = '2026-10-11T15:45:00Z'
    simularFetch({ [LOGIN]: () => respuestaProblema(423, 'CUENTA_BLOQUEADA', { bloqueadoHasta }) })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'incorrecta')

    // Misma conversión que la app (Intl con la zona horaria del entorno): la prueba no depende de dónde corre.
    const hora = new Intl.DateTimeFormat('es', { timeStyle: 'short' }).format(new Date(bloqueadoHasta))
    expect(await screen.findByText(/Tu cuenta está bloqueada/)).toHaveTextContent(hora)
  }, 15_000)

  // Límite de 15 s: medida en más de 2 s (34 pulsaciones de usuario.type).
  it('un 403 muestra el mensaje de cuenta inactiva', async () => {
    simularFetch({ [LOGIN]: () => respuestaProblema(403, 'CUENTA_INACTIVA') })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'Samaria360*')

    expect(await screen.findByText(/Tu cuenta no está activa/)).toBeInTheDocument()
  }, 15_000)

  // DEC-273: con la contraseña correcta, el prestador rechazado ve la justificación del Administrador, como texto.
  it('un 403 CUENTA_RECHAZADA con motivo lo muestra como texto', async () => {
    simularFetch({
      [LOGIN]: () => respuestaProblema(403, 'CUENTA_RECHAZADA', { motivo: 'El RNT <b>no</b> está vigente' }),
    })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'prestador@samaria360.test', 'Samaria360*')

    const alerta = await screen.findByRole('alert')
    await screen.findByText(/Motivo: «El RNT <b>no<\/b> está vigente»/)
    expect(alerta.querySelector('b')).toBeNull()
  }, 15_000)

  it('un 403 CUENTA_RECHAZADA sin motivo conserva el mensaje genérico', async () => {
    simularFetch({ [LOGIN]: () => respuestaProblema(403, 'CUENTA_RECHAZADA') })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'prestador@samaria360.test', 'Samaria360*')

    expect(await screen.findByText(/^El administrador rechazó la solicitud de tu cuenta\. Comunícate/)).toBeInTheDocument()
  }, 15_000)

  // Límite de 15 s: medida en más de 2 s (23 pulsaciones de usuario.type).
  it('un 400 muestra el mensaje de datos inválidos', async () => {
    simularFetch({ [LOGIN]: () => respuestaProblema(400, 'DATOS_INVALIDOS') })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'no-es-correo', 'Samaria360*')

    expect(await screen.findByText(/Revisa los datos/)).toBeInTheDocument()
  }, 15_000)

  it.each([
    ['502 de nginx', () => respuestaHtml(502)],
    ['503 SERVICIO_NO_DISPONIBLE', () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-auth' })],
    ['504 de nginx', () => respuestaHtml(504)],
  ])('un %s muestra que el servicio no está disponible', async (_caso, respuesta) => {
    simularFetch({ [LOGIN]: respuesta })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'Samaria360*')

    expect(
      await screen.findByText('El servicio no está disponible por el momento. Inténtalo de nuevo en unos minutos.'),
    ).toBeInTheDocument()
  })

  it('un 500 muestra el mensaje genérico (defecto del servidor, no una caída)', async () => {
    simularFetch({ [LOGIN]: () => respuestaProblema(500, 'ERROR_INTERNO') })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'Samaria360*')

    expect(await screen.findByText('Ocurrió un error inesperado. Inténtalo de nuevo más tarde.')).toBeInTheDocument()
  })

  it('un error de red muestra su propio mensaje', async () => {
    simularFetch({
      [LOGIN]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    const { usuario } = renderApp('/login')

    await llenarYEnviar(usuario, 'turista@samaria360.test', 'Samaria360*')

    expect(await screen.findByText(/No fue posible conectar con el servidor/)).toBeInTheDocument()
  })

  it('con campos vacíos no llama al backend y marca los campos', async () => {
    const { fetchSimulado } = simularFetch({})
    const { usuario } = renderApp('/login')

    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    const correo = screen.getByLabelText('Correo electrónico')
    expect(correo).toHaveAttribute('aria-invalid', 'true')
    expect(correo).toHaveAccessibleDescription('Este campo es obligatorio.')
    expect(correo).toHaveFocus()
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  // Mejora de diseño (2026-11-13): el ojo muestra y oculta la contraseña; al enviar vuelve a ocultarse.
  it('el botón «Mostrar contraseña» alterna la visibilidad y la oculta otra vez al enviar', async () => {
    simularFetch({ [LOGIN]: () => respuestaProblema(401, 'CREDENCIALES_INVALIDAS') })
    const { usuario } = renderApp('/login')

    const contrasena = screen.getByLabelText('Contraseña')
    const ojo = screen.getByRole('button', { name: 'Mostrar contraseña' })
    expect(contrasena).toHaveAttribute('type', 'password')
    expect(ojo).toHaveAttribute('aria-pressed', 'false')
    expect(ojo).toHaveAttribute('aria-controls', 'contrasena')

    await usuario.type(contrasena, 'abc')
    await usuario.click(ojo)
    expect(contrasena).toHaveAttribute('type', 'text')
    expect(ojo).toHaveAttribute('aria-pressed', 'true')
    expect(contrasena).toHaveValue('abc')

    await usuario.click(ojo)
    expect(contrasena).toHaveAttribute('type', 'password')

    await usuario.click(ojo)
    await usuario.type(screen.getByLabelText('Correo electrónico'), 'a@b.co')
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByText('Correo o contraseña incorrectos.')).toBeInTheDocument()
    expect(contrasena).toHaveAttribute('type', 'password')
    expect(ojo).toHaveAttribute('aria-pressed', 'false')
  }, 15_000)

  // CU Registrarse (RF01, RF02): «Registrarse» dejó de estar deshabilitado y lleva a /registro (WF-02, WF-03). La
  // recuperación de contraseña (RF34) sigue pendiente.
  it('«Registrarse» lleva al registro y ¿Olvidaste tu contraseña? sigue deshabilitado', () => {
    simularFetch({})
    renderApp('/login')

    const pestanas = screen.getByRole('navigation', { name: 'Acceso a la cuenta' })
    expect(within(pestanas).getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('aria-current', 'page')
    expect(within(pestanas).getByRole('link', { name: 'Registrarse' })).toHaveAttribute('href', '/registro')
    expect(screen.getByRole('button', { name: /¿Olvidaste tu contraseña\?/ })).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByText('3 intentos fallidos bloquearán la cuenta por 15 minutos.')).toBeInTheDocument()
  })

  // WF-01 v2: sin el título grande «Samaria360» (el h1 queda solo para lectores de pantalla) ni el bloque «Plataforma».
  it('sigue WF-01 v2: sin imagen ni ilustración, y con los elementos en el orden del wireframe', () => {
    simularFetch({})
    renderApp('/login')

    const principal = screen.getByRole('main')
    // WF-01 no tiene imagen: ni la foto del panel ni la ilustración (los iconos SVG de los campos no cuentan).
    expect(principal.querySelectorAll('img')).toHaveLength(0)
    expect(principal.querySelector('.escena-santa-marta, [src*="panel-login"]')).toBeNull()

    const titulo = screen.getByRole('heading', { level: 1, name: 'Iniciar sesión en Samaria360' })
    expect(titulo).toHaveClass('solo-lector')
    expect(screen.queryByRole('region', { name: 'Plataforma' })).not.toBeInTheDocument()
    expect(screen.queryByText('Explorar destinos')).not.toBeInTheDocument()

    const enOrden = [
      titulo,
      screen.getByText('Plataforma de turismo de Santa Marta'),
      // Las pestañas pasaron de tablist a enlaces (cambian de página: /login y /registro).
      screen.getByRole('navigation', { name: 'Acceso a la cuenta' }),
      screen.getByText('Accede con el correo con el que te registraste o crea una cuenta nueva.'),
      screen.getByRole('region', { name: 'Ingresa con tu cuenta' }),
    ]
    for (let i = 1; i < enOrden.length; i++) {
      expect(enOrden[i - 1].compareDocumentPosition(enOrden[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    }
  })
})
