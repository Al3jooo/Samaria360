import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { respuestaJson, respuestaProblema, simularFetch } from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const PEDIR = 'POST /api/v1/auth/recuperacion'
const RESTABLECER = 'POST /api/v1/auth/recuperacion/restablecimiento'
const TOKEN = 'A'.repeat(42) + '_'

describe('Recuperar contraseña (RF34, WF-27 y WF-28)', () => {
  it('desde el login se pide el enlace y la respuesta es la misma exista o no la cuenta', async () => {
    const { solicitudes } = simularFetch({ [PEDIR]: () => respuestaJson(202, { estado: 'RECIBIDO' }) })
    const { usuario } = renderApp('/login')

    await usuario.click(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' }))
    expect(ubicacionActual()).toBe('/recuperar')
    await usuario.type(screen.getByLabelText('Correo electrónico'), ' ana@x.com ')
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Si el correo tiene una cuenta en Samaria360')
    const pedido = solicitudes.find((s) => s.url === '/api/v1/auth/recuperacion')
    expect(pedido?.cuerpo).toEqual({ correo: 'ana@x.com', idioma: 'es' })
    expect(pedido?.cabeceras.Authorization).toBeUndefined()
  }, 15_000)

  it('sin correo no envía nada y marca el campo', async () => {
    const { solicitudes } = simularFetch({})
    const { usuario } = renderApp('/recuperar')

    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    expect(screen.getByText('Este campo es obligatorio.')).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('aria-invalid', 'true')
    expect(solicitudes).toHaveLength(0)
  })

  it('un 429 del gateway pide esperar', async () => {
    simularFetch({ [PEDIR]: () => respuestaProblema(429, 'DEMASIADAS_SOLICITUDES') })
    const { usuario } = renderApp('/recuperar')

    await usuario.type(screen.getByLabelText('Correo electrónico'), 'ana@x.com')
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    expect(await screen.findByText(/Hiciste demasiados intentos/)).toBeInTheDocument()
  })

  it('con el token del enlace guarda la contraseña nueva y quita el token de la URL', async () => {
    const { solicitudes } = simularFetch({ [RESTABLECER]: () => new Response(null, { status: 204 }) })
    const { usuario } = renderApp(`/restablecer#token=${TOKEN}`)

    expect(ubicacionActual()).not.toContain(TOKEN)
    await usuario.type(screen.getByLabelText('Contraseña nueva'), 'ClaveNueva1')
    await usuario.type(screen.getByLabelText('Confirma la contraseña'), 'ClaveNueva1')
    await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByRole('status')).toHaveTextContent('tu contraseña cambió')
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login')
    expect(solicitudes.find((s) => s.url === '/api/v1/auth/recuperacion/restablecimiento')?.cuerpo).toEqual({
      token: TOKEN,
      contrasena: 'ClaveNueva1',
    })
  }, 15_000)

  it('valida la longitud y que las dos contraseñas coincidan antes de enviar', async () => {
    const { solicitudes } = simularFetch({})
    const { usuario } = renderApp(`/restablecer#token=${TOKEN}`)

    await usuario.type(screen.getByLabelText('Contraseña nueva'), 'corta')
    await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
    expect(screen.getByText('La contraseña debe tener al menos 8 caracteres.')).toBeInTheDocument()

    await usuario.type(screen.getByLabelText('Contraseña nueva'), 'larga123')
    await usuario.type(screen.getByLabelText('Confirma la contraseña'), 'distinta1')
    await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
    expect(screen.getByText('Las contraseñas no coinciden.')).toBeInTheDocument()
    expect(solicitudes).toHaveLength(0)
  }, 15_000)

  it('un enlace usado o vencido lleva a pedir otro', async () => {
    simularFetch({ [RESTABLECER]: () => respuestaProblema(400, 'TOKEN_INVALIDO') })
    const { usuario } = renderApp(`/restablecer#token=${TOKEN}`)

    await usuario.type(screen.getByLabelText('Contraseña nueva'), 'ClaveNueva1')
    await usuario.type(screen.getByLabelText('Confirma la contraseña'), 'ClaveNueva1')
    await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByText('El enlace no es válido, ya se usó o venció.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pedir otro enlace' })).toHaveAttribute('href', '/recuperar')
  }, 15_000)

  it('sin token en el enlace no muestra el formulario', () => {
    simularFetch({})
    renderApp('/restablecer')

    expect(screen.getByText('El enlace no es válido, ya se usó o venció.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Contraseña nueva')).not.toBeInTheDocument()
  })
})
