import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  AVISO_CIERRE,
  AVISO_CLIMA,
  USUARIO_TURISTA,
  listaElementos,
  paginaOferta,
  respuestaJson,
  respuestaProblema,
  simularFetch,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'
import { descartarPeticionesSinSimular } from '../../../test/setup'

const AVISOS = 'GET /api/v1/catalogo/avisos'
const OFERTA = { 'GET /api/v1/catalogo/oferta': () => respuestaJson(200, paginaOferta([])) }
const CLAVE_CERRADOS = 'samaria360.avisosCerrados'

function simular(avisos: () => Response = () => respuestaJson(200, listaElementos([AVISO_CIERRE, AVISO_CLIMA]))) {
  return simularFetch({
    ...OFERTA,
    'GET /api/v1/catalogo/faq': () => respuestaJson(200, listaElementos([])),
    [AVISOS]: avisos,
  })
}

const banner = () => screen.findByRole('region', { name: 'Avisos del municipio' })
const sinBanner = () => expect(screen.queryByRole('region', { name: 'Avisos del municipio' })).not.toBeInTheDocument()

describe('Banner de avisos del municipio (RF40, WF-14; DEC-67, DEC-72)', () => {
  it('muestra todos los activos, del más reciente al más antiguo, con su tipo traducido y su fecha', async () => {
    simular()
    const { usuario } = renderApp('/')

    const region = await banner()
    const avisos = within(region).getAllByRole('listitem')
    expect(avisos.map((aviso) => aviso.textContent)).toEqual([
      `Cierre${AVISO_CIERRE.mensaje}· 22/10/2026`,
      `Clima${AVISO_CLIMA.mensaje}· 21/10/2026`,
    ])
    // Debajo del encabezado y fuera del contenido principal.
    expect(screen.getByRole('banner').nextElementSibling).toBe(region)

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))
    const enIngles = screen.getByRole('region', { name: 'Notices from the municipality' })
    expect(within(enIngles).getByText('Closure')).toBeInTheDocument()
    expect(within(enIngles).getByText('Weather')).toBeInTheDocument()
    // El mensaje va como lo escribió el Administrador (DEC-69).
    expect(within(enIngles).getByText(AVISO_CIERRE.mensaje)).toBeInTheDocument()
    expect(within(enIngles).getByRole('button', { name: 'Dismiss notices' })).toBeInTheDocument()
  })

  it('está en las pantallas de la plataforma y no en las de acceso', async () => {
    simular()
    renderApp('/preguntas-frecuentes')
    expect(await banner()).toBeInTheDocument()

    cleanup()
    simular()
    renderApp('/login')
    await screen.findByRole('heading', { level: 1 })
    sinBanner()
  })

  it('«Cerrar avisos» lo oculta, el foco pasa al contenido y no reaparece al navegar ni al volver a montar la app', async () => {
    simular()
    const { usuario } = renderApp('/')

    await usuario.click(within(await banner()).getByRole('button', { name: 'Cerrar avisos' }))
    sinBanner()
    expect(screen.getByRole('main')).toHaveFocus()
    expect(JSON.parse(sessionStorage.getItem(CLAVE_CERRADOS) ?? '[]')).toEqual([AVISO_CIERRE.id, AVISO_CLIMA.id])

    await usuario.click(within(screen.getByRole('contentinfo')).getByRole('link', { name: 'Preguntas frecuentes' }))
    await waitFor(() => expect(ubicacionActual()).toBe('/preguntas-frecuentes'))
    sinBanner()

    // Misma sesión del navegador (sessionStorage), app montada de nuevo: sigue cerrado.
    cleanup()
    const { solicitudes } = simular()
    renderApp('/')
    await waitFor(() => expect(solicitudes.some((s) => s.url === '/api/v1/catalogo/avisos')).toBe(true))
    await screen.findByRole('heading', { level: 1 })
    sinBanner()
  })

  it('un aviso nuevo vuelve a mostrar el banner, solo con los que no se cerraron', async () => {
    sessionStorage.setItem(CLAVE_CERRADOS, JSON.stringify([AVISO_CLIMA.id]))
    simular()
    renderApp('/')

    const region = await banner()
    expect(within(region).getAllByRole('listitem')).toHaveLength(1)
    expect(within(region).getByText(AVISO_CIERRE.mensaje)).toBeInTheDocument()
    expect(within(region).queryByText(AVISO_CLIMA.mensaje)).not.toBeInTheDocument()
  })

  it('sin avisos activos no se muestra', async () => {
    const { solicitudes } = simular(() => respuestaJson(200, listaElementos([])))
    renderApp('/')

    await waitFor(() => expect(solicitudes.some((s) => s.url === '/api/v1/catalogo/avisos')).toBe(true))
    sinBanner()
  })

  it('si el catálogo no responde, no se muestra y la pantalla sigue funcionando', async () => {
    const { solicitudes } = simular(() => respuestaProblema(503, 'SERVICIO_NO_DISPONIBLE'))
    renderApp('/')

    await waitFor(() => expect(solicitudes.some((s) => s.url === '/api/v1/catalogo/avisos')).toBe(true))
    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument()
    sinBanner()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('funciona aunque sessionStorage lance: se muestra y se cierra mientras la app sigue montada', async () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('bloqueado', 'SecurityError')
    })
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('bloqueado', 'SecurityError')
    })
    try {
      simular()
      const { usuario } = renderApp('/')

      await usuario.click(within(await banner()).getByRole('button', { name: 'Cerrar avisos' }))
      sinBanner()
    } finally {
      getItem.mockRestore()
      setItem.mockRestore()
    }
  })

  it('pide los avisos sin Authorization aun con la sesión iniciada (DEC-47)', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    const { solicitudes } = simularFetch({
      ...OFERTA,
      'GET /api/v1/auth/me': () => respuestaJson(200, USUARIO_TURISTA),
      [AVISOS]: () => respuestaJson(200, listaElementos([AVISO_CIERRE])),
    })
    renderApp('/')

    await banner()
    const avisos = solicitudes.filter((s) => s.url === '/api/v1/catalogo/avisos')
    expect(avisos.length).toBeGreaterThan(0)
    for (const solicitud of avisos) expect(solicitud.cabeceras.Authorization).toBeUndefined()
  })
})

describe('Guardia de red de las pruebas (DEC-78)', () => {
  it('solo GET /api/v1/catalogo/avisos exacta responde vacía; cualquier otra petición sin simular sigue fallando', async () => {
    const respuesta = await fetch('/api/v1/catalogo/avisos')
    expect(respuesta.status).toBe(200)
    expect(await respuesta.json()).toEqual({ elementos: [] })

    await expect(fetch('/api/v1/catalogo/avisos?pagina=0')).rejects.toThrow('Petición de red sin simular')
    await expect(fetch('/api/v1/catalogo/avisos', { method: 'POST' })).rejects.toThrow('Petición de red sin simular')
    await expect(fetch('/api/v1/catalogo/faq')).rejects.toThrow('Petición de red sin simular')

    // Quedan registradas: sin descartarlas aquí, afterEach haría fallar esta prueba.
    expect(descartarPeticionesSinSimular()).toEqual([
      '/api/v1/catalogo/avisos?pagina=0',
      '/api/v1/catalogo/avisos',
      '/api/v1/catalogo/faq',
    ])
  })
})
