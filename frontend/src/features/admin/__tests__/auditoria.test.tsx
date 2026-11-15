import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { USUARIO_ADMIN, USUARIO_TURISTA, respuestaJson, simularFetch } from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const AUDITORIA = 'GET /api/v1/analytics/admin/auditoria'

const REGISTRO = {
  usuarioId: '00000000-0000-4000-8000-000000000004',
  accion: 'PRESTADOR_APROBADO',
  detalle: 'Prestador 00000000-0000-4000-8000-000000000002',
  fecha: '2030-03-01T15:00:00Z',
}

describe('Auditoría (RF13, WF-30)', () => {
  it('el Administrador ve las acciones con usuario, fecha y acción, y filtra por acción y fechas en la URL', async () => {
    sessionStorage.setItem('samaria360.token', 'token-admin')
    const { solicitudes } = simularFetch({
      [ME]: () => respuestaJson(200, USUARIO_ADMIN),
      [AUDITORIA]: () => respuestaJson(200, { elementos: [REGISTRO], pagina: 0, tamano: 20, totalElementos: 1 }),
    })
    const { usuario } = renderApp('/admin/auditoria')

    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText('Prestador aprobado')).toBeInTheDocument()
    expect(within(tabla).getByText(`…${REGISTRO.usuarioId.slice(-12)}`)).toHaveAttribute('title', REGISTRO.usuarioId)
    expect(within(tabla).getByText(REGISTRO.detalle)).toBeInTheDocument()

    await usuario.selectOptions(screen.getByLabelText('Acción'), 'ROL_CAMBIADO')
    await usuario.type(screen.getByLabelText('Desde'), '2030-03-01')
    await usuario.click(screen.getByRole('button', { name: 'Filtrar' }))

    expect(ubicacionActual()).toContain('accion=ROL_CAMBIADO')
    const ultima = solicitudes.filter((s) => s.url.startsWith('/api/v1/analytics/admin/auditoria')).at(-1)
    expect(ultima?.url).toContain('accion=ROL_CAMBIADO')
    expect(ultima?.url).toContain('desde=2030-03-01')
    expect(ultima?.url).toContain('pagina=0')
  }, 15_000)

  it('un Turista ve «Sin permiso» y no se llama a la auditoría', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    const { solicitudes } = simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA) })
    renderApp('/admin/auditoria')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin permiso' })).toBeInTheDocument()
    expect(solicitudes.some((s) => s.url.startsWith('/api/v1/analytics/admin'))).toBe(false)
  })
})
