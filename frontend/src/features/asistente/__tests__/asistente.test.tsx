import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  USUARIO_ADMIN,
  USUARIO_PRESTADOR,
  USUARIO_TURISTA,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp } from '../../../test/renderApp'
import { compartirTexto } from '../compartirItinerario'
import { porDia, type ItemItinerario } from '../asistenteApi'

const ME = 'GET /api/v1/auth/me'
const PERFIL = 'GET /api/v1/auth/perfil'
const MENSAJES = 'POST /api/v1/chatbot/mensajes'
const ITINERARIOS = '/api/v1/chatbot/itinerarios'
const CONOCIMIENTO = '/api/v1/chatbot/admin/conocimiento'

const PERFIL_TURISTA = {
  correo: 'turista@samaria360.test',
  primerNombre: 'María',
  segundoNombre: null,
  primerApellido: 'Gómez',
  segundoApellido: null,
  paisOrigen: 'CO',
  categoriasInteres: ['Aventura'],
}

const RESPUESTA = {
  respuesta: 'Te propongo un itinerario de 2 días con lo que encontré en Samaria360.',
  sugerencias: [
    {
      tipo: 'Servicio',
      id: '6a0000000000000000000003',
      nombre: 'Tour Tayrona',
      categoria: 'Tour',
      criterio: 'PREFERENCIAS',
      justificacion: 'Coincide con tus intereses declarados (Tour).',
    },
    {
      tipo: 'Atractivo',
      id: '6a0000000000000000000001',
      nombre: 'Playa Cristal',
      categoria: 'Naturaleza',
      criterio: 'DISPONIBILIDAD',
      justificacion: 'Está abierto y disponible para visitar.',
    },
  ],
  itinerario: [
    { orden: 1, tipo: 'servicio', refId: '6a0000000000000000000003', tituloActividad: 'Tour Tayrona', diaSugerido: 1 },
    { orden: 2, tipo: 'atractivo', refId: '6a0000000000000000000001', tituloActividad: 'Playa Cristal', diaSugerido: 2 },
  ],
  fuentes: [],
  catalogoDisponible: true,
  mensajesRestantes: 19,
}

function sesionTurista(extra: Parameters<typeof simularFetch>[0] = {}) {
  sessionStorage.setItem('samaria360.token', 'token-turista')
  return simularFetch({
    [ME]: () => respuestaJson(200, USUARIO_TURISTA),
    [PERFIL]: () => respuestaJson(200, PERFIL_TURISTA),
    ...extra,
  })
}

describe('Asistente virtual IA (WF-07; RF04, RF35, RF36, RF59)', () => {
  it('envía el mensaje con el idioma y los intereses, y muestra la respuesta, el itinerario por día y los prestadores', async () => {
    const { solicitudes } = sesionTurista({ [MENSAJES]: () => respuestaJson(200, RESPUESTA) })
    const { usuario } = renderApp('/asistente')

    expect(await screen.findByRole('heading', { level: 1, name: 'Asistente Virtual IA' })).toBeInTheDocument()
    // Antes de la primera respuesta no se sabe cuántos mensajes quedan hoy.
    expect(screen.getByText('Puedes enviar hasta 20 mensajes al día.')).toBeInTheDocument()
    expect(screen.getByText('En línea')).toBeInTheDocument()
    expect(screen.getByRole('note')).toHaveTextContent('versión beta')
    expect(screen.getByRole('button', { name: 'Guardar itinerario en perfil' })).toBeDisabled()

    await usuario.type(screen.getByLabelText('Tu pregunta'), 'Un plan de 2 días con tours')
    await usuario.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(await screen.findByText(RESPUESTA.respuesta)).toBeInTheDocument()
    const envio = solicitudes.find((s) => s.url === '/api/v1/chatbot/mensajes')
    expect(envio?.cuerpo).toEqual({ mensaje: 'Un plan de 2 días con tours', idioma: 'es', intereses: ['Aventura'] })
    expect(envio?.cabeceras.Authorization).toBe('Bearer token-turista')

    const itinerario = screen.getByRole('region', { name: 'Itinerario sugerido' })
    expect(within(itinerario).getByRole('heading', { name: 'Día 1' })).toBeInTheDocument()
    expect(within(itinerario).getByRole('link', { name: 'Playa Cristal' })).toHaveAttribute(
      'href',
      '/atractivos/6a0000000000000000000001',
    )
    const prestadores = screen.getByRole('region', { name: 'Prestadores mencionados' })
    expect(within(prestadores).getByRole('link', { name: /Tour Tayrona/ })).toHaveTextContent(
      'Coincide con tus intereses declarados',
    )
    expect(within(prestadores).queryByText('Playa Cristal')).not.toBeInTheDocument()
    expect(screen.getByText('Te quedan 19 de 20 mensajes hoy.')).toBeInTheDocument()
  }, 15_000)

  it('guarda el itinerario sugerido en el perfil (RF35)', async () => {
    const { solicitudes } = sesionTurista({
      [MENSAJES]: () => respuestaJson(200, RESPUESTA),
      [`POST ${ITINERARIOS}`]: () =>
        respuestaJson(201, { id: 'i1', nombre: 'x', items: RESPUESTA.itinerario, fechaCreacion: '', fechaActualizacion: '' }),
    })
    const { usuario } = renderApp('/asistente')

    await usuario.click(await screen.findByRole('button', { name: 'Tours mañana' }))
    await screen.findByText(RESPUESTA.respuesta)
    await usuario.click(screen.getByRole('button', { name: 'Guardar itinerario en perfil' }))

    expect(await screen.findByText('Itinerario guardado en tu perfil.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver mis itinerarios' })).toHaveAttribute('href', '/perfil/itinerarios')
    const guardado = solicitudes.find((s) => s.url === ITINERARIOS && s.metodo === 'POST')
    expect(guardado?.cuerpo).toMatchObject({ items: RESPUESTA.itinerario })
    expect((guardado?.cuerpo as { nombre: string }).nombre).toMatch(/^Itinerario del /)
  }, 15_000)

  it('el mensaje 21 del día muestra el límite y deshabilita el campo', async () => {
    sesionTurista({ [MENSAJES]: () => respuestaProblema(429, 'LIMITE_MENSAJES') })
    const { usuario } = renderApp('/asistente')

    await usuario.type(await screen.findByLabelText('Tu pregunta'), 'hola')
    await usuario.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('límite de 20 mensajes')
    expect(screen.getByLabelText('Tu pregunta')).toBeDisabled()
  })

  it('en inglés pide la respuesta en inglés (RF59)', async () => {
    const { solicitudes } = sesionTurista({ [MENSAJES]: () => respuestaJson(200, { ...RESPUESTA, itinerario: [] }) })
    const { usuario } = renderApp('/asistente')

    await usuario.click(await screen.findByRole('button', { name: 'EN · Inglés' }))
    await usuario.type(screen.getByLabelText('Your question'), 'beaches')
    await usuario.click(screen.getByRole('button', { name: 'Send' }))

    await screen.findByText(RESPUESTA.respuesta)
    expect((solicitudes.find((s) => s.url === '/api/v1/chatbot/mensajes')?.cuerpo as { idioma: string }).idioma).toBe('en')
  })

  it('un Prestador ve «Sin permiso» y no se llama al asistente', async () => {
    sessionStorage.setItem('samaria360.token', 'token-prestador')
    const { solicitudes } = simularFetch({ [ME]: () => respuestaJson(200, USUARIO_PRESTADOR) })
    renderApp('/asistente')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin permiso' })).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url.startsWith('/api/v1/chatbot'))).toBe(false)
  })
})

describe('Mis itinerarios (WF-31, RF35)', () => {
  const GUARDADO = {
    id: 'i1',
    nombre: 'Fin de semana',
    items: RESPUESTA.itinerario,
    fechaCreacion: '2030-03-01T15:00:00Z',
    fechaActualizacion: '2030-03-01T15:00:00Z',
  }

  it('lista, renombra y elimina con confirmación', async () => {
    const { solicitudes } = sesionTurista({
      [`GET ${ITINERARIOS}`]: () => respuestaJson(200, { elementos: [GUARDADO] }),
      [`PUT ${ITINERARIOS}/i1`]: (s) => respuestaJson(200, { ...GUARDADO, ...(s.cuerpo as object) }),
      [`DELETE ${ITINERARIOS}/i1`]: () => new Response(null, { status: 204 }),
    })
    const { usuario } = renderApp('/perfil/itinerarios')

    expect(await screen.findByRole('heading', { level: 2, name: 'Fin de semana' })).toBeInTheDocument()
    expect(screen.getByText('1 de 20 itinerarios')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Editar' }))
    const nombre = screen.getByLabelText('Nombre')
    await usuario.clear(nombre)
    await usuario.type(nombre, 'Puente festivo')
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByRole('heading', { level: 2, name: 'Puente festivo' })).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'PUT')?.cuerpo).toMatchObject({ nombre: 'Puente festivo' })

    await usuario.click(screen.getByRole('button', { name: 'Eliminar' }))
    await usuario.click(screen.getByRole('button', { name: 'Sí, eliminar' }))
    expect(await screen.findByText('Todavía no tienes itinerarios guardados.')).toBeInTheDocument()
  }, 15_000)
})

describe('Base de conocimientos (WF-32, RF05)', () => {
  it('el Administrador agrega una entrada', async () => {
    sessionStorage.setItem('samaria360.token', 'token-admin')
    let entradas: unknown[] = []
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_ADMIN),
      [`GET ${CONOCIMIENTO}`]: () =>
        respuestaJson(200, { elementos: entradas, pagina: 0, tamano: 10, totalElementos: entradas.length }),
      [`POST ${CONOCIMIENTO}`]: (s) => {
        const entrada = { id: 'k1', ...(s.cuerpo as object), fechaActualizacion: '2030-03-01T15:00:00Z' }
        entradas = [entrada]
        return respuestaJson(201, entrada)
      },
    })
    const { usuario } = renderApp('/admin/conocimiento')

    expect(await screen.findByText('La base de conocimientos está vacía.')).toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Categoría'), 'Clima')
    await usuario.type(screen.getByLabelText(/^Contenido/), 'Lleva protector solar.')
    await usuario.click(screen.getByRole('button', { name: 'Agregar entrada' }))

    expect(await screen.findByText('Entrada agregada.')).toBeInTheDocument()
    expect(await screen.findByText('Lleva protector solar.')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'POST')?.cuerpo).toEqual({
      contenido: 'Lleva protector solar.',
      categoria: 'Clima',
    })
  }, 15_000)
})

describe('Utilidades del itinerario', () => {
  it('agrupa por día en orden', () => {
    expect(porDia([...RESPUESTA.itinerario].reverse() as ItemItinerario[]).map((d) => d.dia)).toEqual([1, 2])
  })

  it('comparte con la hoja del sistema, copia o avisa', async () => {
    const share = vi.fn(async () => {})
    expect(await compartirTexto('T', 'x', { share })).toBe('compartido')
    const writeText = vi.fn(async () => {})
    expect(await compartirTexto('T', 'x', { clipboard: { writeText } })).toBe('copiado')
    expect(writeText).toHaveBeenCalledWith('T\n\nx')
    expect(await compartirTexto('T', 'x', {})).toBe('manual')
  })
})
