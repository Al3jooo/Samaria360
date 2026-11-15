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
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const OFERTA = 'GET /api/v1/catalogo/oferta'

function conResultados(elementos: unknown[] = [HOTEL, TOUR_CAFE, CASCADA_CERRADA], opciones = {}) {
  return simularFetch({ [OFERTA]: () => respuestaJson(200, paginaOferta(elementos, opciones)) })
}

describe('Exploración WF-05', () => {
  // Límite de 15 s: medida en más de 2 s (primera del archivo: paga la carga diferida de /explorar).
  it('los filtros arman la consulta a la API y se reflejan en la URL', async () => {
    const { solicitudes } = conResultados()
    const { usuario } = renderApp('/explorar')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.type(screen.getByLabelText('Nombre, vereda o actividad'), 'café')
    fireEvent.change(screen.getByLabelText('Precio máximo'), { target: { value: '150000' } })
    await usuario.click(screen.getByLabelText('Movilidad reducida'))
    await usuario.click(screen.getByLabelText('Discapacidad visual'))
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() =>
      expect(ultimaConsultaCatalogo(solicitudes)).toBe(
        'texto=caf%C3%A9&precioMax=150000&accesibilidad=MovilidadReducida&accesibilidad=Visual&pagina=0&tamano=10',
      ),
    )
    expect(ubicacionActual()).toBe(
      '/explorar?texto=caf%C3%A9&precioMax=150000&accesibilidad=MovilidadReducida&accesibilidad=Visual',
    )
  }, 15_000)

  it('al cargar desde la URL, la página empieza en 1 en la URL y en 0 en la API', async () => {
    const { solicitudes } = conResultados([TOUR_CAFE], { pagina: 1, totalElementos: 11 })
    renderApp('/explorar?categoria=Tour&pagina=2')

    await screen.findByRole('heading', { name: 'Tour de Café en Minca' })
    expect(ultimaConsultaCatalogo(solicitudes)).toBe('categoria=Tour&pagina=1&tamano=10')
    expect(screen.getByRole('button', { name: 'Quitar el filtro de categoría Tours' })).toHaveTextContent('Categoría: Tours')
  })

  it('el chip de categoría quita el filtro y vuelve a la primera página', async () => {
    const { solicitudes } = conResultados()
    const { usuario } = renderApp('/explorar?categoria=Tour&pagina=2')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    await usuario.click(screen.getByRole('button', { name: 'Quitar el filtro de categoría Tours' }))

    expect(ubicacionActual()).toBe('/explorar')
    await waitFor(() => expect(ultimaConsultaCatalogo(solicitudes)).toBe('pagina=0&tamano=10'))
  })

  it('la paginación cambia la URL y la consulta', async () => {
    const { solicitudes } = conResultados([HOTEL], { totalElementos: 25 })
    const { usuario } = renderApp('/explorar?orden=PrecioAscendente')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    const paginacion = screen.getByRole('navigation', { name: 'Páginas de resultados' })
    expect(within(paginacion).getByRole('button', { name: 'Página 1' })).toHaveAttribute('aria-current', 'page')
    await usuario.click(within(paginacion).getByRole('button', { name: 'Página 2' }))

    expect(ubicacionActual()).toBe('/explorar?orden=PrecioAscendente&pagina=2')
    await waitFor(() => expect(ultimaConsultaCatalogo(solicitudes)).toBe('orden=PrecioAscendente&pagina=1&tamano=10'))
  })

  it('el orden por precio y por nombre cambia la URL y vuelve a la página 1', async () => {
    const { solicitudes } = conResultados()
    const { usuario } = renderApp('/explorar?pagina=3')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    // Sin orden en la URL rige el de la API sin ubicación: por nombre.
    expect(screen.getByRole('button', { name: 'Nombre' })).toHaveAttribute('aria-pressed', 'true')

    await usuario.click(screen.getByRole('button', { name: 'Precio: mayor a menor' }))
    expect(ubicacionActual()).toBe('/explorar?orden=PrecioDescendente')
    await waitFor(() => expect(ultimaConsultaCatalogo(solicitudes)).toBe('orden=PrecioDescendente&pagina=0&tamano=10'))
    expect(screen.getByRole('button', { name: 'Precio: mayor a menor' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('sin ubicación, la distancia y «Menor distancia» quedan deshabilitadas con una explicación', async () => {
    const { solicitudes } = conResultados()
    renderApp('/explorar?orden=Distancia&distanciaKm=5')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    expect(screen.getByLabelText('Distancia máxima')).toBeDisabled()
    expect(screen.getByLabelText('Distancia máxima')).toHaveAccessibleDescription(
      'Comparte tu ubicación para filtrar y ordenar por distancia.',
    )
    const menorDistancia = screen.getByRole('button', { name: 'Menor distancia' })
    expect(menorDistancia).toHaveAttribute('aria-disabled', 'true')
    expect(menorDistancia).toHaveAccessibleDescription('Comparte tu ubicación para filtrar y ordenar por distancia.')
    // Un enlace con distancia u orden por distancia no los envía a la API si no hay ubicación.
    expect(ultimaConsultaCatalogo(solicitudes)).toBe('pagina=0&tamano=10')
    expect(screen.getByRole('button', { name: /Menor afluencia/ })).toHaveAttribute('aria-disabled', 'true')
    // RF61 (DEC-302): el orden por calificación ya funciona.
    expect(screen.getByRole('button', { name: /Calificación/ })).not.toHaveAttribute('aria-disabled')
  })

  it('el atractivo cerrado muestra su insignia de estado', async () => {
    conResultados()
    renderApp('/explorar')

    const tarjeta = (await screen.findByRole('heading', { name: 'Cascada El Salto' })).closest('article')!
    expect(within(tarjeta).getByText('Cerrado temporalmente')).toBeInTheDocument()
    expect(within(tarjeta).queryByText(/Desde/)).not.toBeInTheDocument()
  })

  it('el precio se formatea en pesos colombianos según el idioma (es-CO y en-US)', async () => {
    conResultados([HOTEL])
    const { usuario } = renderApp('/explorar')

    const espanol = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
    const ingles = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
    // Intl usa un espacio duro (U+00A0); Testing Library normaliza el texto del DOM pero no la cadena esperada.
    const normalizar = (texto: string) => texto.replace(/\s/g, ' ')
    const precio = await screen.findByText(normalizar(`Desde ${espanol.format(150000)}`))
    expect(precio.textContent).toMatch(/^Desde \$\s150\.000$/)

    await usuario.click(screen.getByRole('button', { name: 'Inglés' }))

    expect(screen.getByText(normalizar(`From ${ingles.format(150000)}`)).textContent).toMatch(/^From COP\s150,000$/)
  })

  it('un 503 muestra el mensaje y «Reintentar» vuelve a consultar', async () => {
    let llamadas = 0
    simularFetch({
      [OFERTA]: () => {
        llamadas += 1
        return llamadas === 1
          ? respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE', { servicio: 'ms-catalog' })
          : respuestaJson(200, paginaOferta([HOTEL]))
      },
    })
    const { usuario } = renderApp('/explorar')

    expect(await screen.findByText('El catálogo no está disponible en este momento.')).toBeInTheDocument()
    // El resto de la app sigue funcionando.
    expect(screen.getByRole('link', { name: 'Inicio de sesión' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })).toBeInTheDocument()
    expect(llamadas).toBe(2)
  })

  it('un 400 indica revisar los filtros y permite quitarlos', async () => {
    simularFetch({ [OFERTA]: () => respuestaProblema(400, 'CRITERIOS_INVALIDOS') })
    const { usuario } = renderApp('/explorar?texto=x')

    expect(await screen.findByText('Revisa los filtros: alguno no es válido.')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Quitar filtros' }))

    expect(ubicacionActual()).toBe('/explorar')
  })

  it('sin resultados sugiere quitar los filtros', async () => {
    conResultados([], { totalElementos: 0 })
    const { usuario } = renderApp('/explorar?accesibilidad=Auditiva')

    expect(await screen.findByText('No encontramos lugares con esos filtros.')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Quitar filtros' }))
    expect(ubicacionActual()).toBe('/explorar')
  })

  it('una página fuera de rango ofrece ir a la primera página', async () => {
    conResultados([], { pagina: 8, totalElementos: 12 })
    const { usuario } = renderApp('/explorar?categoria=Tour&pagina=9')

    expect(await screen.findByText('Esta página ya no existe: los resultados cambiaron.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quitar filtros' })).not.toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Ir a la primera página' }))

    expect(ubicacionActual()).toBe('/explorar?categoria=Tour')
  })

  it('el contador usa plurales y está en una región aria-live', async () => {
    conResultados([HOTEL], { totalElementos: 12 })
    renderApp('/explorar')

    const contador = await screen.findByRole('heading', { name: 'Resultados en Santa Marta (12 lugares encontrados)' })
    expect(contador.closest('[aria-live="polite"]')).not.toBeNull()
  })

  it('con un solo resultado el contador está en singular, en español y en inglés', async () => {
    conResultados([HOTEL], { totalElementos: 1 })
    const { usuario } = renderApp('/explorar')

    expect(await screen.findByRole('heading', { name: 'Resultados en Santa Marta (1 lugar encontrado)' })).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Inglés' }))
    expect(screen.getByRole('heading', { name: 'Results in Santa Marta (1 place found)' })).toBeInTheDocument()
  })

  // WF-05 v2: dos niveles (Samaria360 › Exploración interactiva); la v1 tenía un tercero, «Mapa y resultados».
  it('las migas de pan son las de la v2 y llevan al inicio', async () => {
    conResultados()
    renderApp('/explorar')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    const migas = screen.getByRole('navigation', { name: 'Migas de pan' })
    expect(within(migas).getAllByRole('listitem')).toHaveLength(2)
    expect(within(migas).getByRole('link', { name: 'Samaria360' })).toHaveAttribute('href', '/')
    expect(within(migas).getByText('Exploración interactiva')).toHaveAttribute('aria-current', 'page')
    expect(within(migas).queryByText('Mapa y resultados')).not.toBeInTheDocument()
  })

  // WF-05 v2 ya no tiene «¿Preguntas sobre el estado de las vías…? Preguntar al asistente».
  it('no muestra la invitación a preguntar al asistente que la v2 quitó', async () => {
    conResultados()
    renderApp('/explorar')
    await screen.findByRole('heading', { name: 'Hotel Casa Tayrona' })

    expect(screen.queryByText(/Preguntas sobre el estado de las vías/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Preguntar al asistente/ })).not.toBeInTheDocument()
  })
})
