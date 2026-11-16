import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { paginaOferta, respuestaJson, simularFetch } from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'
import { TERMINOS } from '../terminos/textoTerminos'

const OFERTA = { 'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])) }

const INDICE_ES = [
  'Objeto y aceptación',
  'Uso permitido y prohibido',
  'Cuentas y roles',
  'Datos personales (Ley 1581 de 2012)',
  'Reservas y cancelaciones',
  'Contenido de terceros e imágenes de referencia',
  'Avisos y preguntas frecuentes',
  'Contacto',
  'Cambios a los términos',
]

describe('WF-15, términos y condiciones (RF46)', () => {
  it('es pública y muestra el título, la nota académica, el índice de DEC-68 y las 9 secciones', async () => {
    simularFetch({})
    renderApp('/terminos')

    expect(await screen.findByRole('heading', { level: 1, name: 'Términos y condiciones' })).toBeInTheDocument()
    expect(
      screen.getByText('Texto del prototipo académico — Universidad del Magdalena. No es un documento legal vigente.'),
    ).toBeInTheDocument()
    const indice = screen.getByRole('navigation', { name: 'Contenido' })
    const enlaces = within(indice).getAllByRole('link')
    expect(enlaces.map((enlace) => enlace.textContent)).toEqual(INDICE_ES)
    expect(enlaces[3]).toHaveAttribute('href', '#privacidad')
    for (const seccion of TERMINOS.es) {
      expect(screen.getByRole('region', { name: seccion.titulo })).toHaveAttribute('id', seccion.id)
    }
  })

  it('la sección 4 tiene el ancla #privacidad y describe la contraseña sin detalles de implementación', async () => {
    simularFetch({})
    renderApp('/terminos')

    const privacidad = await screen.findByRole('region', { name: '4. Datos personales (Ley 1581 de 2012)' })
    expect(privacidad).toHaveAttribute('id', 'privacidad')
    expect(privacidad).toHaveTextContent(
      'La contraseña se guarda protegida mediante una función de resumen criptográfico (no se almacena en texto legible).',
    )
    expect(privacidad).not.toHaveTextContent(/BCrypt|tabla|servicio de/i)
    expect(privacidad).toHaveTextContent(/Responsable del tratamiento: por definir por el equipo del proyecto/)
  })

  it('al llegar con #privacidad, el foco va al título de la sección 4', async () => {
    simularFetch({})
    renderApp('/terminos#privacidad')

    const titulo = await screen.findByRole('heading', { level: 2, name: '4. Datos personales (Ley 1581 de 2012)' })
    await waitFor(() => expect(titulo).toHaveFocus())
  })

  it('se lee en inglés con las mismas secciones y conserva el ancla (RNF08)', async () => {
    simularFetch({})
    const { usuario } = renderApp('/terminos')
    await screen.findByRole('heading', { level: 1, name: 'Términos y condiciones' })

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Terms and conditions' })).toBeInTheDocument()
    expect(screen.getByText(/It is not a legally binding document/)).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '4. Personal data (Colombian Law 1581 of 2012)' })).toHaveAttribute(
      'id',
      'privacidad',
    )
    expect(within(screen.getByRole('navigation', { name: 'Contents' })).getAllByRole('link')).toHaveLength(9)
  })

  it('«Términos y condiciones» y «Políticas de privacidad» del pie llevan a la página y a la sección 4', async () => {
    simularFetch(OFERTA)
    const { usuario } = renderApp('/')

    const pie = within(screen.getByRole('contentinfo'))
    expect(pie.getByRole('link', { name: 'Políticas de privacidad' })).toHaveAttribute('href', '/terminos#privacidad')
    await usuario.click(pie.getByRole('link', { name: 'Términos y condiciones' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Términos y condiciones' })).toBeInTheDocument()
    expect(ubicacionActual()).toBe('/terminos')
  })

  it('el texto legal del login (WF-01) enlaza a los términos y a la sección 4', async () => {
    simularFetch({})
    renderApp('/login')

    const legal = (await screen.findByText(/Al iniciar sesión aceptas/)).closest('p')!
    expect(within(legal).getByRole('link', { name: 'Términos, Condiciones' })).toHaveAttribute('href', '/terminos')
    expect(within(legal).getByRole('link', { name: 'Políticas de Privacidad' })).toHaveAttribute(
      'href',
      '/terminos#privacidad',
    )
  })
})
