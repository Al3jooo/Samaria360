import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  USUARIO_TURISTA,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
  type SolicitudSimulada,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const PAISES = 'GET /api/v1/auth/paises'
const REGISTRO_TURISTA = 'POST /api/v1/auth/registro/turista'
const REGISTRO_PRESTADOR = 'POST /api/v1/auth/registro/prestador'
const RECIBIDO = () => respuestaJson(202, { estado: 'RECIBIDO' })
const CODIGOS = ['US', 'ES', 'DE', 'CO']

function simular(rutas: Record<string, (s: SolicitudSimulada) => Response | Promise<Response>> = {}) {
  return simularFetch({ [PAISES]: () => respuestaJson(200, CODIGOS), ...rutas })
}

const registros = (solicitudes: SolicitudSimulada[]) => solicitudes.filter((s) => s.url.includes('/registro/'))

/** Llena el formulario de turista con datos válidos (el correo con mayúsculas, para comprobar la normalización). */
async function llenarTurista(usuario: ReturnType<typeof renderApp>['usuario']) {
  await usuario.type(screen.getByLabelText('Primer nombre'), 'Ana')
  await usuario.type(screen.getByLabelText('Primer apellido'), 'Pérez')
  await usuario.type(screen.getByLabelText('Correo electrónico'), ' Ana@X.com ')
  await usuario.type(screen.getByLabelText('Crear contraseña'), 'Clave1234')
  await usuario.selectOptions(await screen.findByRole('combobox', { name: 'País de origen' }), 'CO')
  await usuario.click(screen.getByRole('checkbox', { name: 'Cultura' }))
  await usuario.click(screen.getByRole('checkbox', { name: /Acepto la Política de Privacidad/ }))
}

describe('CU Registrarse (RF01, RF02; WF-02, WF-03)', () => {
  it('«Registrarse» del login lleva al registro, con Turista elegido y las pestañas como enlaces', async () => {
    simular()
    const { usuario } = renderApp('/login')

    await usuario.click(screen.getByRole('link', { name: 'Registrarse' }))

    expect(ubicacionActual()).toBe('/registro')
    const pestanas = screen.getByRole('navigation', { name: 'Acceso a la cuenta' })
    expect(within(pestanas).getByRole('link', { name: 'Registrarse' })).toHaveAttribute('aria-current', 'page')
    expect(within(pestanas).getByRole('link', { name: 'Iniciar sesión' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('radio', { name: 'Turista' })).toBeChecked()
    expect(screen.getByRole('heading', { name: 'Formulario – Turista' })).toBeInTheDocument()
    // Pantalla de acceso: encabezado reducido, sin navegación principal.
    expect(screen.queryByRole('navigation', { name: 'Navegación principal' })).not.toBeInTheDocument()
  })

  // El aviso de RF02 era cerrable: es información obligatoria, así que ahora es fijo (role="note", sin botón de cerrar).
  it('con Prestador de Servicios muestra WF-03 y el aviso fijo de validación pendiente', async () => {
    simular()
    const { usuario } = renderApp('/registro')

    await usuario.click(screen.getByRole('radio', { name: 'Prestador de Servicios' }))

    expect(screen.getByRole('heading', { name: 'Formulario – Prestador de Servicios' })).toBeInTheDocument()
    for (const etiqueta of ['Nombre del representante legal', 'Número RNT (Registro Nacional de Turismo)',
      'Correo corporativo', 'Teléfono', 'Crear contraseña']) {
      expect(screen.getByLabelText(etiqueta)).toBeInTheDocument()
    }
    // Decisión D2: razón social opcional y fuera del formulario, como WF-03.
    expect(screen.queryByLabelText(/Razón social/)).not.toBeInTheDocument()
    expect(screen.getByRole('note')).toHaveTextContent(
      'Tu cuenta quedará pendiente de validación hasta que un administrador la revise.',
    )
    expect(screen.queryByRole('button', { name: 'Cerrar aviso' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear cuenta de prestador' })).toBeInTheDocument()
  })

  it.each([
    ['Turista', { 'Primer nombre': 'given-name', 'Segundo nombre (opcional)': 'additional-name',
      'Primer apellido': 'family-name', 'Segundo apellido (opcional)': 'family-name', 'Correo electrónico': 'email',
      'Crear contraseña': 'new-password', 'País de origen': 'country' }],
    ['Prestador de Servicios', { 'Nombre del representante legal': 'name', 'Correo corporativo': 'email',
      'Teléfono': 'tel', 'Crear contraseña': 'new-password' }],
  ])('formulario %s: cada campo personal tiene su autocomplete (WCAG 1.3.5)', async (tipo, esperados) => {
    simular()
    const { usuario } = renderApp('/registro')
    await usuario.click(screen.getByRole('radio', { name: tipo }))

    for (const [etiqueta, valor] of Object.entries(esperados)) {
      expect(screen.getByLabelText(etiqueta), etiqueta).toHaveAttribute('autocomplete', valor)
    }
    if (tipo !== 'Turista') expect(screen.getByLabelText('Teléfono')).toHaveAttribute('type', 'tel')
  })

  it.each(['Turista', 'Prestador de Servicios'])(
    'formulario %s: la contraseña muestra «Mínimo 8 caracteres.» como ayuda que no desaparece',
    async (tipo) => {
      simular()
      const { usuario } = renderApp('/registro')
      await usuario.click(screen.getByRole('radio', { name: tipo }))

      const contrasena = screen.getByLabelText('Crear contraseña')
      expect(contrasena).not.toHaveAttribute('placeholder')
      expect(contrasena).toHaveAccessibleDescription('Mínimo 8 caracteres.')
      await usuario.type(contrasena, 'corta')
      expect(screen.getByText('Mínimo 8 caracteres.')).toBeVisible()
    },
  )

  it('las ayudas siguen conectadas cuando hay un error', async () => {
    simular()
    const { usuario } = renderApp('/registro')
    await screen.findByRole('option', { name: 'Colombia' })
    await usuario.type(screen.getByLabelText('Crear contraseña'), 'corta')

    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de turista' }))

    expect(screen.getByLabelText('Crear contraseña')).toHaveAccessibleDescription(
      'Mínimo 8 caracteres. Debe tener al menos 8 caracteres.',
    )
    expect(screen.getByRole('group', { name: 'Categorías de interés' })).toHaveAccessibleDescription(
      'Elige al menos una. Elige al menos una categoría de interés.',
    )
  })

  it('las categorías y el tipo de cuenta van en fieldset con legend; la ayuda de las categorías está conectada', async () => {
    simular()
    renderApp('/registro')

    expect(screen.getByRole('group', { name: 'Selecciona el tipo de cuenta que quieres registrar.' })).toContainElement(
      screen.getByRole('radio', { name: 'Turista' }),
    )
    const categorias = screen.getByRole('group', { name: 'Categorías de interés' })
    expect(categorias.tagName).toBe('FIELDSET')
    expect(categorias).toHaveAccessibleDescription('Elige al menos una.')
  })

  it.each(['Turista', 'Prestador de Servicios'])(
    'formulario %s: «Política de Privacidad» va al mismo destino que el pie, la sección 4 de los términos',
    async (tipo) => {
      simular()
      const { usuario } = renderApp('/registro')
      await usuario.click(screen.getByRole('radio', { name: tipo }))

      // RUTA_POLITICA_PRIVACIDAD = /terminos#privacidad (DEC-68, DEC-76): la casilla y el pie enlazan al mismo lugar.
      const casilla = screen.getByRole('checkbox', { name: /Acepto la Política de Privacidad/ })
      expect(casilla).toHaveAccessibleName('Acepto la Política de Privacidad y el tratamiento de mis datos.')
      const enlace = screen.getByRole('link', { name: 'Política de Privacidad' })
      expect(enlace).toHaveAttribute('href', '/terminos#privacidad')
      // En una pestaña nueva, para no perder lo escrito, y anunciado a los lectores de pantalla.
      expect(enlace).toHaveAttribute('target', '_blank')
      expect(enlace).toHaveAttribute('rel', 'noopener noreferrer')
      expect(enlace).toHaveAccessibleDescription('Se abre en una pestaña nueva.')
      const pie = within(screen.getByRole('contentinfo'))
      expect(pie.getByRole('link', { name: 'Políticas de privacidad' })).toHaveAttribute('href', '/terminos#privacidad')
    },
  )

  it('valida en el cliente: errores por campo, foco en el primero y resumen, sin llamar al backend', async () => {
    const { solicitudes } = simular()
    const { usuario } = renderApp('/registro')
    await screen.findByRole('option', { name: 'Colombia' })

    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de turista' }))

    expect(registros(solicitudes)).toHaveLength(0)
    expect(screen.getByRole('alert')).toHaveTextContent('Revisa los 7 campos marcados.')
    const primerNombre = screen.getByLabelText('Primer nombre')
    expect(primerNombre).toHaveFocus()
    expect(primerNombre).toHaveAttribute('aria-invalid', 'true')
    expect(primerNombre).toHaveAccessibleDescription('Este campo es obligatorio.')
    expect(screen.getByRole('combobox', { name: 'País de origen' })).toHaveAccessibleDescription(
      'Selecciona tu país de origen.',
    )
    expect(screen.getByText('Elige al menos una categoría de interés.')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Acepto la Política/ })).toHaveAccessibleDescription(
      'Debes aceptar la Política de Privacidad para crear la cuenta.',
    )
    expect(screen.getByLabelText('Segundo nombre (opcional)')).not.toHaveAttribute('aria-invalid')
  })

  // Límite de 15 s en las que llenan el formulario completo con userEvent: medidas en más de 2 s con toda la suite
  // (turista 2,6 s, prestador 3,3 s, servicio no disponible 2,1 s) o cerca (errores del backend, 1,7–1,9 s).
  it('un turista registrado vuelve al login con el mensaje y el correo escrito, sin el correo en la URL', async () => {
    const { solicitudes } = simular({ [REGISTRO_TURISTA]: RECIBIDO })
    const { usuario } = renderApp('/registro')
    await screen.findByRole('option', { name: 'Colombia' })

    await llenarTurista(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de turista' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Registro recibido. Inicia sesión con tu correo.')
    expect(ubicacionActual()).toBe('/login')
    expect(screen.getByLabelText('Correo electrónico')).toHaveValue('ana@x.com')
    expect(registros(solicitudes)[0].cuerpo).toEqual({
      primerNombre: 'Ana',
      segundoNombre: '',
      primerApellido: 'Pérez',
      segundoApellido: '',
      correo: 'ana@x.com',
      contrasena: 'Clave1234',
      paisOrigen: 'CO',
      categoriasInteres: ['Cultura'],
      aceptoPoliticas: true,
    })
  }, 15_000)

  it('un prestador registrado vuelve al login con el aviso de validación pendiente', async () => {
    const { solicitudes } = simular({ [REGISTRO_PRESTADOR]: RECIBIDO })
    const { usuario } = renderApp('/registro')
    await usuario.click(screen.getByRole('radio', { name: 'Prestador de Servicios' }))

    await usuario.type(screen.getByLabelText('Nombre del representante legal'), 'Carlos Gómez')
    await usuario.type(screen.getByLabelText('Número RNT (Registro Nacional de Turismo)'), 'RNT-123456')
    await usuario.type(screen.getByLabelText('Correo corporativo'), 'Contacto@Empresa.com')
    await usuario.type(screen.getByLabelText('Teléfono'), '+57 300 000 0000')
    await usuario.type(screen.getByLabelText('Crear contraseña'), 'Clave1234')
    await usuario.click(screen.getByRole('checkbox', { name: /Acepto la Política/ }))
    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de prestador' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Solicitud recibida; quedará pendiente de validación.')
    expect(screen.getByLabelText('Correo electrónico')).toHaveValue('contacto@empresa.com')
    expect(registros(solicitudes)[0].cuerpo).toMatchObject({ correoCorporativo: 'contacto@empresa.com', rnt: 'RNT-123456' })
  }, 15_000)

  it('muestra en cada campo los códigos que devuelve el backend', async () => {
    simular({
      [REGISTRO_TURISTA]: () =>
        respuestaProblema(400, 'DATOS_INVALIDOS', { campos: { correo: 'FORMATO', categoriasInteres: 'DUPLICADO' } }),
    })
    const { usuario } = renderApp('/registro')
    await screen.findByRole('option', { name: 'Colombia' })

    await llenarTurista(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de turista' }))

    const correo = screen.getByLabelText('Correo electrónico')
    await waitFor(() => expect(correo).toHaveFocus())
    expect(correo).toHaveAccessibleDescription('Escribe un correo válido, por ejemplo nombre@correo.com.')
    expect(screen.getByText('No repitas una opción.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Revisa los 2 campos marcados.')
    expect(ubicacionActual()).toBe('/registro')
  }, 15_000)

  it.each([
    ['RNT_YA_REGISTRADO', () => respuestaProblema(409, 'RNT_YA_REGISTRADO'), 'Número RNT (Registro Nacional de Turismo)',
      'Ya hay un prestador registrado con este RNT.'],
    ['POLITICAS_NO_ACEPTADAS', () => respuestaProblema(400, 'POLITICAS_NO_ACEPTADAS'), /Acepto la Política/,
      'Debes aceptar la Política de Privacidad para crear la cuenta.'],
  ])('el %s del backend aparece en su campo', async (_codigo, respuesta, etiqueta, mensaje) => {
    simular({ [REGISTRO_PRESTADOR]: respuesta })
    const { usuario } = renderApp('/registro')
    await usuario.click(screen.getByRole('radio', { name: 'Prestador de Servicios' }))
    await usuario.type(screen.getByLabelText('Nombre del representante legal'), 'Carlos')
    await usuario.type(screen.getByLabelText('Número RNT (Registro Nacional de Turismo)'), 'RNT-1')
    await usuario.type(screen.getByLabelText('Correo corporativo'), 'c@empresa.co')
    await usuario.type(screen.getByLabelText('Teléfono'), '300')
    await usuario.type(screen.getByLabelText('Crear contraseña'), 'Clave1234')
    await usuario.click(screen.getByRole('checkbox', { name: /Acepto la Política/ }))

    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de prestador' }))

    await waitFor(() => expect(screen.getByLabelText(etiqueta)).toHaveAccessibleDescription(mensaje))
  }, 15_000)

  it('si el servicio no está disponible lo avisa sin marcar campos', async () => {
    simular({ [REGISTRO_TURISTA]: () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-auth' }) })
    const { usuario } = renderApp('/registro')
    await screen.findByRole('option', { name: 'Colombia' })

    await llenarTurista(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de turista' }))

    expect(await screen.findByText(/El servicio no está disponible/)).toBeInTheDocument()
    expect(screen.getByLabelText('Primer nombre')).not.toHaveAttribute('aria-invalid')
  }, 15_000)

  // DEC-271: el gateway limita los registros por conexión y responde 429 DEMASIADAS_SOLICITUDES.
  it('un 429 del gateway avisa de los demasiados intentos sin marcar campos', async () => {
    simular({ [REGISTRO_TURISTA]: () => respuestaProblema(429, 'DEMASIADAS_SOLICITUDES') })
    const { usuario } = renderApp('/registro')
    await screen.findByRole('option', { name: 'Colombia' })

    await llenarTurista(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Crear cuenta de turista' }))

    expect(await screen.findByText(/Hubo demasiados intentos desde esta conexión/)).toBeInTheDocument()
    expect(screen.getByLabelText('Primer nombre')).not.toHaveAttribute('aria-invalid')
  }, 15_000)

  it('los países salen del backend, ordenados por el nombre en el idioma activo', async () => {
    simular()
    const { usuario } = renderApp('/registro')

    const pais = await screen.findByRole('combobox', { name: 'País de origen' })
    await screen.findByRole('option', { name: 'Colombia' })
    expect(within(pais).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Selecciona tu país',
      'Alemania',
      'Colombia',
      'España',
      'Estados Unidos',
    ])

    await usuario.click(screen.getByRole('button', { name: 'Inglés' }))
    const country = screen.getByRole('combobox', { name: 'Country of origin' })
    expect(within(country).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Select your country',
      'Colombia',
      'Germany',
      'Spain',
      'United States',
    ])
  })

  it('si la lista de países no carga, avisa y permite reintentar', async () => {
    let intentos = 0
    simularFetch({
      [PAISES]: () => (++intentos === 1 ? respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE') : respuestaJson(200, CODIGOS)),
    })
    const { usuario } = renderApp('/registro')

    expect(await screen.findByText('No pudimos cargar la lista de países.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'País de origen' })).toBeDisabled()
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByRole('option', { name: 'Colombia' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'País de origen' })).toBeEnabled()
  })

  it('funciona en inglés: formulario y errores traducidos', async () => {
    simular()
    const { usuario } = renderApp('/registro')
    await usuario.click(screen.getByRole('button', { name: 'Inglés' }))

    expect(screen.getByRole('heading', { name: 'Form – Tourist' })).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Create tourist account' }))

    expect(screen.getByLabelText('First name')).toHaveAccessibleDescription('This field is required.')
    expect(screen.getByRole('alert')).toHaveTextContent('Check the 7 marked fields.')
  })

  it('con la sesión iniciada, /registro redirige al inicio', async () => {
    sessionStorage.setItem('samaria360.token', 'token-guardado')
    simularFetch({
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_TURISTA),
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])),
    })
    renderApp('/registro')

    await waitFor(() => expect(ubicacionActual()).toBe('/'))
  })
})

describe('Inicio de sesión de un prestador sin validar (decisión D3)', () => {
  it.each([
    ['CUENTA_PENDIENTE_VALIDACION', 'Tu cuenta está en revisión por el administrador.'],
    ['CUENTA_RECHAZADA', 'El administrador rechazó la solicitud de tu cuenta.'],
  ])('%s muestra su mensaje en el formulario', async (codigo, mensaje) => {
    simularFetch({ 'POST /api/v1/auth/login': () => respuestaProblema(403, codigo) })
    const { usuario } = renderApp('/login')

    await usuario.type(screen.getByLabelText('Correo electrónico'), 'prestador@samaria360.test')
    await usuario.type(screen.getByLabelText('Contraseña'), 'Samaria360*')
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(mensaje)
  })
})
