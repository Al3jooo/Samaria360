import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  DETALLE_HOTEL,
  DETALLE_RESTAURANTE,
  DETALLE_TOUR,
  HOTEL,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const DETALLE = 'GET /api/v1/catalogo/servicios'

describe('Detalle de un servicio (WF-06)', () => {
  it('un alojamiento muestra sus datos, sus unidades y «Ver disponibilidad y reservar»', async () => {
    simularFetch({ [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL) })
    renderApp('/servicios/s-hotel')

    expect(await screen.findByRole('heading', { level: 1, name: 'Hotel Casa Tayrona' })).toBeInTheDocument()
    const ficha = screen.getByRole('article', { name: 'Hotel Casa Tayrona' })
    expect(within(ficha).getByText('Alojamiento')).toHaveClass('detalle__categoria')

    const descripcion = screen.getByRole('region', { name: 'Descripción' })
    expect(within(descripcion).getByText('Alojamiento boutique cerca del Parque de los Novios.')).toBeInTheDocument()
    const incluidos = within(descripcion).getByRole('list', { name: 'Servicios incluidos' })
    expect(within(incluidos).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['WiFi', 'Desayuno'])

    const precio = screen.getByRole('region', { name: 'Precio' })
    expect(within(precio).getByText(/^Desde \$\s150\.000$/)).toBeInTheDocument()
    const unidades = within(precio).getByRole('list', { name: 'Habitaciones' })
    expect(within(unidades).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)).toEqual([
      'Doble',
      'Suite familiar',
    ])
    // El precio llega como texto con centavos: se muestra exacto.
    expect(within(unidades).getByText(/260\.000,50 por noche/)).toBeInTheDocument()
    expect(within(unidades).getByText('4 huéspedes por habitación')).toBeInTheDocument()
    // «Ver disponibilidad y reservar» va en la región «Precio», como en WF-06.
    expect(within(precio).getByRole('link', { name: 'Ver disponibilidad y reservar' })).toHaveAttribute(
      'href',
      '/servicios/s-hotel/reservar',
    )
    // La segunda imagen es una miniatura con texto alternativo.
    expect(screen.getByRole('img', { name: 'Foto 2 de Hotel Casa Tayrona' })).toBeInTheDocument()
  })

  it('las regiones siguen el orden de WF-06', async () => {
    simularFetch({ [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL) })
    renderApp('/servicios/s-hotel')

    const ficha = await screen.findByRole('article', { name: 'Hotel Casa Tayrona' })
    expect(within(ficha).getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Alerta de afluencia',
      'Descripción',
      'Información de contacto',
      'Precio',
      'Horarios de atención',
      'Destinos alternativos con menor afluencia',
    ])
  })

  it('muestra el contacto, el horario (RF58) y la accesibilidad con nombre accesible (RF27)', async () => {
    simularFetch({ [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL) })
    renderApp('/servicios/s-hotel')

    const contacto = await screen.findByRole('region', { name: 'Información de contacto' })
    expect(within(contacto).getByRole('link', { name: '+57 300 000 0101' })).toHaveAttribute('href', 'tel:+573000000101')
    expect(within(contacto).getByRole('link', { name: 'reservas@casatayrona.test' })).toHaveAttribute(
      'href',
      'mailto:reservas@casatayrona.test',
    )

    const horarios = screen.getByRole('region', { name: 'Horarios de atención' })
    expect(horarios).toHaveTextContent('Recepción 24 horas')
    expect(horarios).toHaveTextContent('Check-in desde las 3:00 p. m.')

    const accesibilidad = screen.getByRole('list', { name: 'Accesibilidad' })
    expect(within(accesibilidad).getByRole('img', { name: 'Movilidad reducida' })).toHaveAttribute(
      'aria-label',
      'Movilidad reducida',
    )
    // En el orden de RF27, como llega del servidor.
    expect(within(accesibilidad).getAllByRole('img').map((icono) => icono.getAttribute('aria-label'))).toEqual([
      'Movilidad reducida',
      'Discapacidad visual',
    ])
  })

  it('lo pendiente aparece deshabilitado, sin texto visible de estado y con «No disponible» para lectores', async () => {
    simularFetch({ [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL) })
    const { usuario } = renderApp('/servicios/s-hotel')

    const ficha = await screen.findByRole('article', { name: 'Hotel Casa Tayrona' })
    expect(within(ficha).queryByText(/Próximamente/)).not.toBeInTheDocument()

    // El favorito ya no está pendiente (RF54, B1): lo prueba favoritos.test.tsx. «Compartir» sigue deshabilitado (DEC-90).
    for (const nombre of ['Compartir', 'Reportar información falsa o desactualizada']) {
      // jsdom une el texto del span solo-lector sin el espacio inicial; un navegador lo conserva.
      const boton = screen.getByRole('button', { name: new RegExp(`^${nombre} ?\\(No disponible\\)$`) })
      expect(boton).toHaveAttribute('aria-disabled', 'true')
      expect(within(boton).getByText(/No disponible/)).toHaveClass('solo-lector')
      // Pulsarlo no hace nada: la ruta no cambia.
      await usuario.click(boton)
      expect(ubicacionActual()).toBe('/servicios/s-hotel')
    }
    for (const nombre of ['Alerta de afluencia', 'Destinos alternativos con menor afluencia']) {
      const region = screen.getByRole('group', { name: nombre })
      expect(region).toHaveAttribute('aria-disabled', 'true')
      expect(within(region).getByText('No disponible')).toHaveClass('solo-lector')
      expect(within(region).getByText('—')).toHaveAttribute('aria-hidden', 'true')
    }
    const etiquetas = screen.getByRole('list', { name: 'Etiquetas del servicio' })
    expect(within(etiquetas).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Prestador verificado (No disponible)',
      'Afluencia en vivo (No disponible)',
      'Reserva inmediata (No disponible)',
    ])

    // Ubicación y calificación del subtítulo, y WhatsApp, dirección y RNT del contacto: «—» + «No disponible».
    expect(screen.getByText('Ubicación')).toHaveTextContent('Ubicación —No disponible')
    expect(screen.getByText('Calificación y reseñas')).toHaveTextContent('Calificación y reseñas —No disponible')
    const contacto = screen.getByRole('region', { name: 'Información de contacto' })
    for (const campo of ['WhatsApp', 'Dirección', 'Registro RNT']) {
      const valor = within(contacto).getByText(campo).nextElementSibling as HTMLElement
      expect(valor).toHaveTextContent('—No disponible')
      expect(within(valor).getByText('No disponible')).toHaveClass('solo-lector')
    }
  })

  it('un tour muestra duración, punto de encuentro, dificultad y aforo', async () => {
    simularFetch({ [`${DETALLE}/s-tour-cafe`]: () => respuestaJson(200, DETALLE_TOUR) })
    renderApp('/servicios/s-tour-cafe')

    const descripcion = await screen.findByRole('region', { name: 'Descripción' })
    expect(within(descripcion).getByRole('heading', { level: 3, name: 'Datos del servicio' })).toBeInTheDocument()
    expect(within(descripcion).getByText('4 h')).toBeInTheDocument()
    expect(within(descripcion).getByText('Iglesia de Minca')).toBeInTheDocument()
    expect(within(descripcion).getByText('Media')).toBeInTheDocument()
    expect(within(descripcion).getByText('12 personas')).toBeInTheDocument()
    const precio = screen.getByRole('region', { name: 'Precio' })
    expect(within(precio).getByText(/^Desde \$\s80\.000$/)).toBeInTheDocument()
    expect(within(precio).getByRole('link', { name: 'Ver disponibilidad y reservar' })).toBeInTheDocument()
  })

  it('una categoría que no se reserva (Gastronomía) no tiene botón de reserva; lo que falta queda deshabilitado', async () => {
    simularFetch({ [`${DETALLE}/s-restaurante`]: () => respuestaJson(200, DETALLE_RESTAURANTE) })
    renderApp('/servicios/s-restaurante')

    const descripcion = await screen.findByRole('region', { name: 'Descripción' })
    expect(within(descripcion).getByText('Caribeña')).toBeInTheDocument()
    expect(within(descripcion).getByText('Sí')).toBeInTheDocument()
    // Sin descripción: el mismo estado deshabilitado, no una región ausente.
    expect(within(descripcion).getByText('No disponible')).toHaveClass('solo-lector')

    const precio = screen.getByRole('region', { name: 'Precio' })
    expect(within(precio).getByText('$30.000 – $60.000')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /reservar/i })).not.toBeInTheDocument()

    // Sin contacto, horario ni accesibilidad: visibles, con el mismo estado deshabilitado que lo pendiente.
    const contacto = screen.getByRole('region', { name: 'Información de contacto' })
    expect(within(contacto).queryByRole('link')).not.toBeInTheDocument()
    expect(within(contacto).getByText('Teléfono').parentElement).toHaveTextContent('Teléfono—No disponible')
    expect(within(contacto).getByText('Correo').parentElement).toHaveTextContent('Correo—No disponible')
    const horarios = screen.getByRole('group', { name: 'Horarios de atención' })
    expect(horarios).toHaveAttribute('aria-disabled', 'true')
    expect(within(horarios).getByText('No disponible')).toHaveClass('solo-lector')
    expect(screen.getByText('Accesibilidad')).toHaveTextContent('Accesibilidad —No disponible')
    expect(screen.queryByRole('list', { name: 'Accesibilidad' })).not.toBeInTheDocument()
    // Ningún elemento usa «Próximamente» desde DEC-270.
    const ficha = screen.getByRole('article', { name: 'Mirador Gourmet' })
    expect(within(ficha).queryByText(/Próximamente|Sin información/)).not.toBeInTheDocument()
  })

  it('«Volver a la búsqueda» conserva los filtros de la búsqueda de origen', async () => {
    simularFetch({
      'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([HOTEL])),
      [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL),
    })
    const { usuario } = renderApp('/explorar?texto=hotel&categoria=Alojamiento')

    const tarjeta = await screen.findByRole('article', { name: 'Hotel Casa Tayrona' })
    await usuario.click(within(tarjeta).getByRole('link', { name: 'Ver detalle' }))
    await screen.findByRole('heading', { level: 1, name: 'Hotel Casa Tayrona' })

    const volver = screen.getByRole('link', { name: /Volver a la búsqueda/ })
    expect(volver).toHaveAttribute('href', '/explorar?texto=hotel&categoria=Alojamiento')
    // El enlace a WF-10 también recuerda la búsqueda para volver desde allí.
    await usuario.click(volver)
    await waitFor(() => expect(ubicacionActual()).toBe('/explorar?texto=hotel&categoria=Alojamiento'))
  })

  it('sin búsqueda de origen, «Volver a la búsqueda» lleva a /explorar', async () => {
    simularFetch({ [`${DETALLE}/s-hotel`]: () => respuestaJson(200, DETALLE_HOTEL) })
    renderApp('/servicios/s-hotel')

    await screen.findByRole('heading', { level: 1, name: 'Hotel Casa Tayrona' })
    expect(screen.getByRole('link', { name: /Volver a la búsqueda/ })).toHaveAttribute('href', '/explorar')
  })

  it('un servicio no visible (404) lo dice, sin «Reintentar»', async () => {
    simularFetch({ [`${DETALLE}/s-oculto`]: () => respuestaProblema(404, 'OFERTA_NO_DISPONIBLE') })
    renderApp('/servicios/s-oculto')

    expect(await screen.findByRole('alert')).toHaveTextContent('Este servicio no está disponible.')
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('con el catálogo caído (503) avisa y «Reintentar» vuelve a pedir el detalle', async () => {
    let llamadas = 0
    simularFetch({
      [`${DETALLE}/s-hotel`]: () => {
        llamadas += 1
        return llamadas === 1 ? respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE') : respuestaJson(200, DETALLE_HOTEL)
      },
    })
    const { usuario } = renderApp('/servicios/s-hotel')

    expect(await screen.findByRole('alert')).toHaveTextContent('El catálogo no está disponible en este momento.')
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Hotel Casa Tayrona' })).toBeInTheDocument()
    expect(llamadas).toBe(2)
  })
})

describe('WF-06: dirección, RNT y oferta (HC2, DEC-170)', () => {
  it('muestra la dirección y el RNT en el contacto, y los días y salidas de la oferta en los horarios', async () => {
    simularFetch({
      [`${DETALLE}/s-tour-cafe`]: () =>
        respuestaJson(200, {
          ...DETALLE_TOUR,
          direccion: 'Iglesia de Minca, parque principal',
          rnt: 'RNT-00000',
          oferta: { diasOperacion: ['Lunes', 'Miercoles', 'Sabado'], salidas: ['07:00', '13:30'] },
        }),
    })
    renderApp('/servicios/s-tour-cafe')

    const contacto = await screen.findByRole('region', { name: 'Información de contacto' })
    expect(within(contacto).getByText('Dirección').nextElementSibling).toHaveTextContent('Iglesia de Minca, parque principal')
    expect(within(contacto).getByText('Registro RNT').nextElementSibling).toHaveTextContent('RNT-00000')
    const horarios = screen.getByRole('region', { name: 'Horarios de atención' })
    expect(within(horarios).getByText('Días de operación').nextElementSibling).toHaveTextContent('Lunes, Miércoles, Sábado')
    expect(within(horarios).getByText('Salidas').nextElementSibling).toHaveTextContent('07:00, 13:30')
  })

  it('con dirección, RNT y oferta nulos no rompe: «—» y sin oferta', async () => {
    simularFetch({
      [`${DETALLE}/s-restaurante`]: () =>
        respuestaJson(200, { ...DETALLE_RESTAURANTE, direccion: null, rnt: null, oferta: null }),
    })
    renderApp('/servicios/s-restaurante')

    const contacto = await screen.findByRole('region', { name: 'Información de contacto' })
    for (const campo of ['Dirección', 'Registro RNT']) {
      expect(within(contacto).getByText(campo).nextElementSibling).toHaveTextContent('—No disponible')
    }
    // Sin horario ni oferta, la región sigue deshabilitada.
    expect(screen.getByRole('group', { name: 'Horarios de atención' })).toHaveAttribute('aria-disabled', 'true')
    expect(screen.queryByText('Días de operación')).not.toBeInTheDocument()
  })
})
