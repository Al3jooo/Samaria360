import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  PERFIL_TURISTA,
  USUARIO_ADMIN,
  USUARIO_PRESTADOR,
  USUARIO_TURISTA,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
  type SolicitudSimulada,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const PERFIL = '/api/v1/auth/perfil'
const PAISES = 'GET /api/v1/auth/paises'
const CATALOGO = { 'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])) }

/** La primera carga de una página diferida puede pasar de 5 s con toda la suite en paralelo (ver test/setup.ts). */
const LENTO = { timeout: 15_000 }

function conSesion(usuario: object, rutas: Parameters<typeof simularFetch>[0] = {}) {
  sessionStorage.setItem('samaria360.token', 'token-guardado')
  return simularFetch({
    [ME]: () => respuestaJson(200, usuario),
    [PAISES]: () => respuestaJson(200, ['CO', 'US', 'MX']),
    [`GET ${PERFIL}`]: () => respuestaJson(200, PERFIL_TURISTA),
    ...rutas,
  })
}

const puts = (solicitudes: SolicitudSimulada[]) => solicitudes.filter((s) => s.metodo === 'PUT' && s.url === PERFIL)

async function abrirPerfil(ruta = '/perfil', rutas: Parameters<typeof simularFetch>[0] = {}) {
  const simulado = conSesion(USUARIO_TURISTA, rutas)
  const app = renderApp(ruta)
  await screen.findByRole('tabpanel', {}, LENTO)
  // En «Datos personales», el país espera la lista de /paises.
  if (!ruta.includes('intereses')) await waitFor(() => expect(screen.getByLabelText('País de origen')).toBeEnabled())
  return { ...simulado, ...app }
}

describe('Turista, acceso (RNF06, DEC-92)', () => {
  it('el menú del turista lleva a «Mi perfil» y a «Mis favoritos»', async () => {
    conSesion(USUARIO_TURISTA, CATALOGO)
    const { usuario } = renderApp('/')
    await usuario.click(await screen.findByRole('button', { name: 'Menú de usuario de Ana Pérez' }))

    const menu = within(screen.getByRole('menu'))
    expect(menu.getByRole('menuitem', { name: 'Mi perfil' })).toHaveAttribute('href', '/perfil')
    expect(menu.getByRole('menuitem', { name: 'Mis favoritos' })).toHaveAttribute('href', '/perfil/favoritos')
  })

  it.each([
    ['Prestador', USUARIO_PRESTADOR],
    ['Administrador', USUARIO_ADMIN],
  ])('el menú del %s no tiene «Mi perfil» ni «Mis favoritos»', async (_rol, usuarioActual) => {
    conSesion(usuarioActual, CATALOGO)
    const { usuario } = renderApp('/')
    await usuario.click(await screen.findByRole('button', { name: `Menú de usuario de ${usuarioActual.nombreVisible}` }))

    const menu = within(screen.getByRole('menu'))
    expect(menu.queryByRole('menuitem', { name: 'Mi perfil' })).not.toBeInTheDocument()
    expect(menu.queryByRole('menuitem', { name: 'Mis favoritos' })).not.toBeInTheDocument()
  })

  it.each([
    ['Prestador', USUARIO_PRESTADOR, '/perfil'],
    ['Prestador', USUARIO_PRESTADOR, '/perfil/favoritos'],
    ['Administrador', USUARIO_ADMIN, '/perfil'],
    ['Administrador', USUARIO_ADMIN, '/perfil/favoritos'],
  ])('el %s en %s ve «Sin permiso» y no llama a la API', async (_rol, usuarioActual, ruta) => {
    const { solicitudes } = conSesion(usuarioActual)
    renderApp(ruta)

    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url === PERFIL || s.url.startsWith('/api/v1/catalogo/favoritos'))).toBe(false)
  })

  it.each(['/perfil', '/perfil/favoritos'])('sin sesión, %s lleva al login', async (ruta) => {
    simularFetch({})
    renderApp(ruta)

    await waitFor(() => expect(ubicacionActual()).toBe('/login'))
  })
})

describe('WF-16 «Mi perfil» (RF53)', () => {
  it('carga el perfil con el token; el correo es de solo lectura y /paises sale sin token (DEC-47)', async () => {
    const { solicitudes } = await abrirPerfil()

    const correo = screen.getByLabelText('Correo electrónico')
    expect(correo).toHaveValue('turista@samaria360.test')
    expect(correo).toHaveAttribute('readonly')
    expect(correo).toHaveAccessibleDescription('Es tu identidad para iniciar sesión y no se puede cambiar.')
    expect(screen.getByLabelText('Primer nombre')).toHaveValue('Ana')
    expect(screen.getByLabelText('País de origen')).toHaveValue('CO')

    expect(solicitudes.find((s) => s.url === PERFIL)?.cabeceras.Authorization).toBe('Bearer token-guardado')
    expect(solicitudes.find((s) => s.url === '/api/v1/auth/paises')?.cabeceras.Authorization).toBeUndefined()
  })

  it('guardar envía el perfil completo, nunca el correo, con los intereses guardados (no el borrador)', async () => {
    let meLlamadas = 0
    const { solicitudes, usuario } = await abrirPerfil('/perfil', {
      [ME]: () => {
        meLlamadas += 1
        return respuestaJson(200, meLlamadas === 1 ? USUARIO_TURISTA : { ...USUARIO_TURISTA, nombreVisible: 'Ana María Pérez' })
      },
      [`PUT ${PERFIL}`]: ({ cuerpo }) =>
        respuestaJson(200, { ...PERFIL_TURISTA, ...(cuerpo as object), segundoNombre: 'María' }),
    })

    // Un cambio sin guardar en «Intereses» no viaja al guardar «Datos personales» (DEC-97) y se conserva.
    await usuario.click(screen.getByRole('tab', { name: 'Intereses turísticos' }))
    expect(ubicacionActual()).toBe('/perfil?seccion=intereses')
    await usuario.click(screen.getByRole('checkbox', { name: 'Aventura' }))
    await usuario.click(screen.getByRole('tab', { name: 'Datos personales' }))

    await usuario.type(screen.getByLabelText(/^Segundo nombre/), 'María')
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('Perfil actualizado.')).toHaveAttribute('role', 'status')
    const [put] = puts(solicitudes)
    expect(put.cabeceras.Authorization).toBe('Bearer token-guardado')
    expect(put.cuerpo).toEqual({
      primerNombre: 'Ana',
      segundoNombre: 'María',
      primerApellido: 'Pérez',
      segundoApellido: '',
      paisOrigen: 'CO',
      categoriasInteres: ['Naturaleza', 'Cultura'],
    })
    expect(put.cuerpo).not.toHaveProperty('correo')

    // El nombre del encabezado se actualiza con /me.
    expect(await screen.findByRole('button', { name: 'Menú de usuario de Ana María Pérez' })).toBeInTheDocument()

    // El borrador de los intereses sigue ahí.
    await usuario.click(screen.getByRole('tab', { name: 'Intereses turísticos' }))
    expect(screen.getByRole('checkbox', { name: 'Aventura' })).toBeChecked()
  })

  it('intereses: solo hay 3 casillas, 0 no se puede enviar y guardar envía las elegidas con los datos guardados', async () => {
    const { solicitudes, usuario } = await abrirPerfil('/perfil?seccion=intereses', {
      [`PUT ${PERFIL}`]: ({ cuerpo }) => respuestaJson(200, { ...PERFIL_TURISTA, ...(cuerpo as object) }),
    })

    const grupo = screen.getByRole('group', { name: 'Categorías de interés' })
    expect(within(grupo).getAllByRole('checkbox').map((casilla) => casilla.closest('label')?.textContent)).toEqual([
      'Naturaleza',
      'Cultura',
      'Aventura',
    ])

    await usuario.click(screen.getByRole('checkbox', { name: 'Naturaleza' }))
    await usuario.click(screen.getByRole('checkbox', { name: 'Cultura' }))
    await usuario.click(screen.getByRole('button', { name: 'Guardar intereses' }))

    expect(await screen.findByText('Elige al menos una categoría de interés.')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Naturaleza' })).toHaveFocus()
    expect(puts(solicitudes)).toHaveLength(0)

    await usuario.click(screen.getByRole('checkbox', { name: 'Aventura' }))
    await usuario.click(screen.getByRole('button', { name: 'Guardar intereses' }))

    expect(await screen.findByText('Intereses actualizados.')).toBeInTheDocument()
    expect(puts(solicitudes)[0].cuerpo).toEqual({
      primerNombre: 'Ana',
      segundoNombre: '',
      primerApellido: 'Pérez',
      segundoApellido: '',
      paisOrigen: 'CO',
      categoriasInteres: ['Aventura'],
    })
  })

  it('un 400 DATOS_INVALIDOS muestra cada código en su campo y enfoca el primero', async () => {
    const { usuario } = await abrirPerfil('/perfil', {
      [`PUT ${PERFIL}`]: () =>
        respuestaProblema(400, 'DATOS_INVALIDOS', { campos: { primerApellido: 'LONGITUD_MAXIMA', paisOrigen: 'NO_PERMITIDO' } }),
    })

    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    const apellido = screen.getByLabelText('Primer apellido')
    await waitFor(() => expect(apellido).toHaveAttribute('aria-invalid', 'true'))
    expect(apellido).toHaveAccessibleDescription('Admite como máximo 50 caracteres.')
    expect(screen.getByLabelText('País de origen')).toHaveAccessibleDescription('Elige una opción de la lista.')
    expect(apellido).toHaveFocus()
    expect(screen.queryByText('Perfil actualizado.')).not.toBeInTheDocument()
  })

  it('más de 50 caracteres o un nombre vacío no se envían (las reglas del registro, DEC-85)', async () => {
    const { solicitudes, usuario } = await abrirPerfil()

    await usuario.clear(screen.getByLabelText('Primer nombre'))
    await usuario.type(screen.getByLabelText(/^Segundo nombre/), 'x'.repeat(51))
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(screen.getByLabelText('Primer nombre')).toHaveAccessibleDescription('Este campo es obligatorio.')
    expect(screen.getByLabelText(/^Segundo nombre/)).toHaveAccessibleDescription('Admite como máximo 50 caracteres.')
    expect(puts(solicitudes)).toHaveLength(0)
  })

  it('«Descartar» vuelve a los valores guardados', async () => {
    const { usuario } = await abrirPerfil()

    await usuario.type(screen.getByLabelText('Primer nombre'), 'beth')
    expect(screen.getByLabelText('Primer nombre')).toHaveValue('Anabeth')
    await usuario.click(screen.getByRole('button', { name: 'Descartar' }))

    expect(screen.getByLabelText('Primer nombre')).toHaveValue('Ana')
  })

  it.each([
    ['503', () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-auth' })],
    [
      'error de red',
      () => {
        throw new TypeError('Failed to fetch')
      },
    ],
  ])('si /me falla después de guardar (%s), la sesión y el nombre se conservan y el perfil queda guardado', async (_caso, fallo) => {
    let meLlamadas = 0
    const { usuario } = await abrirPerfil('/perfil', {
      [ME]: () => {
        meLlamadas += 1
        return meLlamadas === 1 ? respuestaJson(200, USUARIO_TURISTA) : fallo()
      },
      [`PUT ${PERFIL}`]: ({ cuerpo }) => respuestaJson(200, { ...PERFIL_TURISTA, ...(cuerpo as object) }),
    })

    await usuario.clear(screen.getByLabelText('Primer nombre'))
    await usuario.type(screen.getByLabelText('Primer nombre'), 'Anita')
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('Perfil actualizado.')).toBeInTheDocument()
    await waitFor(() => expect(meLlamadas).toBe(2))
    expect(screen.getByRole('button', { name: 'Menú de usuario de Ana Pérez' })).toBeInTheDocument()
    expect(screen.getByLabelText('Primer nombre')).toHaveValue('Anita')
    expect(sessionStorage.getItem('samaria360.token')).toBe('token-guardado')
    expect(ubicacionActual()).toBe('/perfil')
  })

  it('un 401 SESION_INVALIDADA al guardar cierra la sesión con su motivo (DEC-50) y lleva al login', async () => {
    const { usuario } = await abrirPerfil('/perfil', {
      [`PUT ${PERFIL}`]: () => respuestaProblema(401, 'SESION_INVALIDADA'),
    })

    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(ubicacionActual()).toBe('/login'))
    expect(sessionStorage.getItem('samaria360.token')).toBeNull()
    expect(await screen.findByText(/cambió el estado o los permisos de tu cuenta/)).toBeInTheDocument()
  })

  it('cambiar de idioma conserva lo escrito (RNF08)', async () => {
    const { usuario } = await abrirPerfil()

    await usuario.type(screen.getByLabelText('Primer nombre'), 'beth')
    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'My profile' })).toBeInTheDocument()
    expect(screen.getByLabelText('First name')).toHaveValue('Anabeth')
  })
})
