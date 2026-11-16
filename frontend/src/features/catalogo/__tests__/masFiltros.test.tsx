import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  CASCADA_CERRADA,
  HOTEL,
  TOUR_CAFE,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
  ultimaConsultaCatalogo,
  type SolicitudSimulada,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const OFERTA = 'GET /api/v1/catalogo/oferta'
const FILTROS = 'GET /api/v1/catalogo/filtros'

/** Como GET /api/v1/catalogo/filtros con la semilla de infra/mongo/init. */
const OPCIONES: Record<string, unknown> = {
  Alojamiento: {
    categoria: 'Alojamiento',
    serviciosIncluidos: ['WiFi', 'Desayuno', 'Piscina', 'Parqueadero'],
    tiposHabitacion: ['Cabaña', 'Compartida', 'Doble', 'Ecohabitación'],
    huespedes: { minimo: 2, maximo: 6 },
  },
  Gastronomia: { categoria: 'Gastronomia', opcionVegetariana: true, tiposCocina: ['Colombiana', 'Mariscos', 'Mediterránea'] },
  Tour: { categoria: 'Tour', nivelesDificultad: ['Alta', 'Media'], duracionHoras: { minimo: 4, maximo: 8 } },
  AlquilerEquipo: { categoria: 'AlquilerEquipo', tiposVehiculoEquipo: ['Bicicleta urbana'] },
}

const consulta = (solicitud: SolicitudSimulada) => new URLSearchParams(solicitud.url.split('?')[1] ?? '')

/** Con categoría, la API solo devuelve servicios (decisión 2); sin ella, también el atractivo. */
function simularCatalogo(opciones: (solicitud: SolicitudSimulada) => Response = (s) =>
  respuestaJson(200, OPCIONES[consulta(s).get('categoria') ?? ''])) {
  return simularFetch({
    [OFERTA]: (s) =>
      respuestaJson(200, paginaOferta(consulta(s).has('categoria') ? [HOTEL, TOUR_CAFE] : [HOTEL, TOUR_CAFE, CASCADA_CERRADA])),
    [FILTROS]: opciones,
  })
}

const botonMasFiltros = () => screen.getByRole('button', { name: /^Más filtros/ })
/** El panel que controla el botón (en cualquier idioma). */
const panel = () => {
  const boton = screen.getByRole('button', { name: /^(Más filtros|More filters)/ })
  return document.getElementById(boton.getAttribute('aria-controls') ?? '') as HTMLElement
}
const pedidasOpciones = (solicitudes: SolicitudSimulada[]) =>
  solicitudes.filter((s) => s.url.startsWith('/api/v1/catalogo/filtros')).map((s) => s.url)

describe('«Más filtros» (WF-05, RF10)', () => {
  // Límite de 15 s: medida en más de 2 s (primera del archivo: paga la carga diferida de /explorar).
  it('el botón despliega el panel con aria-expanded; el foco entra al abrir y vuelve con Escape o «Cerrar»', async () => {
    simularCatalogo()
    const { usuario } = renderApp('/explorar')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    const boton = botonMasFiltros()
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(panel()).not.toBeVisible()

    await usuario.click(boton)
    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(panel()).toBeVisible()
    expect(within(panel()).getByRole('radio', { name: 'Todas (incluye atractivos)' })).toHaveFocus()

    await usuario.keyboard('{Escape}')
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(panel()).not.toBeVisible()
    expect(boton).toHaveFocus()

    await usuario.click(boton)
    await usuario.click(within(panel()).getByRole('button', { name: 'Cerrar' }))
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(boton).toHaveFocus()
  }, 15_000)

  it('sin categoría invita a elegir una y no pide opciones', async () => {
    const { solicitudes } = simularCatalogo()
    const { usuario } = renderApp('/explorar')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(botonMasFiltros())

    expect(within(panel()).getByText(/^Elige una categoría para ver sus filtros\./)).toBeInTheDocument()
    expect(within(panel()).getByRole('radio', { name: 'Todas (incluye atractivos)' })).toBeChecked()
    expect(pedidasOpciones(solicitudes)).toEqual([])
  })

  // Límite de 15 s: medida en más de 2 s (muchas interacciones de usuario y dos búsquedas).
  it('las opciones salen del backend y los filtros van a la URL y a la API; los chips los quitan', async () => {
    const { solicitudes } = simularCatalogo()
    const { usuario } = renderApp('/explorar')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(botonMasFiltros())
    await usuario.click(within(panel()).getByRole('radio', { name: 'Alojamiento' }))

    await usuario.click(await within(panel()).findByRole('checkbox', { name: 'Piscina' }))
    expect(pedidasOpciones(solicitudes)).toEqual(['/api/v1/catalogo/filtros?categoria=Alojamiento'])
    expect(within(panel()).getAllByRole('checkbox').map((c) => c.closest('label')?.textContent)).toEqual([
      'WiFi',
      'Desayuno',
      'Piscina',
      'Parqueadero',
    ])
    const huespedes = within(panel()).getByLabelText('Huéspedes')
    expect(huespedes).toHaveAttribute('max', '6')
    await usuario.type(huespedes, '3')
    const tipo = within(panel()).getByLabelText('Tipo de habitación')
    expect(within(tipo).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Cualquiera',
      'Cabaña',
      'Compartida',
      'Doble',
      'Ecohabitación',
    ])
    await usuario.selectOptions(tipo, 'Cabaña')
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(ubicacionActual()).toBe(
      '/explorar?categoria=Alojamiento&serviciosIncluidos=Piscina&huespedes=3&tipoHabitacion=Caba%C3%B1a',
    )
    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe(
        'categoria=Alojamiento&serviciosIncluidos=Piscina&huespedes=3&tipoHabitacion=Caba%C3%B1a&pagina=0&tamano=10',
      ),
    )
    expect(botonMasFiltros()).toHaveAccessibleName('Más filtros, 3 activos')

    await usuario.click(screen.getByRole('button', { name: 'Quitar el filtro 3 huéspedes' }))
    expect(ubicacionActual()).toBe('/explorar?categoria=Alojamiento&serviciosIncluidos=Piscina&tipoHabitacion=Caba%C3%B1a')
    expect(screen.getByRole('button', { name: 'Quitar el filtro Piscina' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quitar el filtro Tipo de habitación: Cabaña' })).toBeInTheDocument()
  }, 15_000)

  it('al cambiar de categoría se limpian los filtros que ya no aplican', async () => {
    simularCatalogo()
    const { usuario } = renderApp('/explorar?categoria=Alojamiento&serviciosIncluidos=WiFi&huespedes=2')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(botonMasFiltros())
    expect(await within(panel()).findByRole('checkbox', { name: 'WiFi' })).toBeChecked()
    expect(within(panel()).getByLabelText('Huéspedes')).toHaveValue(2)

    await usuario.click(within(panel()).getByRole('radio', { name: 'Gastronomía' }))
    expect(await within(panel()).findByRole('checkbox', { name: 'Opción vegetariana' })).not.toBeChecked()
    expect(within(panel()).queryByRole('checkbox', { name: 'WiFi' })).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(ubicacionActual()).toBe('/explorar?categoria=Gastronomia')
  })

  it('«Todas» quita la categoría y sus filtros, y vuelven los atractivos', async () => {
    const { solicitudes } = simularCatalogo()
    const { usuario } = renderApp('/explorar?categoria=Tour&nivelDificultad=Media')
    await screen.findByRole('heading', { name: 'Tour de Café en Minca' })
    expect(screen.queryByRole('heading', { name: 'Cascada El Salto' })).not.toBeInTheDocument()

    await usuario.click(botonMasFiltros())
    expect(within(panel()).getByRole('radio', { name: 'Tours' })).toHaveFocus()
    await usuario.click(within(panel()).getByRole('radio', { name: 'Todas (incluye atractivos)' }))
    expect(within(panel()).queryByLabelText('Nivel de dificultad')).not.toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(ubicacionActual()).toBe('/explorar')
    expect(await screen.findByRole('heading', { name: 'Cascada El Salto' })).toBeInTheDocument()
    expect(ultimaConsultaCatalogo(solicitudes)).toBe('pagina=0&tamano=10')
  })

  it.each([
    [
      'de otra categoría',
      '/explorar?categoria=Gastronomia&serviciosIncluidos=WiFi&tipoCocina=Mariscos',
      '/explorar?categoria=Gastronomia&tipoCocina=Mariscos',
    ],
    ['sin categoría', '/explorar?texto=mar&huespedes=2&menuVegetariano=true', '/explorar?texto=mar'],
    ['con un valor inválido', '/explorar?categoria=Alojamiento&serviciosIncluidos=Jacuzzi&huespedes=0', '/explorar?categoria=Alojamiento'],
  ])('una URL con filtros %s se limpia sin mostrar un error', async (_caso, url, limpia) => {
    const { solicitudes } = simularCatalogo()
    renderApp(url)

    await waitFor(() => expect(ubicacionActual()).toBe(limpia))
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })
    const consultas = solicitudes.filter((s) => s.url.startsWith('/api/v1/catalogo/oferta')).map((s) => s.url)
    for (const descartado of ['serviciosIncluidos', 'huespedes', 'menuVegetariano']) {
      expect(consultas.some((u) => u.includes(descartado))).toBe(false)
    }
    expect(screen.queryByText('Revisa los filtros: alguno no es válido.')).not.toBeInTheDocument()
  })

  it('si las opciones no cargan, avisa y permite reintentar', async () => {
    let intentos = 0
    simularCatalogo((s) => {
      intentos += 1
      return intentos === 1
        ? respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-catalog' })
        : respuestaJson(200, OPCIONES[consulta(s).get('categoria') ?? ''])
    })
    const { usuario } = renderApp('/explorar?categoria=AlquilerEquipo')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(botonMasFiltros())
    expect(await within(panel()).findByText('No pudimos cargar las opciones de esta categoría.')).toBeInTheDocument()
    await usuario.click(within(panel()).getByRole('button', { name: 'Reintentar' }))

    const tipo = await within(panel()).findByLabelText('Tipo de vehículo o equipo')
    await usuario.selectOptions(tipo, 'Bicicleta urbana')
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(ubicacionActual()).toBe('/explorar?categoria=AlquilerEquipo&tipoVehiculoEquipo=Bicicleta+urbana')
  })

  it('Gastronomía: opción vegetariana y tipo de cocina', async () => {
    const { solicitudes } = simularCatalogo()
    const { usuario } = renderApp('/explorar?categoria=Gastronomia')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(botonMasFiltros())
    await usuario.click(await within(panel()).findByRole('checkbox', { name: 'Opción vegetariana' }))
    await usuario.selectOptions(within(panel()).getByLabelText('Tipo de cocina'), 'Mediterránea')
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(ubicacionActual()).toBe('/explorar?categoria=Gastronomia&menuVegetariano=true&tipoCocina=Mediterr%C3%A1nea')
    await waitFor(() => expect(ultimaConsultaCatalogo(solicitudes)).toContain('menuVegetariano=true&tipoCocina='))
    expect(screen.getByRole('button', { name: 'Quitar el filtro Opción vegetariana' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quitar el filtro Tipo de cocina: Mediterránea' })).toBeInTheDocument()
  })

  it('Tours: nivel de dificultad y duración máxima con un deslizador entre el mínimo y el máximo', async () => {
    simularCatalogo()
    const { usuario } = renderApp('/explorar?categoria=Tour')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(botonMasFiltros())
    const duracion = await within(panel()).findByLabelText('Duración máxima')
    expect(duracion).toHaveAttribute('min', '4')
    expect(duracion).toHaveAttribute('max', '8')
    expect(duracion).toHaveAttribute('aria-valuetext', 'Sin límite')
    fireEvent.change(duracion, { target: { value: '5.5' } })
    expect(duracion).toHaveAttribute('aria-valuetext', 'Hasta 5,5 h')
    await usuario.selectOptions(within(panel()).getByLabelText('Nivel de dificultad'), 'Media')
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(ubicacionActual()).toBe('/explorar?categoria=Tour&nivelDificultad=Media&duracionMaxHoras=5.5')
    expect(screen.getByRole('button', { name: 'Quitar el filtro Duración máxima: Hasta 5,5 h' })).toBeInTheDocument()
  })

  it('funciona en inglés: etiquetas, servicios incluidos traducidos y chips', async () => {
    simularCatalogo()
    const { usuario } = renderApp('/explorar?categoria=Alojamiento&serviciosIncluidos=Desayuno&huespedes=1')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))
    expect(screen.getByRole('button', { name: 'Remove the filter Breakfast' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove the filter 1 guest' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: /^More filters/ }))
    expect(panel()).toHaveAccessibleName('Filters by category')
    expect(await within(panel()).findByRole('checkbox', { name: 'Breakfast' })).toBeChecked()
    expect(within(panel()).getByRole('group', { name: 'Included services' })).toBeInTheDocument()
    expect(within(panel()).getByLabelText('Guests')).toHaveValue(1)
  })
})
