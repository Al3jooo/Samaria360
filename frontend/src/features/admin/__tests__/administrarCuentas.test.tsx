import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import i18n from '../../../i18n'
import {
  DETALLE_PENDIENTE,
  PRESTADOR_ACTIVO,
  PRESTADOR_INACTIVO,
  PRESTADOR_PENDIENTE,
  PRESTADOR_RECHAZADO,
  TURISTA_ACTIVO,
  TURISTA_DESACTIVADO,
  USUARIO_ADMIN,
  USUARIO_TURISTA,
  paginaAdmin,
  respuestaJson,
  respuestaProblema,
  simularFetch,
  type SolicitudSimulada,
} from '../../../test/fetchSimulado'
import { renderApp, ubicacionActual } from '../../../test/renderApp'

const ME = 'GET /api/v1/auth/me'
const PRESTADORES = 'GET /api/v1/auth/admin/prestadores'
const TURISTAS = 'GET /api/v1/auth/admin/turistas'
const BASE = '/api/v1/auth/admin'

type Manejador = (solicitud: SolicitudSimulada) => Response | Promise<Response>

function sesionAdmin() {
  sessionStorage.setItem('samaria360.token', 'token-admin')
}

const consulta = (solicitud: SolicitudSimulada) => new URL(solicitud.url, 'http://localhost').searchParams

/** Las consultas a un listado (sin el contador de Pendientes de las otras pestañas). */
function listados(solicitudes: SolicitudSimulada[], ruta: string, estado?: string) {
  return solicitudes.filter(
    (s) => s.metodo === 'GET' && s.url.startsWith(`${BASE}/${ruta}?`) && (estado === undefined || consulta(s).get('estado') === estado),
  )
}

/** Monta /admin/cuentas como Administrador con las rutas dadas (por defecto, un Pendiente). */
function montar(ruta = '/admin/cuentas', rutas: Record<string, Manejador> = {}) {
  sesionAdmin()
  const simulado = simularFetch({
    [ME]: () => respuestaJson(200, USUARIO_ADMIN),
    [PRESTADORES]: () => respuestaJson(200, paginaAdmin([PRESTADOR_PENDIENTE])),
    ...rutas,
  })
  return { ...simulado, ...renderApp(ruta) }
}

const tabla = () => screen.findByRole('table')
const dialogo = () => screen.getByRole('dialog')

describe('Acceso (DEC-51)', () => {
  it('un Turista en /admin/cuentas ve «Sin permiso» y no se llama a la API de administración', async () => {
    sessionStorage.setItem('samaria360.token', 'token-turista')
    const { solicitudes } = simularFetch({ [ME]: () => respuestaJson(200, USUARIO_TURISTA) })
    renderApp('/admin/cuentas')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin permiso' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Tu cuenta no tiene permiso para ver esta página.')
    // Además de /me solo salen el GET público del banner común (DEC-72) y la campana del Turista (RF63, DEC-294); nada
    // de /auth/admin.
    expect(
      solicitudes
        .map((s) => s.url)
        .filter((url) => url !== '/api/v1/catalogo/avisos' && !url.startsWith('/api/v1/analytics/notificaciones')),
    ).toEqual(['/api/v1/auth/me'])
  })

  it('un 403 ACCESO_DENEGADO de la API muestra «Sin permiso» y la sesión sigue abierta', async () => {
    montar('/admin/cuentas', { [PRESTADORES]: () => respuestaProblema(403, 'ACCESO_DENEGADO') })

    expect(await screen.findByRole('heading', { level: 1, name: 'Sin permiso' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Menú de usuario de Laura Díaz' })).toBeInTheDocument()
    expect(sessionStorage.getItem('samaria360.token')).toBe('token-admin')
    expect(ubicacionActual()).toBe('/admin/cuentas')
  })
})

describe('RF28: validar prestadores', () => {
  it('«Prestadores por validar (n)» lista representante, RNT, correo y fecha de registro', async () => {
    const { solicitudes } = montar()

    const filas = within(await tabla()).getAllByRole('row')
    expect(within(filas[0]).getAllByRole('columnheader').map((c) => c.textContent)).toEqual([
      'Representante legal',
      'RNT',
      'Correo corporativo',
      'Registrado',
      'Acción',
    ])
    expect(within(filas[1]).getAllByRole('cell').map((c) => c.textContent)).toEqual([
      'Ana Pérez',
      '123456',
      'ana@hotel.co',
      '18/10/2026',
      'Revisar: Ana Pérez',
    ])
    expect(screen.getByRole('tab', { name: 'Prestadores por validar (1)' })).toHaveAttribute('aria-selected', 'true')
    const lista = listados(solicitudes, 'prestadores')
    expect(lista).toHaveLength(1)
    expect(consulta(lista[0]).get('estado')).toBe('Pendiente')
    expect(consulta(lista[0]).get('pagina')).toBe('0')
    expect(lista[0].cabeceras.Authorization).toBe('Bearer token-admin')
  })

  it('«Revisar» muestra el detalle: RNT, razón social «—», «Sí · fecha» y sin «País» (DEC-45)', async () => {
    const { usuario } = montar('/admin/cuentas', {
      [`GET ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}`]: () => respuestaJson(200, DETALLE_PENDIENTE),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Revisar: Ana Pérez' }))

    const detalle = await screen.findByRole('region', { name: 'Detalle del prestador' })
    await within(detalle).findByText('123456')
    const valor = (etiqueta: string) => within(detalle).getByText(etiqueta).nextElementSibling?.textContent
    expect(valor('Representante legal')).toBe('Ana Pérez')
    expect(valor('Razón social')).toBe('—sin datos')
    expect(valor('RNT')).toBe('123456')
    expect(valor('Correo corporativo')).toBe('ana@hotel.co')
    expect(valor('Teléfono')).toBe('+57 300 000 0000')
    expect(valor('Políticas aceptadas')).toBe('Sí · 18/10/2026')
    expect(valor('Estado')).toBe('Pendiente de validación')
    expect(within(detalle).queryByText(/País/)).not.toBeInTheDocument()
    expect(within(detalle).getByRole('heading', { name: 'Detalle del prestador' })).toHaveFocus()
  })

  it('«Aprobar» envía {decision: "Aprobar"} y recarga la lista y el contador', async () => {
    let aprobado = false
    const { usuario, solicitudes } = montar('/admin/cuentas', {
      [PRESTADORES]: () => respuestaJson(200, paginaAdmin(aprobado ? [] : [PRESTADOR_PENDIENTE])),
      [`GET ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}`]: () => respuestaJson(200, DETALLE_PENDIENTE),
      [`POST ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}/validacion`]: () => {
        aprobado = true
        return respuestaJson(200, { ...DETALLE_PENDIENTE, estado: 'Activo' })
      },
    })
    await usuario.click(await screen.findByRole('button', { name: 'Revisar: Ana Pérez' }))
    await usuario.click(await screen.findByRole('button', { name: 'Aprobar → Activo' }))

    expect(await screen.findByText('Se aprobó a Ana Pérez: su cuenta quedó Activa.')).toBeInTheDocument()
    const post = solicitudes.find((s) => s.metodo === 'POST')
    expect(post?.cuerpo).toEqual({ decision: 'Aprobar' })
    expect(await screen.findByText('No hay prestadores pendientes de validación.')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Prestadores por validar (0)' })).toBeInTheDocument()
    expect(listados(solicitudes, 'prestadores', 'Pendiente')).toHaveLength(2)
    expect(screen.queryByRole('region', { name: 'Detalle del prestador' })).not.toBeInTheDocument()
  })

  it('«Rechazar» exige una justificación de 1 a 500 caracteres y envía el texto sin espacios en los extremos', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas', {
      [`GET ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}`]: () => respuestaJson(200, DETALLE_PENDIENTE),
      [`POST ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}/validacion`]: () =>
        respuestaJson(200, { ...DETALLE_PENDIENTE, estado: 'Rechazado', motivoRechazo: 'RNT vencido' }),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Revisar: Ana Pérez' }))
    await usuario.click(await screen.findByRole('button', { name: 'Rechazar' }))

    expect(within(dialogo()).getByRole('heading', { name: 'Rechazar prestador «Ana Pérez»' })).toBeInTheDocument()
    expect(dialogo()).toHaveTextContent('La decisión es definitiva')
    const campo = within(dialogo()).getByLabelText('Justificación (obligatoria)')
    const confirmar = within(dialogo()).getByRole('button', { name: 'Confirmar rechazo' })
    expect(campo).toHaveFocus()

    // Vacía: deshabilitado.
    expect(confirmar).toHaveAttribute('aria-disabled', 'true')
    await usuario.click(confirmar)
    expect(solicitudes.some((s) => s.metodo === 'POST')).toBe(false)

    // Solo espacios: sigue deshabilitado (se cuentan sin los extremos).
    await usuario.type(campo, '    ')
    expect(within(dialogo()).getByText('0 de 500 caracteres')).toBeInTheDocument()
    expect(confirmar).toHaveAttribute('aria-disabled', 'true')

    // 501 caracteres: deshabilitado y marcado.
    await usuario.clear(campo)
    await usuario.click(campo)
    await usuario.paste('a'.repeat(501))
    expect(within(dialogo()).getByText('501 de 500 caracteres')).toBeInTheDocument()
    expect(confirmar).toHaveAttribute('aria-disabled', 'true')
    expect(campo).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialogo()).getByText('La justificación admite como máximo 500 caracteres.')).toBeInTheDocument()

    // Válida: se envía recortada.
    await usuario.clear(campo)
    await usuario.type(campo, '  El RNT no está vigente  ')
    expect(confirmar).not.toHaveAttribute('aria-disabled')
    await usuario.click(confirmar)

    expect(await screen.findByText('Se rechazó a Ana Pérez.')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'POST')?.cuerpo).toEqual({
      decision: 'Rechazar',
      justificacion: 'El RNT no está vigente',
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('el 400 de campos.justificacion se muestra en el campo y el diálogo sigue abierto', async () => {
    const { usuario } = montar('/admin/cuentas', {
      [`GET ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}`]: () => respuestaJson(200, DETALLE_PENDIENTE),
      [`POST ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}/validacion`]: () =>
        respuestaProblema(400, 'DATOS_INVALIDOS', { campos: { justificacion: 'REQUERIDO' } }),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Revisar: Ana Pérez' }))
    await usuario.click(await screen.findByRole('button', { name: 'Rechazar' }))
    const campo = within(dialogo()).getByLabelText('Justificación (obligatoria)')
    await usuario.type(campo, 'Motivo')
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Confirmar rechazo' }))

    const error = await within(dialogo()).findByText('Escribe la justificación del rechazo.')
    expect(campo).toHaveAttribute('aria-invalid', 'true')
    expect(campo.getAttribute('aria-describedby')).toContain(error.id)
  })

  it('un Rechazado muestra «Ver motivo», que abre el motivo del rechazo', async () => {
    const { usuario } = montar('/admin/cuentas?pestana=prestadores', {
      [PRESTADORES]: () => respuestaJson(200, paginaAdmin([PRESTADOR_RECHAZADO])),
      [`GET ${BASE}/prestadores/${PRESTADOR_RECHAZADO.idUsuario}`]: () =>
        respuestaJson(200, { ...DETALLE_PENDIENTE, ...PRESTADOR_RECHAZADO, motivoRechazo: 'El RNT no está vigente' }),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Ver motivo: Juan Ruiz' }))

    expect(within(dialogo()).getByRole('heading', { name: 'Motivo del rechazo de Juan Ruiz' })).toBeInTheDocument()
    expect(await within(dialogo()).findByText('El RNT no está vigente')).toBeInTheDocument()
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Cerrar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver motivo: Juan Ruiz' })).toHaveFocus()
  })
})

describe('RF22: inactivar prestadores', () => {
  it('«Inactivar» pide confirmación (irreversible, servicios ocultos), envía la petición y la fila pasa a Inactivo', async () => {
    let inactivo = false
    const { usuario, solicitudes } = montar('/admin/cuentas?pestana=prestadores', {
      [PRESTADORES]: (s) =>
        respuestaJson(
          200,
          consulta(s).get('estado') === 'Pendiente'
            ? paginaAdmin([PRESTADOR_PENDIENTE])
            : paginaAdmin([inactivo ? { ...PRESTADOR_ACTIVO, estado: 'Inactivo' } : PRESTADOR_ACTIVO]),
        ),
      [`POST ${BASE}/prestadores/${PRESTADOR_ACTIVO.idUsuario}/inactivacion`]: () => {
        inactivo = true
        return respuestaJson(200, { ...DETALLE_PENDIENTE, ...PRESTADOR_ACTIVO, estado: 'Inactivo' })
      },
    })
    await usuario.click(await screen.findByRole('button', { name: 'Inactivar: Carlos Ruiz' }))

    expect(dialogo()).toHaveTextContent('Sus servicios dejarán de publicarse en las búsquedas')
    expect(dialogo()).toHaveTextContent('El prestador no se puede reactivar desde el sistema.')
    expect(within(dialogo()).getByRole('button', { name: 'Cancelar' })).toHaveFocus()
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Inactivar' }))

    expect(await screen.findByText('Se inactivó la cuenta de Carlos Ruiz.')).toBeInTheDocument()
    const post = solicitudes.find((s) => s.metodo === 'POST')
    expect(post?.url).toBe(`${BASE}/prestadores/${PRESTADOR_ACTIVO.idUsuario}/inactivacion`)
    expect(post?.cuerpo).toBeUndefined()
    const fila = (await within(await tabla()).findByText('Inactivo')).closest('tr') as HTMLElement
    const reactivar = within(fila).getByRole('button', { name: /^Reactivar: Carlos Ruiz/ })
    expect(reactivar).toHaveAttribute('aria-disabled', 'true')
    await waitFor(() => expect(reactivar).toHaveFocus())
    expect(listados(solicitudes, 'prestadores').filter((s) => consulta(s).get('estado') !== 'Pendiente')).toHaveLength(2)
  })

  it('«Reactivar» de un prestador Inactivo está deshabilitado y anuncia «No disponible» (DEC-37, DEC-22)', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas?pestana=prestadores', {
      [PRESTADORES]: () => respuestaJson(200, paginaAdmin([PRESTADOR_INACTIVO])),
    })
    const reactivar = await screen.findByRole('button', { name: /^Reactivar: Eva Díaz\s*\(No disponible\)$/ })
    expect(reactivar).toHaveAttribute('aria-disabled', 'true')
    expect(reactivar).not.toBeDisabled()

    await usuario.click(reactivar)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(solicitudes.some((s) => s.metodo === 'POST')).toBe(false)
  })
})

describe('RF62: desactivar y reactivar turistas', () => {
  it('«Desactivar» pide confirmación, cambia el estado en su sitio y el foco va a «Reactivar»', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas?pestana=turistas', {
      [TURISTAS]: () => respuestaJson(200, paginaAdmin([TURISTA_ACTIVO])),
      [`POST ${BASE}/turistas/${TURISTA_ACTIVO.idUsuario}/desactivacion`]: () =>
        respuestaJson(200, { ...TURISTA_ACTIVO, estado: 'Desactivado' }),
    })
    const filas = within(await tabla()).getAllByRole('row')
    expect(within(filas[0]).getAllByRole('columnheader').map((c) => c.textContent)).toEqual([
      'Nombre',
      'Correo',
      'País',
      'Estado',
      'Acción',
    ])
    expect(within(filas[1]).getAllByRole('cell')[2]).toHaveTextContent('Estados Unidos')

    await usuario.click(screen.getByRole('button', { name: 'Desactivar: Marta Lee' }))
    expect(dialogo()).toHaveTextContent(
      '¿Desactivar la cuenta de Marta Lee? No podrá iniciar sesión y sus sesiones abiertas se cerrarán.',
    )
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Desactivar' }))

    expect(await screen.findByText('Se desactivó la cuenta de Marta Lee.')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('Desactivado')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reactivar: Marta Lee' })).toHaveFocus())
    // Se reemplazó en su sitio: el listado se consultó una sola vez.
    expect(listados(solicitudes, 'turistas')).toHaveLength(1)
  })

  it('«Reactivar» pide confirmación y la cuenta vuelve a Activo', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas?pestana=turistas', {
      [TURISTAS]: () => respuestaJson(200, paginaAdmin([TURISTA_DESACTIVADO])),
      [`POST ${BASE}/turistas/${TURISTA_DESACTIVADO.idUsuario}/reactivacion`]: () =>
        respuestaJson(200, { ...TURISTA_DESACTIVADO, estado: 'Activo' }),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Reactivar: Pablo Mora' }))
    expect(dialogo()).toHaveTextContent('¿Reactivar la cuenta de Pablo Mora? Podrá volver a iniciar sesión.')
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Reactivar' }))

    expect(await screen.findByText('Se reactivó la cuenta de Pablo Mora.')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('Activo')).toBeInTheDocument()
    expect(solicitudes.find((s) => s.metodo === 'POST')?.url).toBe(
      `${BASE}/turistas/${TURISTA_DESACTIVADO.idUsuario}/reactivacion`,
    )
  })

  it('«Cancelar» cierra el diálogo sin enviar nada y devuelve el foco al botón', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas?pestana=turistas', {
      [TURISTAS]: () => respuestaJson(200, paginaAdmin([TURISTA_ACTIVO])),
    })
    const boton = await screen.findByRole('button', { name: 'Desactivar: Marta Lee' })
    await usuario.click(boton)
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(boton).toHaveFocus()
    expect(solicitudes.some((s) => s.metodo === 'POST')).toBe(false)
  })
})

describe('DEC-39: la cuenta cambió de estado', () => {
  it('un 409 muestra el aviso, recarga la lista y cierra el diálogo', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas?pestana=turistas', {
      [TURISTAS]: () => respuestaJson(200, paginaAdmin([TURISTA_ACTIVO])),
      [`POST ${BASE}/turistas/${TURISTA_ACTIVO.idUsuario}/desactivacion`]: () =>
        respuestaProblema(409, 'ESTADO_CAMBIADO', { estadoActual: 'Desactivado' }),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Desactivar: Marta Lee' }))
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Desactivar' }))

    const aviso = await screen.findByText('La cuenta cambió de estado; se recargó la lista.')
    expect(aviso.closest('[aria-live="polite"]')).not.toBeNull()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(listados(solicitudes, 'turistas')).toHaveLength(2))
  })

  it('un 404 al revisar muestra su aviso y recarga la lista', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas', {
      [`GET ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}`]: () => respuestaProblema(404, 'CUENTA_NO_ENCONTRADA'),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Revisar: Ana Pérez' }))

    expect(await screen.findByText('La cuenta ya no existe; se recargó la lista.')).toBeInTheDocument()
    await waitFor(() => expect(listados(solicitudes, 'prestadores', 'Pendiente')).toHaveLength(2))
    expect(screen.queryByRole('region', { name: 'Detalle del prestador' })).not.toBeInTheDocument()
  })
})

describe('Paginación, filtros y URL (DEC-41, DEC-51)', () => {
  it('páginas de 10: «Siguiente» pide pagina=1 a la API y escribe pagina=2 en la URL', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas', {
      [PRESTADORES]: (s) =>
        respuestaJson(
          200,
          paginaAdmin([PRESTADOR_PENDIENTE], { pagina: Number(consulta(s).get('pagina')), totalElementos: 25 }),
        ),
    })
    expect(await screen.findByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Prestadores por validar (25)' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: /Siguiente/ }))
    expect(await screen.findByText('Página 2 de 3')).toBeInTheDocument()
    expect(ubicacionActual()).toBe('/admin/cuentas?pagina=2')
    expect(consulta(listados(solicitudes, 'prestadores').at(-1)!).get('pagina')).toBe('1')
  })

  it('la URL conserva la pestaña, el estado, la búsqueda y la página', async () => {
    const { usuario, solicitudes } = montar('/admin/cuentas', {
      [TURISTAS]: () => respuestaJson(200, paginaAdmin([TURISTA_DESACTIVADO], { totalElementos: 15 })),
    })
    await usuario.click(await screen.findByRole('tab', { name: /^Turistas/ }))
    await waitFor(() => expect(ubicacionActual()).toBe('/admin/cuentas?pestana=turistas'))

    await usuario.selectOptions(screen.getByLabelText('Estado'), 'Desactivado')
    await usuario.type(screen.getByLabelText('Buscar'), 'pablo')
    await usuario.click(screen.getByRole('button', { name: 'Buscar' }))
    await waitFor(() => expect(ubicacionActual()).toBe('/admin/cuentas?pestana=turistas&estado=Desactivado&q=pablo'))

    await usuario.click(await screen.findByRole('button', { name: /Siguiente/ }))
    await waitFor(() =>
      expect(ubicacionActual()).toBe('/admin/cuentas?pestana=turistas&estado=Desactivado&q=pablo&pagina=2'),
    )
    const ultima = consulta(listados(solicitudes, 'turistas').at(-1)!)
    expect([ultima.get('estado'), ultima.get('q'), ultima.get('pagina')]).toEqual(['Desactivado', 'pablo', '1'])
    // El contador de «Prestadores por validar» se consulta aparte, sin búsqueda.
    const contador = listados(solicitudes, 'prestadores', 'Pendiente')
    expect(contador.every((s) => consulta(s).get('q') === null)).toBe(true)
  })

  it('las pestañas se recorren con las flechas (WAI-ARIA tablist)', async () => {
    // Desde B2 la última pestaña es «Personal» (RF45, DEC-115).
    const { usuario } = montar('/admin/cuentas', {
      [`GET ${BASE}/personal`]: () => respuestaJson(200, paginaAdmin([])),
    })
    const primera = await screen.findByRole('tab', { name: 'Prestadores por validar (1)' })
    primera.focus()
    await usuario.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'Personal' })).toHaveFocus()
    await waitFor(() => expect(ubicacionActual()).toBe('/admin/cuentas?pestana=personal'))
    expect(await screen.findByText('No hay gestores ni administradores.')).toBeInTheDocument()
  })
})

describe('i18n (RF03, RNF08)', () => {
  it('WF-12 en inglés: pestañas, diálogo de Inactivar y avisos', async () => {
    await i18n.changeLanguage('en')
    const { usuario } = montar('/admin/cuentas?pestana=prestadores', {
      [PRESTADORES]: (s) =>
        respuestaJson(
          200,
          consulta(s).get('estado') === 'Pendiente' ? paginaAdmin([PRESTADOR_PENDIENTE]) : paginaAdmin([PRESTADOR_ACTIVO]),
        ),
      [`POST ${BASE}/prestadores/${PRESTADOR_ACTIVO.idUsuario}/inactivacion`]: () =>
        respuestaProblema(409, 'ESTADO_CAMBIADO', { estadoActual: 'Inactivo' }),
    })

    expect(await screen.findByRole('heading', { level: 1, name: 'Manage accounts' })).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: 'Providers to validate (1)' })).toBeInTheDocument()
    await usuario.click(await screen.findByRole('button', { name: 'Deactivate: Carlos Ruiz' }))
    expect(dialogo()).toHaveTextContent('The provider cannot be reactivated from the system.')
    expect(dialogo()).toHaveTextContent('Their services will no longer be published in searches')
    await usuario.click(within(dialogo()).getByRole('button', { name: 'Deactivate' }))
    expect(await screen.findByText('The account status changed; the list was reloaded.')).toBeInTheDocument()
  })

  it('el diálogo de Rechazar en inglés avisa que la decisión es definitiva', async () => {
    await i18n.changeLanguage('en')
    const { usuario } = montar('/admin/cuentas', {
      [`GET ${BASE}/prestadores/${PRESTADOR_PENDIENTE.idUsuario}`]: () => respuestaJson(200, DETALLE_PENDIENTE),
    })
    await usuario.click(await screen.findByRole('button', { name: 'Review: Ana Pérez' }))
    expect(await screen.findByText('Yes · 10/18/2026')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Reject' }))
    expect(dialogo()).toHaveTextContent('This decision is final')
    expect(within(dialogo()).getByText('0 of 500 characters')).toBeInTheDocument()
  })
})
