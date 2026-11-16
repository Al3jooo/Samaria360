import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { navegador } from '../../shared/navegador'
import { renderApp } from '../../test/renderApp'

// Simula que el archivo diferido de /explorar no se pudo descargar (p. ej. sin conexión).
vi.mock('../../features/catalogo/ExplorarPage', async () => {
  throw new TypeError('Failed to fetch dynamically imported module')
})

describe('Fallo de la carga diferida de /explorar', () => {
  it('muestra el aviso con «Reintentar» (recarga la página) sin desmontar el encabezado', async () => {
    // React registra en consola el error que captura el límite.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const recargar = vi.spyOn(navegador, 'recargar').mockImplementation(() => {})
    const { usuario } = renderApp('/explorar')

    const aviso = await screen.findByRole('alert')
    expect(aviso).toHaveTextContent('No pudimos cargar esta sección. Revisa tu conexión.')
    expect(screen.getByRole('link', { name: 'Inicio de sesión' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'EN · Inglés' })).toBeInTheDocument()

    await usuario.click(within(aviso).getByRole('button', { name: 'Reintentar' }))
    expect(recargar).toHaveBeenCalledTimes(1)

    await usuario.click(screen.getByRole('button', { name: 'EN · Inglés' }))
    expect(screen.getByRole('alert')).toHaveTextContent("We couldn't load this section. Check your connection.")
    expect(within(screen.getByRole('alert')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
