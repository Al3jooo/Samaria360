import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  USUARIO_PRESTADOR,
  respuestaJson,
  respuestaProblema,
  servicioGestion,
  servicioResumen,
  simularFetch,
  type SolicitudSimulada,
} from '../../../test/fetchSimulado'
import { renderApp } from '../../../test/renderApp'
import { asistentesCsv } from '../ofertaApi'

/**
 * Iteración I del Prestador (DEC-310 a DEC-313): WF-38 «Oferta y disponibilidad» (RF19), WF-39 «Historial de
 * modificaciones» (RF41), WF-40 «Asistentes» (RF42) y la galería de WF-18 (RF48).
 */

const ME = 'GET /api/v1/auth/me'
const ID = '6a3000000000000000000001'
const CATALOGO = '/api/v1/catalogo/prestador'
const RESERVAS = '/api/v1/reservas/prestador'
const LENTO = { timeout: 15_000 }

type Manejador = (solicitud: SolicitudSimulada) => Response | Promise<Response>

function montar(ruta: string, rutas: Record<string, Manejador>) {
  sessionStorage.setItem('samaria360.token', 'token-guardado')
  const simulado = simularFetch({ [ME]: () => respuestaJson(200, USUARIO_PRESTADOR), ...rutas })
  return { ...simulado, ...renderApp(ruta) }
}

const INVENTARIO = {
  idServicio: ID,
  nombre: 'Tour de Café en Minca',
  categoria: 'Tour',
  desde: '2026-11-16',
  hasta: '2026-11-29',
  diasOperacion: ['Lunes', 'Sabado'],
  salidas: ['07:00:00', '13:30:00'],
  filas: [
    { categoria: 'Tour', idUnidad: null, fecha: '2026-11-16', horaInicio: '07:00:00', cuposTotales: 12, cuposDisponibles: 9, reservados: 3, cerrado: false },
    { categoria: 'Tour', idUnidad: null, fecha: '2026-11-16', horaInicio: '13:30:00', cuposTotales: 12, cuposDisponibles: 12, reservados: 0, cerrado: false },
    { categoria: 'Tour', idUnidad: null, fecha: '2026-11-21', horaInicio: '07:00:00', cuposTotales: 12, cuposDisponibles: 12, reservados: 0, cerrado: true },
  ],
}

const PUBLICADO = servicioGestion({ estadoPublicacion: 'Publicado', imagenes: ['/api/v1/catalogo/imagenes/a1'] })

const PAGINA_SERVICIOS = {
  elementos: [servicioResumen({ estadoPublicacion: 'Publicado' }), servicioResumen({ id: '6a3000000000000000000009', nombreComercial: 'Restaurante', categoria: 'Gastronomia' })],
  pagina: 0,
  tamano: 10,
  totalElementos: 2,
  totalPaginas: 1,
}

/** Fila del inventario: 1 = 16 nov 07:00 (con reservas), 2 = 16 nov 13:30, 3 = 21 nov 07:00 (cerrada). */
const filaDe = (numero: number) => within(screen.getByRole('table')).getAllByRole('row')[numero]

describe('WF-38 Oferta y disponibilidad (RF19)', () => {
  it('el detalle Publicado lleva a la disponibilidad y lista cupos, reservados y estado', async () => {
    const { usuario } = montar(`/prestador/servicios/${ID}`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
      [`GET ${RESERVAS}/servicios/${ID}/inventario`]: () => respuestaJson(200, INVENTARIO),
    })

    await usuario.click(await screen.findByRole('link', { name: 'Gestionar disponibilidad' }, LENTO))
    expect(await screen.findByRole('heading', { level: 1, name: 'Disponibilidad de Tour de Café en Minca' }, LENTO)).toBeInTheDocument()

    const tabla = screen.getByRole('table')
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)
    expect(within(tabla).getByText('Cerrada')).toBeInTheDocument()
    // Con reservas, «Cerrar» queda deshabilitado (el servidor también lo rechaza: 409 FECHA_CON_RESERVAS).
    const conReservas = filaDe(1)
    expect(within(conReservas).getByRole('button', { name: 'Cerrar' })).toBeDisabled()
    expect(within(tabla).getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
  })

  it('guardar cupos envía la fila exacta y el 409 CUPOS_RESERVADOS se explica con lo reservado', async () => {
    const { solicitudes, usuario } = montar(`/prestador/servicios/${ID}/oferta`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
      [`GET ${RESERVAS}/servicios/${ID}/inventario`]: () => respuestaJson(200, INVENTARIO),
      [`PUT ${RESERVAS}/servicios/${ID}/inventario`]: () => respuestaProblema(409, 'CUPOS_RESERVADOS', { reservados: 3 }),
    })

    await screen.findByRole('table', {}, LENTO)
    const fila = filaDe(1)
    fireEvent.change(within(fila).getByRole('spinbutton'), { target: { value: '2' } })
    await usuario.click(within(fila).getByRole('button', { name: 'Guardar cupos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No puedes dejar menos cupos que los ya reservados (3).')
    const put = solicitudes.find((s) => s.metodo === 'PUT')
    expect(put?.cuerpo).toEqual({ fecha: '2026-11-16', horaInicio: '07:00', idUnidad: null, cuposTotales: 2 })
  })

  it('cerrar una fecha sin reservas usa DELETE con la fecha y la salida y recarga', async () => {
    let cargas = 0
    const { solicitudes, usuario } = montar(`/prestador/servicios/${ID}/oferta?desde=2026-11-16&hasta=2026-11-29`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
      [`GET ${RESERVAS}/servicios/${ID}/inventario`]: () => {
        cargas += 1
        return respuestaJson(200, INVENTARIO)
      },
      [`DELETE ${RESERVAS}/servicios/${ID}/inventario`]: () => respuestaJson(200, { ...INVENTARIO.filas[1], cerrado: true }),
    })

    await screen.findByRole('table', {}, LENTO)
    expect(solicitudes.find((s) => s.url.includes('/inventario?'))?.url).toContain('desde=2026-11-16&hasta=2026-11-29')
    await usuario.click(within(filaDe(2)).getByRole('button', { name: 'Cerrar' }))

    expect(await screen.findByText('Fecha cerrada.')).toBeInTheDocument()
    const borrado = solicitudes.find((s) => s.metodo === 'DELETE')
    expect(borrado?.url).toBe(`${RESERVAS}/servicios/${ID}/inventario?fecha=2026-11-16&horaInicio=13%3A30`)
    await waitFor(() => expect(cargas).toBe(2))
  })

  it('la plantilla envía los días en orden y las salidas; el 202 sin cuerpo se confirma', async () => {
    const { solicitudes, usuario } = montar(`/prestador/servicios/${ID}/oferta`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
      [`GET ${RESERVAS}/servicios/${ID}/inventario`]: () => respuestaJson(200, INVENTARIO),
      [`PUT ${RESERVAS}/servicios/${ID}/plantilla`]: () => new Response(null, { status: 202 }),
    })

    await screen.findByRole('table', {}, LENTO)
    await usuario.click(screen.getByRole('checkbox', { name: 'Miércoles' }))
    await usuario.click(screen.getByRole('checkbox', { name: 'Lunes' }))
    fireEvent.change(screen.getByLabelText('Horarios de salida (HH:mm, separados por coma)'), {
      target: { value: '06:30, 15:00' },
    })
    await usuario.click(screen.getByRole('button', { name: 'Guardar días y horarios' }))

    expect(await screen.findByText('Días y horarios guardados. Las fechas nuevas los usarán.')).toBeInTheDocument()
    const put = solicitudes.find((s) => s.metodo === 'PUT')
    expect(put?.cuerpo).toEqual({ diasOperacion: ['Miercoles', 'Sabado'], salidas: ['06:30', '15:00'] })
  })

  it('un servicio sin oferta editable muestra el motivo', async () => {
    montar(`/prestador/servicios/${ID}/oferta`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
      [`GET ${RESERVAS}/servicios/${ID}/inventario`]: () => respuestaProblema(409, 'OFERTA_NO_DISPONIBLE'),
    })

    expect(await screen.findByRole('alert', {}, LENTO)).toHaveTextContent('no tiene disponibilidad editable')
  })
})

describe('WF-39 Historial de modificaciones (RF41)', () => {
  it('lista el valor anterior y el nuevo y filtra por servicio en la URL', async () => {
    const { solicitudes, usuario } = montar('/prestador/historial-modificaciones', {
      [`GET ${CATALOGO}/servicios`]: () => respuestaJson(200, PAGINA_SERVICIOS),
      [`GET ${CATALOGO}/historial-modificaciones`]: () =>
        respuestaJson(200, {
          elementos: [
            { idServicio: ID, nombreServicio: 'Tour de Café en Minca', tipo: 'Precio', detalle: null, valorAnterior: '80000', valorNuevo: '95000', fecha: '2026-11-14T15:00:00Z' },
            { idServicio: ID, nombreServicio: 'Tour de Café en Minca', tipo: 'Cupos', detalle: '2026-11-16 07:00', valorAnterior: '12', valorNuevo: '8', fecha: '2026-11-13T15:00:00Z' },
          ],
          pagina: 0,
          tamano: 20,
          totalElementos: 2,
        }),
    })

    expect(await screen.findByRole('heading', { level: 1, name: 'Historial de modificaciones' }, LENTO)).toBeInTheDocument()
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText('95000')).toBeInTheDocument()
    expect(within(tabla).getByText('2026-11-16 07:00')).toBeInTheDocument()
    expect(within(tabla).getByText('Cupos')).toBeInTheDocument()

    await screen.findByRole('option', { name: 'Tour de Café en Minca' })
    await usuario.selectOptions(screen.getByRole('combobox', { name: 'Servicio' }), ID)
    await usuario.click(screen.getByRole('button', { name: 'Filtrar' }))
    await waitFor(() =>
      expect(solicitudes.some((s) => s.url === `${CATALOGO}/historial-modificaciones?servicio=${ID}&pagina=0`)).toBe(true),
    )
  })
})

describe('WF-40 Asistentes (RF42)', () => {
  const LISTADO = {
    idServicio: ID,
    fecha: '2026-11-16',
    horaInicio: null,
    totalPersonas: 3,
    asistentes: [
      { idReserva: 'r1', nombre: 'Ana Pérez', correo: 'ana@correo.test', telefono: '+57 300', personas: 2, horaInicio: '07:00:00', nombreUnidad: null, fechaEntrada: '2026-11-16', fechaSalida: '2026-11-16' },
      { idReserva: 'r2', nombre: null, correo: null, telefono: null, personas: 1, horaInicio: '13:30', nombreUnidad: null, fechaEntrada: '2026-11-16', fechaSalida: null },
    ],
  }

  it('consulta con servicio y fecha de la URL, muestra el contacto y descarga el CSV', async () => {
    const crear = vi.fn(() => 'blob:asistentes')
    const revocar = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: crear, revokeObjectURL: revocar }))
    const { solicitudes, usuario } = montar(`/prestador/asistentes?servicio=${ID}&fecha=2026-11-16`, {
      [`GET ${CATALOGO}/servicios`]: () => respuestaJson(200, PAGINA_SERVICIOS),
      [`GET ${RESERVAS}/asistentes`]: () => respuestaJson(200, LISTADO),
    })

    expect(await screen.findByText('Ana Pérez', {}, LENTO)).toBeInTheDocument()
    expect(screen.getByText('Contacto no disponible')).toBeInTheDocument()
    expect(screen.getByText('3 personas en total')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.url.startsWith(`${RESERVAS}/asistentes`))?.url).toBe(
      `${RESERVAS}/asistentes?servicio=${ID}&fecha=2026-11-16`,
    )
    // Solo los servicios con oferta (Tour y Alojamiento) en el selector.
    await screen.findByRole('option', { name: 'Tour de Café en Minca' })
    expect(screen.queryByRole('option', { name: 'Restaurante' })).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Descargar CSV' }))
    expect(crear).toHaveBeenCalledTimes(1)
    expect(revocar).toHaveBeenCalledWith('blob:asistentes')
  })

  it('sin servicio ni fecha no consulta; el 503 de ms-auth se explica', async () => {
    const { solicitudes } = montar('/prestador/asistentes', {
      [`GET ${CATALOGO}/servicios`]: () => respuestaJson(200, PAGINA_SERVICIOS),
    })
    expect(await screen.findByText('Elige un servicio y una fecha para ver los asistentes.', {}, LENTO)).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url.startsWith(`${RESERVAS}/asistentes`))).toBe(false)
  })

  it('el 503 de los contactos se explica', async () => {
    montar(`/prestador/asistentes?servicio=${ID}&fecha=2026-11-16`, {
      [`GET ${CATALOGO}/servicios`]: () => respuestaJson(200, PAGINA_SERVICIOS),
      [`GET ${RESERVAS}/asistentes`]: () => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-auth' }),
    })
    expect(await screen.findByRole('alert', {}, LENTO)).toHaveTextContent('Los datos de contacto no están disponibles')
  })

  it('el CSV lleva BOM, punto y coma y comillas escapadas', () => {
    const csv = asistentesCsv(
      { ...LISTADO, asistentes: [{ ...LISTADO.asistentes[0], horaInicio: '07:00', nombre: 'Ana "La Guía"' }] },
      ['Nombre', 'Correo', 'Teléfono', 'Personas', 'Salida', 'Entrada', 'Fin'],
    )
    expect(csv.startsWith('﻿"Nombre";"Correo"')).toBe(true)
    expect(csv).toContain('"Ana ""La Guía""";"ana@correo.test";"+57 300";"2";"07:00";"2026-11-16";"2026-11-16"')
  })
})

describe('Galería de WF-18 (RF48)', () => {
  it('agrega una imagen PNG y quitar queda deshabilitado con una sola', async () => {
    const { solicitudes, usuario } = montar(`/prestador/servicios/${ID}`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
      [`POST ${CATALOGO}/servicios/${ID}/imagenes`]: () =>
        respuestaJson(201, { imagenes: ['/api/v1/catalogo/imagenes/a1', '/api/v1/catalogo/imagenes/a2'] }),
      [`DELETE ${CATALOGO}/servicios/${ID}/imagenes`]: () =>
        respuestaJson(200, { imagenes: ['/api/v1/catalogo/imagenes/a2'] }),
    })

    const galeria = await screen.findByRole('region', { name: 'Galería' }, LENTO)
    expect(within(galeria).getByRole('button', { name: 'Quitar la imagen 1' })).toBeDisabled()

    const archivo = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'playa.png', { type: 'image/png' })
    await usuario.upload(within(galeria).getByLabelText('Elegir una imagen'), archivo)
    expect(await within(galeria).findByText('Imagen agregada.')).toBeInTheDocument()
    expect(within(galeria).getAllByRole('img')).toHaveLength(2)
    expect(solicitudes.find((s) => s.metodo === 'POST')?.formulario?.get('imagen')).toBeInstanceOf(File)

    await usuario.click(within(galeria).getByRole('button', { name: 'Quitar la imagen 1' }))
    expect(await within(galeria).findByText('Imagen quitada.')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'DELETE')?.url).toBe(
      `${CATALOGO}/servicios/${ID}/imagenes?ruta=%2Fapi%2Fv1%2Fcatalogo%2Fimagenes%2Fa1`,
    )
  })

  it('un archivo que no es JPEG ni PNG no se envía', async () => {
    const { solicitudes } = montar(`/prestador/servicios/${ID}`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, PUBLICADO),
    })
    const galeria = await screen.findByRole('region', { name: 'Galería' }, LENTO)
    const gif = new File(['GIF8'], 'a.gif', { type: 'image/gif' })
    fireEvent.change(within(galeria).getByLabelText('Elegir una imagen'), { target: { files: [gif] } })
    expect(await within(galeria).findByRole('alert')).toHaveTextContent('JPEG o PNG de hasta 5 MB')
    expect(solicitudes.some((s) => s.metodo === 'POST')).toBe(false)
  })

  it('un servicio Pendiente no muestra la galería ni la disponibilidad', async () => {
    montar(`/prestador/servicios/${ID}`, {
      [`GET ${CATALOGO}/servicios/${ID}`]: () => respuestaJson(200, servicioGestion()),
    })
    await screen.findByRole('heading', { level: 1, name: 'Tour de Café en Minca' }, LENTO)
    expect(screen.queryByRole('region', { name: 'Galería' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Gestionar disponibilidad' })).not.toBeInTheDocument()
  })
})
