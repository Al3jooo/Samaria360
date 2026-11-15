import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  DETALLE_HOTEL,
  USUARIO_ADMIN,
  USUARIO_PRESTADOR,
  USUARIO_TURISTA,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const DETALLE = 'GET /api/v1/catalogo/servicios'
const CALIFICACIONES = 'GET /api/v1/analytics/calificaciones'
const ELEGIBILIDAD = 'GET /api/v1/analytics/calificaciones/elegibilidad'

const CON_RESENAS = {
  promedio: 4.5,
  cantidad: 2,
  elementos: [
    { id: 'c1', estrellas: 5, comentario: 'Excelente atención', fecha: '2030-03-01T15:00:00Z' },
    { id: 'c2', estrellas: 4, comentario: '', fecha: '2030-02-20T15:00:00Z' },
  ],
  pagina: 0,
  tamano: 10,
  totalElementos: 2,
}

function turista(extra: Parameters<typeof simularFetch>[0] = {}) {
  sessionStorage.setItem('samaria360.token', 'token-turista')
  return simularFetch({
    [ME]: () => respuestaJson(200, USUARIO_TURISTA),
    [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL),
    ...extra,
  })
}

describe('Calificaciones en WF-06 (RF17)', () => {
  it('muestra el promedio en el subtítulo y las reseñas visibles, sin datos del turista', async () => {
    simularFetch({
      [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL),
      [CALIFICACIONES]: () => respuestaJson(200, CON_RESENAS),
    })
    renderApp('/servicios/s-hotel')

    expect(await screen.findByText('4,5 (2 calificaciones)')).toBeInTheDocument()
    const seccion = screen.getByRole('region', { name: 'Calificaciones y reseñas' })
    expect(within(seccion).getByText('Excelente atención')).toBeInTheDocument()
    expect(within(seccion).getAllByRole('listitem')).toHaveLength(2)
    expect(within(seccion).getByRole('link', { name: 'Inicia sesión como turista para calificar.' })).toHaveAttribute(
      'href',
      '/login',
    )
  })

  it('el turista con la reserva Completada califica con estrellas y comentario', async () => {
    let calificado = false
    const { solicitudes } = turista({
      [ELEGIBILIDAD]: () => respuestaJson(200, { puedeCalificar: !calificado, motivo: null, propia: null }),
      [CALIFICACIONES]: () => respuestaJson(200, calificado ? CON_RESENAS : { ...CON_RESENAS, promedio: null, cantidad: 0, elementos: [], totalElementos: 0 }),
      'POST /api/v1/analytics/calificaciones': () => {
        calificado = true
        return respuestaJson(201, { id: 'c9', estrellas: 4, comentario: 'Muy bueno', fecha: '2030-03-02T15:00:00Z' })
      },
    })
    const { usuario } = renderApp('/servicios/s-hotel')

    const formulario = await screen.findByRole('group', { name: 'Tu calificación' })
    await usuario.click(within(formulario).getByLabelText(/4 de 5 estrellas/))
    await usuario.type(screen.getByLabelText(/Comentario/), 'Muy bueno')
    await usuario.click(screen.getByRole('button', { name: 'Enviar calificación' }))

    expect(await screen.findByText('¡Gracias! Tu calificación ya se ve en la ficha.')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'POST' && s.url === '/api/v1/analytics/calificaciones')?.cuerpo).toEqual({
      tipoEntidad: 'servicio',
      idEntidad: DETALLE_HOTEL.id,
      estrellas: 4,
      comentario: 'Muy bueno',
    })
    expect(await screen.findByText('Excelente atención')).toBeInTheDocument()
  })

  it('sin reserva Completada explica por qué no puede calificar', async () => {
    turista()
    renderApp('/servicios/s-hotel')

    expect(
      await screen.findByText('Podrás calificar este servicio cuando tengas una reserva completada.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar calificación' })).not.toBeInTheDocument()
  })
})

describe('Reportar información falsa (RF57)', () => {
  it('el turista elige un motivo de la lista y envía el reporte', async () => {
    const { solicitudes } = turista({
      'POST /api/v1/analytics/reportes': () => respuestaJson(201, { id: 'r1', estado: 'Pendiente' }),
    })
    const { usuario } = renderApp('/servicios/s-hotel')

    await usuario.click(await screen.findByRole('button', { name: 'Reportar información falsa o desactualizada' }))
    await usuario.selectOptions(screen.getByLabelText('Motivo'), 'PRECIO_INCORRECTO')
    await usuario.type(screen.getByLabelText('Descripción (opcional)'), 'Subió el precio')
    await usuario.click(screen.getByRole('button', { name: 'Enviar reporte' }))

    expect(await screen.findByText('Gracias: el reporte quedó en revisión del Administrador.')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.url === '/api/v1/analytics/reportes')?.cuerpo).toEqual({
      tipoEntidad: 'servicio',
      idEntidad: DETALLE_HOTEL.id,
      motivo: 'PRECIO_INCORRECTO',
      descripcion: 'Subió el precio',
    })
  })

  it('un reporte duplicado lo dice', async () => {
    turista({ 'POST /api/v1/analytics/reportes': () => respuestaProblema(409, 'REPORTE_DUPLICADO') })
    const { usuario } = renderApp('/servicios/s-hotel')

    await usuario.click(await screen.findByRole('button', { name: 'Reportar información falsa o desactualizada' }))
    await usuario.selectOptions(screen.getByLabelText('Motivo'), 'OTRO')
    await usuario.click(screen.getByRole('button', { name: 'Enviar reporte' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya tienes un reporte pendiente para este lugar.')
  })
})

describe('Calificaciones recibidas (WF-33, RF56)', () => {
  it('el Prestador ve el promedio y filtra por servicio en la URL', async () => {
    sessionStorage.setItem('samaria360.token', 'token-prestador')
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_PRESTADOR),
      'GET /api/v1/analytics/prestador/calificaciones': () =>
        respuestaJson(200, {
          promedio: 4.0,
          cantidad: 1,
          servicios: [{ idServicio: 's1', nombre: 'Tour Tayrona', promedio: 4.0, cantidad: 1 }],
          elementos: [
            { id: 'c1', idServicio: 's1', nombreServicio: 'Tour Tayrona', estrellas: 4, comentario: 'Bien', fecha: '2030-03-01T15:00:00Z' },
          ],
          pagina: 0,
          tamano: 20,
          totalElementos: 1,
        }),
    })
    const { usuario } = renderApp('/prestador/calificaciones')

    expect(await screen.findByRole('heading', { level: 1, name: 'Calificaciones recibidas' })).toBeInTheDocument()
    expect(await screen.findByText('Bien')).toBeInTheDocument()
    await usuario.selectOptions(screen.getByLabelText('Servicio'), 's1')
    await usuario.click(screen.getByRole('button', { name: 'Filtrar' }))

    expect(ubicacionActual()).toContain('servicio=s1')
    expect(solicitudes.at(-1)?.url).toContain('servicio=s1')
  })
})

describe('Moderación (WF-34; RF29, RF57)', () => {
  const CALIFICACION = {
    id: 'c1',
    tipoEntidad: 'servicio',
    idEntidad: 's1',
    nombreEntidad: 'Tour Tayrona',
    turistaId: 't1',
    estrellas: 1,
    comentario: 'Texto ofensivo',
    estado: 'Visible',
    motivoModeracion: null,
    fecha: '2030-03-01T15:00:00Z',
    fechaModeracion: null,
  }

  it('el Administrador oculta una calificación con su motivo', async () => {
    sessionStorage.setItem('samaria360.token', 'token-admin')
    let oculta = false
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_ADMIN),
      'GET /api/v1/analytics/admin/calificaciones': () =>
        respuestaJson(200, {
          elementos: [{ ...CALIFICACION, estado: oculta ? 'Oculta' : 'Visible' }],
          pagina: 0,
          tamano: 20,
          totalElementos: 1,
        }),
      'POST /api/v1/analytics/admin/calificaciones/c1/ocultamiento': () => {
        oculta = true
        return respuestaJson(200, { ...CALIFICACION, estado: 'Oculta' })
      },
    })
    const { usuario } = renderApp('/admin/moderacion')

    await usuario.click(await screen.findByRole('button', { name: 'Ocultar' }))
    await usuario.type(screen.getByLabelText('Motivo para ocultarla'), 'Lenguaje ofensivo')
    await usuario.click(screen.getByRole('button', { name: 'Ocultar calificación' }))

    expect(await screen.findByRole('button', { name: 'Volver a mostrar' })).toBeInTheDocument()
    expect(solicitudes.find((s) => s.url.endsWith('/ocultamiento'))?.cuerpo).toEqual({ motivo: 'Lenguaje ofensivo' })
  })

  it('en «Reportes» marca un reporte revisado', async () => {
    sessionStorage.setItem('samaria360.token', 'token-admin')
    let revisado = false
    simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_ADMIN),
      'GET /api/v1/analytics/admin/reportes': () =>
        respuestaJson(200, {
          elementos: revisado
            ? []
            : [{ id: 'r1', tipoEntidad: 'atractivo', idEntidad: 'a1', turistaId: 't1', motivo: 'INFORMACION_DESACTUALIZADA', descripcion: 'Horario viejo', estado: 'Pendiente', fecha: '2030-03-01T15:00:00Z', revisadoPor: null, fechaRevision: null }],
          pagina: 0,
          tamano: 20,
          totalElementos: revisado ? 0 : 1,
        }),
      'POST /api/v1/analytics/admin/reportes/r1/revision': () => {
        revisado = true
        return respuestaJson(200, {})
      },
    })
    const { usuario } = renderApp('/admin/moderacion?pestana=reportes')

    expect(await screen.findByText('Información desactualizada')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver el atractivo' })).toHaveAttribute('href', '/atractivos/a1')
    await usuario.click(screen.getByRole('button', { name: 'Marcar revisado' }))

    expect(await screen.findByText('Reporte marcado como revisado.')).toBeInTheDocument()
    expect(await screen.findByText('No hay reportes con este filtro.')).toBeInTheDocument()
  })

  it('un Turista ve «Sin permiso»', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA) })
    renderApp('/admin/moderacion')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin permiso' })).toBeInTheDocument()
  })
})
