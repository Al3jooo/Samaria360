import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { StrictMode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { App } from '../../../app/App'
import {
  CASCADA_CERRADA,
  DETALLE_HOTEL,
  HOTEL,
  TOUR_CAFE,
  paginaOferta,
  respuestaJson,
  simularFetch,
  ultimaConsultaCatalogo,
} from '../../../test/fetchSimulado'
import { simularGeolocalizacion } from '../../../test/geolocalizacionSimulada'
import { leafletSimulado, mapaVivo } from '../../../test/leafletSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const OFERTA = 'GET /api/v1/catalogo/oferta'
const CENTRAR = 'Centrar en mi ubicación'
const EL_RODADERO = { latitud: 11.2047, longitud: -74.2272 }
const BOGOTA = { latitud: 4.6097, longitud: -74.0817 }
const LEJOS = 'Estás lejos de Santa Marta: las distancias se calculan desde tu ubicación actual.'

function conResultados(elementos: unknown[] = [HOTEL, TOUR_CAFE, CASCADA_CERRADA], opciones = {}) {
  return simularFetch({ [OFERTA]: () => respuestaJson(200, paginaOferta(elementos, opciones)) })
}

async function cargarExplorar(ruta = '/explorar') {
  const resultado = renderApp(ruta)
  await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })
  // Los marcadores se agregan en un efecto del mapa, que puede correr después de que aparece la lista.
  await waitFor(() => expect(mapaVivo().marcadores().length).toBeGreaterThan(0))
  return resultado
}

const marcador = (titulo: string) => {
  const encontrado = mapaVivo()
    .marcadores()
    .find((m) => m.opciones.title === titulo)
  if (!encontrado) throw new Error(`Sin marcador «${titulo}»`)
  return encontrado
}

const puntosResultados = [HOTEL, TOUR_CAFE, CASCADA_CERRADA].map(({ coordenada }) => [coordenada.latitud, coordenada.longitud])

describe('Exploración WF-05: ubicación', () => {
  // Límite de 15 s: medida en más de 2 s (la primera del archivo paga la carga diferida de /explorar).
  it('no pide la ubicación al cargar la página', async () => {
    const getCurrentPosition = simularGeolocalizacion(EL_RODADERO)
    const { solicitudes } = conResultados()
    await cargarExplorar()

    expect(getCurrentPosition).not.toHaveBeenCalled()
    expect(ultimaConsultaCatalogo(solicitudes)).toBe('pagina=0&tamano=10')
  }, 15_000)

  it('al centrar, la API recibe lat y lng sin orden, rige «Menor distancia» y la ubicación no se guarda', async () => {
    const getCurrentPosition = simularGeolocalizacion(EL_RODADERO)
    const { solicitudes } = conResultados()
    const { usuario } = await cargarExplorar()

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    expect(getCurrentPosition).toHaveBeenCalledTimes(1)
    expect(getCurrentPosition.mock.calls[0][2]).toEqual({ enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 })
    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe('lat=11.2047&lng=-74.2272&pagina=0&tamano=10'),
    )
    expect(screen.getByRole('button', { name: 'Menor distancia' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Menor distancia' })).not.toHaveAttribute('aria-disabled')
    expect(screen.getByRole('button', { name: 'Nombre' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByLabelText('Distancia máxima')).toBeEnabled()
    expect(screen.queryByText('Comparte tu ubicación para filtrar y ordenar por distancia.')).not.toBeInTheDocument()
    expect(screen.getByText('Usando tu ubicación aproximada.').closest('[role="status"]')).not.toBeNull()

    const tuUbicacion = marcador('Tu ubicación')
    expect(tuUbicacion.opciones.icon.options.className).toContain('marcador--ubicacion')
    expect(tuUbicacion.posicion).toEqual([EL_RODADERO.latitud, EL_RODADERO.longitud])

    // Solo en memoria: ni en la URL ni en el almacenamiento del navegador.
    expect(ubicacionActual()).toBe('/explorar')
    const almacenado = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage })
    expect(almacenado).not.toContain('11.2047')
    expect(almacenado).not.toContain('-74.2272')
  })

  it('pulsar otra vez solo recentra el mapa, sin volver a pedir la ubicación', async () => {
    const getCurrentPosition = simularGeolocalizacion(EL_RODADERO)
    conResultados()
    const { usuario } = await cargarExplorar()

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))
    await screen.findByText('Usando tu ubicación aproximada.')
    // El primer centrado (al obtener la ubicación) se aplica en un efecto del mapa.
    await waitFor(() => expect(mapaVivo().vistas.length).toBeGreaterThan(0))
    const vistasAntes = mapaVivo().vistas.length
    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    expect(getCurrentPosition).toHaveBeenCalledTimes(1)
    expect(mapaVivo().vistas).toHaveLength(vistasAntes + 1)
    expect(mapaVivo().vistas.at(-1)?.centro).toEqual([EL_RODADERO.latitud, EL_RODADERO.longitud])
  })

  it('con un orden en la URL, obtener la ubicación no lo cambia', async () => {
    simularGeolocalizacion(EL_RODADERO)
    const { solicitudes } = conResultados()
    const { usuario } = await cargarExplorar('/explorar?orden=PrecioAscendente')

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe('lat=11.2047&lng=-74.2272&orden=PrecioAscendente&pagina=0&tamano=10'),
    )
    expect(screen.getByRole('button', { name: 'Precio: menor a mayor' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Menor distancia' })).toHaveAttribute('aria-pressed', 'false')
    expect(ubicacionActual()).toBe('/explorar?orden=PrecioAscendente')
  })

  it('obtener la ubicación vuelve a la primera página', async () => {
    simularGeolocalizacion(EL_RODADERO)
    const { solicitudes } = conResultados([HOTEL], { pagina: 2, totalElementos: 25 })
    const { usuario } = await cargarExplorar('/explorar?categoria=Tour&pagina=3')

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    await waitFor(() => expect(ubicacionActual()).toBe('/explorar?categoria=Tour'))
    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe('categoria=Tour&lat=11.2047&lng=-74.2272&pagina=0&tamano=10'),
    )
  })

  it('con ubicación, el filtro de distancia va a la URL y la API recibe lat, lng y distanciaKm', async () => {
    simularGeolocalizacion(EL_RODADERO)
    const { solicitudes } = conResultados()
    const { usuario } = await cargarExplorar()

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))
    await waitFor(() => expect(screen.getByLabelText('Distancia máxima')).toBeEnabled())
    fireEvent.change(screen.getByLabelText('Distancia máxima'), { target: { value: '20' } })
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(ubicacionActual()).toBe('/explorar?distanciaKm=20')
    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe('lat=11.2047&lng=-74.2272&distanciaKm=20&pagina=0&tamano=10'),
    )
  })

  it.each([
    [1, 'No diste permiso para usar tu ubicación. Puedes seguir explorando sin ella.'],
    [2, 'No pudimos determinar tu ubicación en este momento.'],
    [3, 'Obtener tu ubicación tardó demasiado. Inténtalo de nuevo.'],
  ] as const)('el error %i de geolocalización muestra su aviso sin bloquear la página', async (codigo, mensaje) => {
    simularGeolocalizacion({ codigo })
    const { solicitudes } = conResultados()
    const { usuario } = await cargarExplorar()

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    expect((await screen.findByText(mensaje)).closest('[role="status"]')).not.toBeNull()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Hotel Casa Tayrona' })).toBeInTheDocument()
    expect(screen.getByLabelText('Distancia máxima')).toBeDisabled()
    expect(ultimaConsultaCatalogo(solicitudes)).toBe('pagina=0&tamano=10')
    // Se puede volver a intentar.
    expect(screen.getByRole('button', { name: CENTRAR })).not.toHaveAttribute('aria-disabled')
  })

  it('sin geolocalización en el navegador, el botón queda deshabilitado con la razón', async () => {
    conResultados()
    const { usuario } = await cargarExplorar()

    const boton = screen.getByRole('button', { name: CENTRAR })
    expect(boton).toHaveAttribute('aria-disabled', 'true')
    expect(boton).toHaveAccessibleDescription('Tu navegador no permite compartir la ubicación.')
    await usuario.click(boton)
    expect(screen.queryByText('Usando tu ubicación aproximada.')).not.toBeInTheDocument()
  })

  it('lejos de Santa Marta (Bogotá): se usa para la API y se avisa, pero no entra en el encuadre', async () => {
    simularGeolocalizacion(BOGOTA)
    const { solicitudes } = conResultados()
    const { usuario } = await cargarExplorar()

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe('lat=4.6097&lng=-74.0817&pagina=0&tamano=10'),
    )
    expect((await screen.findByText(new RegExp(LEJOS))).closest('[role="status"]')).not.toBeNull()
    await waitFor(() => expect(mapaVivo().encuadres.length).toBeGreaterThanOrEqual(2))
    expect(mapaVivo().encuadres.at(-1)?.puntos).toEqual(puntosResultados)
    expect(marcador('Tu ubicación').posicion).toEqual([BOGOTA.latitud, BOGOTA.longitud])
    // Ni al obtenerla ni al pulsar otra vez se centra el mapa en Bogotá.
    await usuario.click(screen.getByRole('button', { name: CENTRAR }))
    expect(mapaVivo().vistas).toEqual([])
  })

  it('cerca de Santa Marta (El Rodadero): sin aviso y la ubicación entra en el encuadre', async () => {
    simularGeolocalizacion(EL_RODADERO)
    conResultados()
    const { usuario } = await cargarExplorar()

    await usuario.click(screen.getByRole('button', { name: CENTRAR }))

    await screen.findByText('Usando tu ubicación aproximada.')
    await waitFor(() => expect(mapaVivo().encuadres.length).toBeGreaterThanOrEqual(2))
    expect(screen.queryByText(new RegExp(LEJOS))).not.toBeInTheDocument()
    expect(mapaVivo().encuadres.at(-1)?.puntos).toEqual([
      ...puntosResultados,
      [EL_RODADERO.latitud, EL_RODADERO.longitud],
    ])
  })
})

describe('Exploración WF-05: mapa', () => {
  it('dibuja un marcador por resultado, distinto para servicio y atractivo, y encuadra los resultados', async () => {
    conResultados()
    await cargarExplorar()

    const mapa = mapaVivo()
    expect(mapa.marcadores().map((m) => m.opciones.title)).toEqual([
      'Hotel Casa Tayrona',
      'Tour de Café en Minca',
      'Cascada El Salto',
    ])
    expect(marcador('Hotel Casa Tayrona').opciones.icon.options.className).toContain('marcador--servicio')
    expect(marcador('Cascada El Salto').opciones.icon.options.className).toContain('marcador--atractivo')
    expect(marcador('Cascada El Salto').posicion).toEqual([11.15, -74.11])
    expect(marcador('Cascada El Salto').opciones.keyboard).toBe(true)
    expect(marcador('Cascada El Salto').getElement()).toHaveAttribute('aria-label', 'Cascada El Salto')
    expect(mapa.encuadres.at(-1)).toEqual({ puntos: puntosResultados, opciones: { padding: [32, 32], maxZoom: 15 } })
  })

  it('la ventana emergente muestra el nombre como texto, nunca como HTML', async () => {
    const nombre = 'Hotel <img src=x onerror="alert(1)">'
    conResultados([{ ...HOTEL, nombre }])
    renderApp('/explorar')
    await screen.findByRole('heading', { name: nombre })
    await waitFor(() => marcador(nombre))

    const popup = marcador(nombre).abrirPopup()
    expect(popup.querySelector('.popup-oferta__nombre')!.textContent).toBe(nombre)
    // La única imagen es la miniatura: el nombre no se interpretó como HTML.
    const imagenes = popup.querySelectorAll('img')
    expect(imagenes).toHaveLength(1)
    expect(imagenes[0]).toHaveClass('popup-oferta__miniatura')
    expect(imagenes[0]).toHaveAttribute('alt', `Foto de ${nombre}`)
  })

  // RF23: «desplegando el nombre y una imagen miniatura al hacer clic sobre el marcador».
  it('la ventana emergente de un servicio trae miniatura de tamaño fijo, nombre y «Ver detalle»', async () => {
    conResultados()
    await cargarExplorar('/explorar?texto=hotel')

    const popup = within(marcador('Hotel Casa Tayrona').abrirPopup())
    const miniatura = popup.getByRole('img', { name: 'Foto de Hotel Casa Tayrona' })
    expect(miniatura).toHaveAttribute('src', HOTEL.imagen)
    expect(miniatura).toHaveAttribute('width', '120')
    expect(miniatura).toHaveAttribute('height', '80')
    // La ventana emergente no está montada en el documento (Leaflet la monta al abrirse): se revisa su contenido.
    expect(popup.getByText('Hotel Casa Tayrona')).toHaveClass('popup-oferta__nombre')
    expect(popup.getByRole('link', { name: 'Ver detalle' })).toHaveAttribute('href', '/servicios/s-hotel')

    // Si la foto no carga, la ilustración de su categoría.
    fireEvent.error(miniatura)
    expect(miniatura).toHaveAttribute('src', '/imagenes/catalogo/categorias/alojamiento.svg')
  })

  it('un atractivo tiene «Ver detalle» hacia su ficha en la ventana emergente (DEC-224)', async () => {
    conResultados()
    await cargarExplorar()

    const popup = within(marcador('Cascada El Salto').abrirPopup())
    expect(popup.getByRole('img', { name: 'Foto de Cascada El Salto' })).toHaveAttribute('src', CASCADA_CERRADA.imagen)
    expect(popup.getByRole('link', { name: 'Ver detalle' })).toHaveAttribute('href', `/atractivos/${CASCADA_CERRADA.id}`)
  })

  it('«Ver detalle» de la ventana emergente navega al detalle y recuerda la búsqueda', async () => {
    simularFetch({
      [OFERTA]: () => respuestaJson(200, paginaOferta([HOTEL, TOUR_CAFE, CASCADA_CERRADA])),
      'GET /api/v1/catalogo/servicios/s-hotel': () => respuestaJson(200, DETALLE_HOTEL),
    })
    await cargarExplorar('/explorar?texto=hotel')

    const enlace = within(marcador('Hotel Casa Tayrona').abrirPopup()).getByRole('link', { name: 'Ver detalle' })
    act(() => {
      enlace.click()
    })
    // La navegación va en una transición: la ruta cambia cuando llega la página diferida del detalle.
    await waitFor(() => expect(ubicacionActual()).toBe('/servicios/s-hotel'))
    expect(await screen.findByRole('heading', { level: 1, name: 'Hotel Casa Tayrona' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Volver a la búsqueda/ })).toHaveAttribute('href', '/explorar?texto=hotel')
  })

  it('usa las teselas de OSM con zoom máximo 19 y su atribución', async () => {
    conResultados()
    await cargarExplorar()

    const mapa = mapaVivo()
    expect(mapa.opciones.maxZoom).toBe(19)
    const [teselas] = mapa.teselas()
    expect(teselas.url).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png')
    expect(teselas.opciones.maxZoom).toBe(19)
    expect(teselas.opciones.attribution).toContain('OpenStreetMap</a> contributors')
    expect(teselas.opciones.attribution).toContain('https://www.openstreetmap.org/copyright')
  })

  it('la rueda no hace zoom hasta enfocar o pulsar el mapa, y deja de hacerlo al salir', async () => {
    conResultados()
    await cargarExplorar()

    const mapa = mapaVivo()
    expect(mapa.contenedor).toHaveAttribute('tabindex', '0')
    expect(mapa.contenedor).toHaveAccessibleName('Mapa interactivo de afluencia y atractivos')
    expect(mapa.contenedor).toHaveAccessibleDescription('Haz clic en el mapa o enfócalo para usar la rueda.')
    expect(mapa.scrollWheelZoom.enabled()).toBe(false)

    mapa.fire('focus')
    expect(mapa.scrollWheelZoom.enabled()).toBe(true)
    mapa.fire('blur')
    expect(mapa.scrollWheelZoom.enabled()).toBe(false)
    mapa.fire('click')
    expect(mapa.scrollWheelZoom.enabled()).toBe(true)
    mapa.fire('mouseout')
    expect(mapa.scrollWheelZoom.enabled()).toBe(false)
  })

  it('el control de zoom está traducido y se reemplaza al cambiar de idioma', async () => {
    conResultados()
    const { usuario } = await cargarExplorar()

    expect(mapaVivo().controles.map((c) => c.opciones)).toEqual([
      { position: 'topright', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' },
    ])
    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))

    expect(mapaVivo().controles.map((c) => c.opciones)).toEqual([
      { position: 'topright', zoomInTitle: 'Zoom in', zoomOutTitle: 'Zoom out' },
    ])
    expect(leafletSimulado.mapas).toHaveLength(1)
  })

  it('pulsar un marcador resalta y enfoca su tarjeta; enfocar una tarjeta resalta su marcador', async () => {
    conResultados()
    await cargarExplorar()

    act(() => {
      marcador('Cascada El Salto').fire('click')
    })
    const cascada = screen.getByRole('article', { name: 'Cascada El Salto' })
    expect(cascada).toHaveClass('tarjeta-oferta--resaltada')
    expect(cascada).toHaveFocus()
    expect(marcador('Cascada El Salto').getElement()).toHaveClass('marcador--resaltado')

    const hotel = screen.getByRole('article', { name: 'Hotel Casa Tayrona' })
    act(() => {
      within(hotel).getByRole('link', { name: 'Ver detalle' }).focus()
    })
    expect(marcador('Hotel Casa Tayrona').getElement()).toHaveClass('marcador--resaltado')
    expect(marcador('Hotel Casa Tayrona').zIndexOffset).toBeGreaterThan(0)
    expect(marcador('Cascada El Salto').getElement()).not.toHaveClass('marcador--resaltado')
    expect(marcador('Cascada El Salto').zIndexOffset).toBe(0)
    expect(cascada).not.toHaveClass('tarjeta-oferta--resaltada')

    fireEvent.mouseEnter(screen.getByRole('article', { name: 'Tour de Café en Minca' }))
    expect(marcador('Tour de Café en Minca').getElement()).toHaveClass('marcador--resaltado')
  })

  it('cambiar de página actualiza los marcadores y el encuadre sin crear otro mapa', async () => {
    simularFetch({
      [OFERTA]: ({ url }) =>
        respuestaJson(
          200,
          url.includes('pagina=1')
            ? paginaOferta([CASCADA_CERRADA], { pagina: 1, totalElementos: 11 })
            : paginaOferta([HOTEL, TOUR_CAFE], { totalElementos: 11 }),
        ),
    })
    const { usuario } = await cargarExplorar()
    const mapa = mapaVivo()
    const encuadresAntes = mapa.encuadres.length

    const paginacion = screen.getByRole('navigation', { name: 'Páginas de resultados' })
    await usuario.click(within(paginacion).getByRole('button', { name: 'Página 2' }))
    await screen.findByRole('heading', { name: 'Cascada El Salto' })

    await waitFor(() => expect(mapa.marcadores().map((m) => m.opciones.title)).toEqual(['Cascada El Salto']))
    expect(leafletSimulado.mapas).toHaveLength(1)
    expect(mapaVivo()).toBe(mapa)
    expect(mapa.encuadres.length).toBeGreaterThan(encuadresAntes)
    expect(mapa.encuadres.at(-1)?.puntos).toEqual([[11.15, -74.11]])
  })

  it('con StrictMode queda un solo mapa vivo (el del primer montaje se elimina)', async () => {
    conResultados()
    render(
      <StrictMode>
        <MemoryRouter initialEntries={['/explorar']}>
          <App />
        </MemoryRouter>
      </StrictMode>,
    )
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    expect(leafletSimulado.mapas).toHaveLength(2)
    expect(leafletSimulado.mapas.filter((mapa) => mapa.eliminado)).toHaveLength(1)
    // Los marcadores se agregan en un efecto del mapa, que puede correr después de que aparece la lista.
    await waitFor(() => expect(mapaVivo().marcadores()).toHaveLength(3))
    expect(mapaVivo().controles).toHaveLength(1)
  })

  it('«Lista / Mapa» alterna la vista y, al mostrar el mapa, recalcula su tamaño', async () => {
    conResultados()
    const { usuario } = await cargarExplorar()

    const selector = screen.getByRole('group', { name: 'Vista de resultados' })
    const lista = within(selector).getByRole('button', { name: 'Lista' })
    const mapaBoton = within(selector).getByRole('button', { name: 'Mapa' })
    const cuerpo = screen.getByRole('region', { name: /Resultados en Santa Marta/ }).parentElement!
    expect(lista).toHaveAttribute('aria-pressed', 'true')
    expect(cuerpo).toHaveAttribute('data-vista', 'lista')
    const invalidacionesAntes = mapaVivo().invalidaciones

    await usuario.click(mapaBoton)

    expect(mapaBoton).toHaveAttribute('aria-pressed', 'true')
    expect(lista).toHaveAttribute('aria-pressed', 'false')
    expect(cuerpo).toHaveAttribute('data-vista', 'mapa')
    expect(mapaVivo().invalidaciones).toBe(invalidacionesAntes + 1)

    await usuario.click(lista)
    expect(cuerpo).toHaveAttribute('data-vista', 'lista')
  })
})
