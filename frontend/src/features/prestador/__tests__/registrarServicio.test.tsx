import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mapaVivo } from '../../../test/leafletSimulado'
import {
  USUARIO_PRESTADOR,
  perfilPrestador,
  respuestaJson,
  respuestaProblema,
  servicioGestion,
  simularFetch,
  type SolicitudSimulada,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const REGISTRAR = 'POST /api/v1/catalogo/prestador/servicios'
const RUTA = '/prestador/servicios/nuevo'
const ID = '6a3000000000000000000001'

/** La primera carga de una página diferida puede pasar de 5 s con toda la suite en paralelo (ver test/setup.ts). */
const LENTO = { timeout: 15_000 }

type Usuario = ReturnType<typeof renderApp>['usuario']

const JPEG = [0xff, 0xd8, 0xff, 0xe0]

function foto(nombre: string, firma = JPEG): File {
  const bytes = new Uint8Array(64)
  bytes.set(firma)
  return new File([bytes], nombre, { type: 'image/jpeg' })
}

/** jsdom no tiene URL.createObjectURL: vistas previas con una URL fija por archivo. */
beforeEach(() => {
  let n = 0
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => `blob:vista-${(n += 1)}`), revokeObjectURL: vi.fn() }))
})

function conPrestador(rutas: Parameters<typeof simularFetch>[0] = {}) {
  sessionStorage.setItem('samaria360.token', 'token-guardado')
  return simularFetch({
    [ME]: () => respuestaJson(200, USUARIO_PRESTADOR),
    [`GET /api/v1/catalogo/prestador/servicios/${ID}`]: () => respuestaJson(200, servicioGestion()),
    ...rutas,
  })
}

async function abrirRegistro(rutas: Parameters<typeof simularFetch>[0] = {}) {
  const simulado = conPrestador(rutas)
  const app = renderApp(RUTA)
  await screen.findByRole('heading', { level: 1, name: 'Registrar servicio comercial' }, LENTO)
  return { ...simulado, ...app }
}

const escribir = (etiqueta: string | RegExp, valor: string, contenedor: HTMLElement = document.body) =>
  fireEvent.change(within(contenedor).getByLabelText(etiqueta), { target: { value: valor } })

const registros = (solicitudes: SolicitudSimulada[]) =>
  solicitudes.filter((s) => s.metodo === 'POST' && s.url === '/api/v1/catalogo/prestador/servicios')

/** Datos comunes válidos y la categoría. */
async function llenarComunes(usuario: Usuario, categoria: string) {
  escribir('Nombre comercial', 'Tour de Café en Minca')
  await usuario.selectOptions(screen.getByLabelText('Categoría'), categoria)
  escribir('Descripción', 'Recorrido guiado por fincas cafeteras.')
  escribir('Teléfono / WhatsApp', '+57 300 000 0301')
  escribir('Correo electrónico', 'tours@cafeminca.test')
  escribir('Dirección', 'Iglesia de Minca')
  escribir('Latitud', '11.1432')
  escribir('Longitud', '-74.1121')
  // DEC-318: la foto es obligatoria en el registro.
  await usuario.upload(screen.getByLabelText('Agregar fotos'), [foto('portada.jpg')])
}

async function llenarTour(usuario: Usuario) {
  await llenarComunes(usuario, 'Tour')
  escribir('Precio «desde» (COP)', '80000')
  escribir('Punto de encuentro', 'Iglesia de Minca')
  escribir('Duración (horas)', '4')
  escribir('Aforo máximo por grupo', '12')
  await usuario.click(screen.getByRole('checkbox', { name: 'Sábado' }))
  await usuario.click(screen.getByRole('checkbox', { name: 'Lunes' }))
  escribir('Salida 1', '13:30')
  await usuario.click(screen.getByRole('button', { name: 'Agregar salida' }))
  escribir('Salida 2', '07:00')
}

async function llenarUnidad(usuario: Usuario, numero: number, nombre: string, precio: string) {
  if (numero > 1) await usuario.click(screen.getByRole('button', { name: 'Agregar unidad' }))
  const unidad = screen.getByRole('group', { name: `Unidad ${numero}` })
  escribir('Nombre de la unidad', nombre, unidad)
  escribir('Tipo de habitación', 'Doble', unidad)
  escribir('Habitaciones', '3', unidad)
  escribir('Huéspedes por habitación', '2', unidad)
  escribir('Precio por noche', precio, unidad)
}

async function llenarAlojamiento(usuario: Usuario) {
  await llenarComunes(usuario, 'Alojamiento')
  await llenarUnidad(usuario, 1, 'Doble', '150000')
  await llenarUnidad(usuario, 2, 'Suite', '120000.50')
  await usuario.click(screen.getByRole('button', { name: 'Todos los días' }))
}

const enviar = (usuario: Usuario) => usuario.click(screen.getByRole('button', { name: 'Enviar a aprobación' }))

describe('WF-08 «Registrar servicio comercial» (RF09, RF10, RF37)', () => {
  it('Tour: el cuerpo respeta el contrato y el 201 lleva al detalle propio «Pendiente de aprobación» (DEC-168)', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaJson(201, servicioGestion()),
    })
    await llenarTour(usuario)
    await enviar(usuario)

    expect(await screen.findByText('Enviado a aprobación: el Administrador lo revisará.', {}, LENTO)).toHaveAttribute(
      'role',
      'status',
    )
    expect(ubicacionActual()).toBe(`/prestador/servicios/${ID}`)
    expect(screen.getByText('Pendiente de aprobación')).toBeInTheDocument()

    const [registro] = registros(solicitudes)
    expect(registro.cabeceras.Authorization).toBe('Bearer token-guardado')
    expect(registro.cuerpo).toEqual({
      nombreComercial: 'Tour de Café en Minca',
      categoria: 'Tour',
      descripcion: 'Recorrido guiado por fincas cafeteras.',
      direccion: 'Iglesia de Minca',
      coordenadas: { longitud: -74.1121, latitud: 11.1432 },
      contacto: { telefono: '+57 300 000 0301', correo: 'tours@cafeminca.test' },
      condicionesAccesibilidad: [],
      precioBase: 80000,
      detalleCategoria: { puntoEncuentro: 'Iglesia de Minca', duracionHoras: 4, aforoMaxGrupo: 12 },
      // Días en el orden de la semana y salidas ordenadas al enviar (DEC-164).
      oferta: { diasOperacion: ['Lunes', 'Sabado'], salidas: ['07:00', '13:30'] },
    })
    expect(registro.cuerpoTexto).toContain('"coordenadas":{"longitud":-74.1121,"latitud":11.1432}')
    for (const campo of ['rnt', 'estadoPublicacion', 'prestadorId', 'prestadorActivo', 'idPrestador']) {
      expect(registro.cuerpo).not.toHaveProperty(campo)
    }
  })

  it('Alojamiento con 2 unidades: precio «desde» derivado en pantalla, sin precioBase ni salidas en el cuerpo', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaJson(201, servicioGestion({ categoria: 'Alojamiento' })),
    })
    await llenarAlojamiento(usuario)

    // El precio «desde» es el menor precio por noche (DEC-127), en solo lectura.
    expect(screen.queryByLabelText('Precio «desde» (COP)', { selector: 'input' })).not.toBeInTheDocument()
    expect(screen.getByText('Se calcula con el menor precio por noche de tus unidades.')).toBeInTheDocument()
    expect(document.querySelector('output')?.textContent).toMatch(/120\.000,5/)

    await enviar(usuario)
    await waitFor(() => expect(registros(solicitudes)).toHaveLength(1))
    const { cuerpo } = registros(solicitudes)[0] as { cuerpo: Record<string, unknown> }
    expect(cuerpo).not.toHaveProperty('precioBase')
    expect(cuerpo.oferta).toEqual({
      diasOperacion: ['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado', 'Domingo'],
    })
    expect((cuerpo.detalleCategoria as { unidades: unknown[] }).unidades).toEqual([
      { nombre: 'Doble', tipoHabitacion: 'Doble', numHabitaciones: 3, capacidadMaxHuespedes: 2, precioNoche: 150000 },
      { nombre: 'Suite', tipoHabitacion: 'Doble', numHabitaciones: 3, capacidadMaxHuespedes: 2, precioNoche: 120000.5 },
    ])
  })

  it.each([
    ['Gastronomia', 'Gastronomía'],
    ['AlquilerEquipo', 'Alquiler de equipos'],
  ])('%s: sin oferta en el cuerpo y la sección lo dice', async (categoria) => {
    const { solicitudes, usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaJson(201, servicioGestion({ categoria, oferta: null })),
    })
    await llenarComunes(usuario, categoria)
    escribir('Precio «desde» (COP)', '45000')
    expect(screen.getByText('Esta categoría no registra oferta inicial.')).toBeInTheDocument()
    if (categoria === 'Gastronomia') {
      escribir('Tipo de cocina', 'Caribeña')
      escribir('Aforo máximo por turno', '40')
      escribir('Rango de precio por plato', '$30.000 – $60.000')
    } else {
      escribir('Tipo de vehículo o equipo', 'Kayak')
      escribir('Unidades disponibles', '6')
    }
    await enviar(usuario)

    await waitFor(() => expect(registros(solicitudes)).toHaveLength(1))
    expect(registros(solicitudes)[0].cuerpo).not.toHaveProperty('oferta')
  })

  it('al cambiar de categoría no se envían los campos de la anterior, y al volver se recupera lo escrito', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaJson(201, servicioGestion({ categoria: 'Gastronomia' })),
    })
    await llenarTour(usuario)
    await usuario.selectOptions(screen.getByLabelText('Categoría'), 'Gastronomia')
    escribir('Tipo de cocina', 'Caribeña')
    escribir('Aforo máximo por turno', '40')
    escribir('Rango de precio por plato', '$30.000 – $60.000')

    // Volver a Tour recupera lo escrito; luego se envía como Gastronomía.
    await usuario.selectOptions(screen.getByLabelText('Categoría'), 'Tour')
    expect(screen.getByLabelText('Punto de encuentro')).toHaveValue('Iglesia de Minca')
    await usuario.selectOptions(screen.getByLabelText('Categoría'), 'Gastronomia')
    await enviar(usuario)

    await waitFor(() => expect(registros(solicitudes)).toHaveLength(1))
    const { cuerpo } = registros(solicitudes)[0] as { cuerpo: Record<string, unknown> }
    expect(cuerpo.categoria).toBe('Gastronomia')
    expect(cuerpo.detalleCategoria).toEqual({
      tipoCocina: 'Caribeña',
      aforoMaxTurno: 40,
      menuVegetariano: false,
      rangoPrecioPlato: '$30.000 – $60.000',
    })
    expect(cuerpo).not.toHaveProperty('oferta')
    expect(JSON.stringify(cuerpo)).not.toContain('puntoEncuentro')
  })

  it('sin datos no envía nada: marca los obligatorios y enfoca el primero', async () => {
    const { solicitudes, usuario } = await abrirRegistro()
    await enviar(usuario)

    const nombre = screen.getByLabelText('Nombre comercial')
    await waitFor(() => expect(nombre).toHaveFocus())
    expect(nombre).toHaveAttribute('aria-invalid', 'true')
    expect(nombre).toHaveAccessibleDescription(expect.stringContaining('Este dato es obligatorio.'))
    expect(screen.getByLabelText('Latitud')).toHaveAccessibleDescription('Este dato es obligatorio.')
    expect(registros(solicitudes)).toHaveLength(0)
  })

  it('400 DATOS_INVALIDOS con claves anidadas e índices: cada error junto a su control', async () => {
    const { usuario } = await abrirRegistro({
      [REGISTRAR]: () =>
        respuestaProblema(400, 'DATOS_INVALIDOS', {
          campos: {
            'contacto.correo': 'FORMATO',
            'oferta.salidas[1]': 'DUPLICADO',
            'detalleCategoria.aforoMaxGrupo': 'VALOR_MAXIMO',
          },
        }),
    })
    await llenarTour(usuario)
    await enviar(usuario)

    const correo = screen.getByLabelText('Correo electrónico')
    await waitFor(() => expect(correo).toHaveAttribute('aria-invalid', 'true'))
    expect(correo).toHaveAccessibleDescription(expect.stringContaining('El formato no es válido.'))
    // Las salidas se ordenaron antes de enviar: el índice 1 es la de las 13:30.
    const segunda = screen.getByLabelText('Salida 2')
    expect(segunda).toHaveValue('13:30')
    expect(segunda).toHaveAccessibleDescription('Este valor está repetido.')
    expect(screen.getByLabelText('Salida 1')).not.toHaveAttribute('aria-invalid')
    // El primero en el orden del formulario recibe el foco.
    await waitFor(() => expect(correo).toHaveFocus())
    expect(screen.getByRole('alert')).toHaveTextContent('')
  })

  it('DEC-166: un VALOR_MAXIMO del servidor aparece junto al control correcto, con su texto, y en ningún otro', async () => {
    const { usuario } = await abrirRegistro({
      [REGISTRAR]: () =>
        respuestaProblema(400, 'DATOS_INVALIDOS', {
          campos: { 'detalleCategoria.unidades[1].capacidadMaxHuespedes': 'VALOR_MAXIMO' },
        }),
    })
    await llenarAlojamiento(usuario)
    await enviar(usuario)

    const segunda = within(screen.getByRole('group', { name: 'Unidad 2' })).getByLabelText('Huéspedes por habitación')
    await waitFor(() => expect(segunda).toHaveAttribute('aria-invalid', 'true'))
    expect(segunda).toHaveAccessibleDescription('El valor es mayor que el permitido.')
    expect(segunda.id).toBe('servicio-detalleCategoria-unidades-1-capacidadMaxHuespedes')
    expect(within(screen.getByRole('group', { name: 'Unidad 1' })).getByLabelText('Huéspedes por habitación')).not.toHaveAttribute(
      'aria-invalid',
    )
    expect(screen.getAllByText('El valor es mayor que el permitido.')).toHaveLength(1)
  })

  it('un error del servidor sin control en pantalla da un mensaje general', async () => {
    const { usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaProblema(400, 'DATOS_INVALIDOS', { campos: { 'detalleCategoria.otro': 'NO_PERMITIDO' } }),
    })
    await llenarTour(usuario)
    await enviar(usuario)

    expect(await screen.findByText('Algunos datos no son válidos. Revisa el formulario.')).toBeInTheDocument()
  })

  it('DEC-162: un 403 PRESTADOR_NO_HABILITADO durante la sesión muestra su mensaje y conserva lo escrito', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaProblema(403, 'PRESTADOR_NO_HABILITADO'),
    })
    await llenarTour(usuario)
    await enviar(usuario)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Tu cuenta aún no está habilitada para registrar servicios. Lo que escribiste se conserva.',
    )
    expect(ubicacionActual()).toBe(RUTA)
    // La sesión sigue abierta (un 403 que no es CUENTA_INACTIVA no la cierra).
    expect(screen.getByRole('button', { name: 'Menú de usuario de Casa Tayrona SAS' })).toBeInTheDocument()
    expect(screen.getByLabelText('Nombre comercial')).toHaveValue('Tour de Café en Minca')
    expect(screen.getByLabelText('Categoría')).toHaveValue('Tour')
    expect(screen.getByLabelText('Latitud')).toHaveValue('11.1432')
    expect(screen.getByLabelText('Punto de encuentro')).toHaveValue('Iglesia de Minca')
    expect(screen.getByRole('checkbox', { name: 'Lunes' })).toBeChecked()
    expect(screen.getByLabelText('Salida 1')).toHaveValue('07:00')
    expect(registros(solicitudes)).toHaveLength(1)
  })

  it.each([
    ['401', () => respuestaProblema(401, 'NO_AUTENTICADO')],
    ['SESION_INVALIDADA', () => respuestaProblema(401, 'SESION_INVALIDADA')],
  ])('con %s la sesión se cierra y se va al login (comportamiento actual del cliente)', async (_caso, respuesta) => {
    const { usuario } = await abrirRegistro({ [REGISTRAR]: respuesta })
    await llenarTour(usuario)
    await enviar(usuario)

    await waitFor(() => expect(ubicacionActual()).toBe('/login'), LENTO)
    expect(sessionStorage.getItem('samaria360.token')).toBeNull()
  })

  it('503 del catálogo: mensaje propio y lo escrito se conserva', async () => {
    const { usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-catalog' }),
    })
    await llenarTour(usuario)
    await enviar(usuario)

    expect(await screen.findByRole('alert')).toHaveTextContent('El catálogo no está disponible en este momento.')
    expect(screen.getByLabelText('Nombre comercial')).toHaveValue('Tour de Café en Minca')
  })

  it('los elementos de WF-08 sin contrato aparecen deshabilitados (DEC-167) y el RNT no muestra un valor', async () => {
    await abrirRegistro()

    for (const nombre of ['Cobra por', 'El precio incluye impuestos', 'Confirmo que la información es veraz']) {
      const grupo = screen.getByRole('group', { name: nombre })
      expect(grupo).toHaveAttribute('aria-disabled', 'true')
      expect(within(grupo).getByText('No disponible')).toHaveClass('solo-lector')
    }
    expect(screen.getByText('Se toma de tu registro de prestador.')).toBeInTheDocument()
    expect(screen.queryByText(/RNT-/)).not.toBeInTheDocument()
    // O22: el mapa nace en un efecto del selector, que puede correr después de que aparezca el título.
    const mapa = await waitFor(() => mapaVivo(), LENTO)
    expect(mapa.teselas()[0].opciones.attribution).toContain('OpenStreetMap')
  })

  it('DEC-318: las fotos van en el multipart junto a los datos; sin fotos no se envía y se marca el grupo', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      [REGISTRAR]: () => respuestaJson(201, servicioGestion()),
    })
    await llenarTour(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Quitar la foto 1' }))
    await enviar(usuario)

    expect(registros(solicitudes)).toHaveLength(0)
    expect(screen.getByRole('group', { name: 'Fotos del servicio' })).toHaveFocus()

    await usuario.upload(screen.getByLabelText('Agregar fotos'), [foto('portada.jpg'), foto('finca.jpg')])
    await enviar(usuario)

    await waitFor(() => expect(ubicacionActual()).toBe(`/prestador/servicios/${ID}`), LENTO)
    const [registro] = registros(solicitudes)
    expect(registro.cabeceras['Content-Type']).toBeUndefined()
    expect((registro.formulario!.getAll('imagenes') as File[]).map((imagen) => imagen.name)).toEqual([
      'portada.jpg',
      'finca.jpg',
    ])
    expect(registro.cuerpo).toMatchObject({ nombreComercial: 'Tour de Café en Minca', categoria: 'Tour' })
  })

  it('O16 (DEC-203): el RNT se muestra con el de `GET /prestador/perfil` y no viaja en el cuerpo', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      'GET /api/v1/auth/prestador/perfil': () => respuestaJson(200, perfilPrestador({ rnt: 'RNT-12345' })),
      [REGISTRAR]: () => respuestaJson(201, servicioGestion()),
    })
    expect(await screen.findByText('RNT-12345', {}, LENTO)).toBeInTheDocument()
    expect(screen.queryByText('Se toma de tu registro de prestador.')).not.toBeInTheDocument()

    await llenarTour(usuario)
    await enviar(usuario)
    await waitFor(() => expect(registros(solicitudes)).toHaveLength(1))
    expect(registros(solicitudes)[0].cuerpo).not.toHaveProperty('rnt')
  })

  it('O16 (DEC-203): si `GET /prestador/perfil` falla, queda el texto de DEC-167 y el formulario sigue usable', async () => {
    const { solicitudes, usuario } = await abrirRegistro({
      'GET /api/v1/auth/prestador/perfil': () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE'),
      [REGISTRAR]: () => respuestaJson(201, servicioGestion()),
    })
    await waitFor(() => expect(solicitudes.some((s) => s.url === '/api/v1/auth/prestador/perfil')).toBe(true))
    expect(screen.getByText('Se toma de tu registro de prestador.')).toBeInTheDocument()

    await llenarTour(usuario)
    await enviar(usuario)
    expect(await screen.findByText('Enviado a aprobación: el Administrador lo revisará.', {}, LENTO)).toBeInTheDocument()
  })

  it('unidades: de 1 a 20, «Quitar» no está con una sola y quitar una fila limpia sus errores', async () => {
    const { usuario } = await abrirRegistro()
    await usuario.selectOptions(screen.getByLabelText('Categoría'), 'Alojamiento')

    expect(screen.getByRole('button', { name: 'Quitar la unidad 1' })).toBeDisabled()
    const agregar = screen.getByRole('button', { name: 'Agregar unidad' })
    for (let i = 0; i < 19; i += 1) await usuario.click(agregar)
    expect(screen.getAllByRole('group', { name: /^Unidad \d+$/ })).toHaveLength(20)
    expect(agregar).toBeDisabled()

    await enviar(usuario)
    const nombre20 = within(screen.getByRole('group', { name: 'Unidad 20' })).getByLabelText('Nombre de la unidad')
    expect(nombre20).toHaveAttribute('aria-invalid', 'true')
    await usuario.click(screen.getByRole('button', { name: 'Quitar la unidad 20' }))
    expect(screen.getAllByRole('group', { name: /^Unidad \d+$/ })).toHaveLength(19)
    expect(within(screen.getByRole('group', { name: 'Unidad 19' })).getByLabelText('Nombre de la unidad')).not.toHaveAttribute(
      'aria-invalid',
    )
  })
})
