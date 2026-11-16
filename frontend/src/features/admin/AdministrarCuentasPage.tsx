import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { ApiError, servicioNoDisponible } from '../../shared/api/httpClient'
import { BotonNoDisponible } from '../../shared/components/BotonNoDisponible'
import { Paginacion } from '../../shared/components/Paginacion'
import { SinPermiso } from '../../shared/components/SinPermiso'
import { useSesion } from '../auth/useSesion'
import {
  cambiarRol,
  consultarPrestador,
  desactivarTurista,
  inactivarPrestador,
  reactivarTurista,
  validarPrestador,
  type PersonalResumen,
  type PrestadorDetalle,
  type PrestadorResumen,
  type RolCambio,
  type TuristaResumen,
} from './adminApi'
import { DetallePrestador, type EstadoDetalle } from './componentes/DetallePrestador'
import { DialogoCambioRol, type ModoCambioRol } from './componentes/DialogoCambioRol'
import { DialogoConfirmacion, type AccionConfirmable } from './componentes/DialogoConfirmacion'
import { DialogoMotivo } from './componentes/DialogoMotivo'
import { DialogoRechazo } from './componentes/DialogoRechazo'
import { InsigniaCuenta } from './componentes/InsigniaCuenta'
import { TablaCuentas, type ColumnaCuentas, type FilaCuentas } from './componentes/TablaCuentas'
import {
  escribirCriteriosAdmin,
  estadosDe,
  hayFiltrosAdmin,
  leerCriteriosAdmin,
  MAXIMO_BUSQUEDA,
  PESTANAS,
  rolesDe,
  type CriteriosAdmin,
  type Pestana,
} from './criteriosAdmin'
import { formatearFecha, nombrePais } from './formato'
import { clasificarError, useCuentas, type Lista } from './useCuentas'
import '../catalogo/catalogo.css'
import './admin.css'

type Dialogo =
  | { tipo: 'rechazo'; prestador: PrestadorDetalle }
  | { tipo: 'confirmacion'; accion: AccionConfirmable; idUsuario: string; nombre: string }
  | { tipo: 'motivo'; idUsuario: string; nombre: string }
  | {
      tipo: 'cambioRol'
      modo: ModoCambioRol
      idUsuario: string
      nombre: string
      rolActual: RolCambio
      /** Fijo al modificar o revocar; al asignar lo elige el Administrador en el diálogo. */
      rolNuevo?: RolCambio
    }

interface Envio {
  enviando: boolean
  /** Código del último error que se muestra en el diálogo o en el detalle. */
  error: string | null
  /** Código de `campos.justificacion` del último 400. */
  campo: string | null
}

const SIN_ENVIO: Envio = { enviando: false, error: null, campo: null }

/** Errores que cambian la lista por debajo (DEC-39; los de RF45, DEC-119): se avisa, se cierra todo y se recarga. */
const CAMBIAN_LA_LISTA = new Set([
  'ESTADO_CAMBIADO',
  'CUENTA_NO_ENCONTRADA',
  'ROL_CAMBIADO',
  'CUENTA_NO_ELEGIBLE',
  'CAMBIO_ROL_PROPIO',
  'ULTIMO_ADMINISTRADOR',
])

/** Motivos de 409 CUENTA_NO_ELEGIBLE con mensaje propio (DEC-105); otro motivo usa el mensaje general. */
const MOTIVOS_NO_ELEGIBLE = new Set(['PRESTADOR', 'DESACTIVADA', 'SIN_ROL_BASE'])

/** Roles con nombre en `admin.roles`. */
const ROLES_CON_NOMBRE = new Set(['Turista', 'Gestor', 'Administrador'])

/** Código que se muestra para un error que no es de la lista ni de la sesión. */
function codigoMostrado(error: ApiError): string {
  if (servicioNoDisponible(error)) return 'SERVICIO_NO_DISPONIBLE'
  return error.codigo === 'ERROR_RED' ? 'ERROR_RED' : 'GENERICO'
}

/**
 * WF-12 «Administrar cuentas» (CU «Administrar cuentas»; RF28, RF22, RF62) y «Cambiar rol de cuenta» (RF45: pestaña
 * «Personal» y «Asignar rol» en Turistas), solo para el Administrador (RutaProtegida con `rol`, DEC-51). Pestañas,
 * filtros y página en la URL. Región de estado `aria-live` para el éxito, la lista vacía y los 409 y 404 que cambian la
 * lista (DEC-39, DEC-119).
 */
export function AdministrarCuentasPage() {
  const { t, i18n } = useTranslation()
  const id = useId()
  const [parametros, setParametros] = useSearchParams()
  const criterios = useMemo(() => leerCriteriosAdmin(parametros), [parametros])
  const { estado, pendientes, recargar, reemplazarFila } = useCuentas(criterios)
  const { sesion } = useSesion()
  /** La fila propia del Administrador en «Personal» no tiene acciones (DEC-116). */
  const idPropio = sesion.estado === 'autenticado' ? sesion.usuario.idUsuario : null

  const [texto, setTexto] = useState(criterios.q)
  const [qAplicada, setQAplicada] = useState(criterios.q)
  if (criterios.q !== qAplicada) {
    setQAplicada(criterios.q)
    setTexto(criterios.q)
  }
  const [anuncio, setAnuncio] = useState('')
  const [sinPermiso, setSinPermiso] = useState(false)
  const [detalle, setDetalle] = useState<EstadoDetalle | null>(null)
  const [dialogo, setDialogo] = useState<Dialogo | null>(null)
  const [envio, setEnvio] = useState<Envio>(SIN_ENVIO)

  const pestanas = useRef(new Map<Pestana, HTMLButtonElement>())
  const botones = useRef(new Map<string, HTMLButtonElement>())
  const tituloResultados = useRef<HTMLHeadingElement>(null)
  const origen = useRef<HTMLElement | null>(null)
  /**
   * A dónde va el foco cuando llegue la lista nueva (distinta de `antes`): la acción de una fila, si sigue en la página,
   * o el título de resultados.
   */
  const enfocar = useRef<{ fila?: string; antes: Lista | null } | null>(null)
  const consultaDetalle = useRef(0)

  useEffect(() => {
    const pedido = enfocar.current
    if (!pedido || estado.estado === 'cargando') return
    if (estado.estado === 'ok' && estado.lista === pedido.antes) return
    const { fila } = pedido
    enfocar.current = null
    const boton = fila ? botones.current.get(fila) : undefined
    if (boton) boton.focus()
    else tituloResultados.current?.focus()
  })

  /**
   * DEC-121: una página que llega vacía y está más allá de la última (p. ej. tras asignar o revocar el único elemento
   * de la última página, o una URL con `pagina` fuera de rango) lleva a la última que existe, sin una entrada nueva en
   * el historial y sin borrar el anuncio. Solo corre cuando llega una lista: no se repite.
   */
  useEffect(() => {
    if (estado.estado !== 'ok') return
    const { elementos, totalPaginas } = estado.lista.pagina
    if (elementos.length === 0 && criterios.pagina > 1 && totalPaginas >= 1 && criterios.pagina > totalPaginas) {
      setParametros(escribirCriteriosAdmin({ ...criterios, pagina: totalPaginas }), { replace: true })
    }
  }, [estado, criterios, setParametros])

  if (sinPermiso || (estado.estado === 'error' && estado.error === 'sinPermiso')) {
    return <SinPermiso />
  }

  const pedirFoco = (fila?: string) => {
    enfocar.current = { fila, antes: estado.estado === 'ok' ? estado.lista : null }
  }

  const navegar = (cambios: Partial<CriteriosAdmin>) => {
    setAnuncio('')
    setParametros(escribirCriteriosAdmin({ ...criterios, ...cambios }))
  }

  const cambiarPestana = (pestana: Pestana) => {
    if (pestana === criterios.pestana) return
    setDetalle(null)
    navegar({ pestana, estado: '', rol: '', q: '', pagina: 1 })
  }

  const alPulsarEnPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    const actual = PESTANAS.indexOf(criterios.pestana)
    const destino: Record<string, number> = {
      ArrowRight: (actual + 1) % PESTANAS.length,
      ArrowLeft: (actual - 1 + PESTANAS.length) % PESTANAS.length,
      Home: 0,
      End: PESTANAS.length - 1,
    }
    if (!(evento.key in destino)) return
    evento.preventDefault()
    const pestana = PESTANAS[destino[evento.key]]
    cambiarPestana(pestana)
    pestanas.current.get(pestana)?.focus()
  }

  const abrirDialogo = (nuevo: Dialogo) => {
    origen.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setEnvio(SIN_ENVIO)
    setAnuncio('')
    setDialogo(nuevo)
  }

  /** Cancelar o Esc: se cierra y el foco vuelve al botón que lo abrió. */
  const cerrarDialogo = () => {
    setDialogo(null)
    setEnvio(SIN_ENVIO)
    origen.current?.focus()
  }

  const nombreRol = (rol: string) => t(`admin.roles.${rol}`)

  /** Texto del 409 o 404 que cambia la lista: el de RF45 lleva el rol actual o el motivo (DEC-119). */
  const mensajeDeCambio = (error: ApiError): string => {
    if (error.codigo === 'ROL_CAMBIADO') {
      const rol = error.problema?.rolActual
      return typeof rol === 'string' && ROLES_CON_NOMBRE.has(rol)
        ? t('admin.errores.ROL_CAMBIADO', { rol: nombreRol(rol) })
        : t('admin.errores.ROL_CAMBIADO_SIN_ROL')
    }
    if (error.codigo === 'CUENTA_NO_ELEGIBLE') {
      const motivo = error.problema?.motivo
      return typeof motivo === 'string' && MOTIVOS_NO_ELEGIBLE.has(motivo)
        ? t(`admin.errores.CUENTA_NO_ELEGIBLE_${motivo}`)
        : t('admin.errores.CUENTA_NO_ELEGIBLE')
    }
    return t(`admin.errores.${error.codigo}`)
  }

  /** 409 o 404 (DEC-39): aviso en la región, se cierran el diálogo y el detalle y se recargan la lista y el contador. */
  const trasCambioDeEstado = (error: ApiError) => {
    setDialogo(null)
    setDetalle(null)
    setEnvio(SIN_ENVIO)
    setAnuncio(mensajeDeCambio(error))
    pedirFoco()
    recargar()
  }

  /** Ejecuta una acción del Administrador y atiende sus errores; devuelve null si falló. */
  const ejecutar = async <T,>(accion: () => Promise<T>): Promise<T | null> => {
    setEnvio({ enviando: true, error: null, campo: null })
    try {
      const respuesta = await accion()
      setEnvio(SIN_ENVIO)
      return respuesta
    } catch (error: unknown) {
      if (!(error instanceof ApiError)) {
        setEnvio({ enviando: false, error: 'GENERICO', campo: null })
        return null
      }
      if (CAMBIAN_LA_LISTA.has(error.codigo)) {
        trasCambioDeEstado(error)
      } else if (error.status === 401) {
        // La sesión ya se cerró (httpClient); RutaProtegida lleva al login con el aviso.
        setEnvio(SIN_ENVIO)
      } else if (clasificarError(error) === 'sinPermiso') {
        setSinPermiso(true)
      } else if (error.codigo === 'DATOS_INVALIDOS') {
        const campos = (error.problema?.campos ?? {}) as Record<string, string>
        setEnvio({ enviando: false, error: campos.justificacion ? null : 'GENERICO', campo: campos.justificacion ?? null })
      } else {
        setEnvio({ enviando: false, error: codigoMostrado(error), campo: null })
      }
      return null
    }
  }

  const revisar = (idUsuario: string) => {
    const consulta = ++consultaDetalle.current
    setAnuncio('')
    setEnvio(SIN_ENVIO)
    setDetalle({ estado: 'cargando', idUsuario })
    consultarPrestador(idUsuario)
      .then((datos) => {
        if (consulta === consultaDetalle.current) setDetalle({ estado: 'ok', idUsuario, prestador: datos })
      })
      .catch((error: unknown) => {
        if (consulta !== consultaDetalle.current) return
        if (error instanceof ApiError && CAMBIAN_LA_LISTA.has(error.codigo)) {
          trasCambioDeEstado(error)
        } else if (error instanceof ApiError && clasificarError(error) === 'sinPermiso') {
          setSinPermiso(true)
        } else if (!(error instanceof ApiError && error.status === 401)) {
          const codigo = error instanceof ApiError ? codigoMostrado(error) : 'GENERICO'
          setDetalle({ estado: 'error', idUsuario, codigo })
        }
      })
  }

  /** Aprobar o rechazar (RF28): la fila sale de «por validar», así que se recargan la lista y el contador. */
  const validar = async (prestador: PrestadorDetalle, decision: 'Aprobar' | 'Rechazar', justificacion?: string) => {
    const resultado = await ejecutar(() => validarPrestador(prestador.idUsuario, decision, justificacion))
    if (!resultado) return
    setDialogo(null)
    setDetalle(null)
    setAnuncio(
      t(decision === 'Aprobar' ? 'admin.exito.aprobado' : 'admin.exito.rechazado', {
        nombre: resultado.representanteLegal,
      }),
    )
    pedirFoco(prestador.idUsuario)
    recargar()
  }

  const confirmar = async (accion: AccionConfirmable, idUsuario: string, nombre: string) => {
    if (accion === 'inactivar') {
      // RF22. La respuesta es el detalle, no la fila del listado: se recarga la página actual (condición 5).
      const resultado = await ejecutar(() => inactivarPrestador(idUsuario))
      if (!resultado) return
      setDialogo(null)
      setAnuncio(t('admin.exito.inactivado', { nombre }))
      pedirFoco(idUsuario)
      recargar()
      return
    }
    // RF62. La respuesta tiene la forma de la fila: se reemplaza en su sitio (aunque quede fuera del filtro, hasta la
    // próxima recarga) y el foco va a su acción nueva (foco estable, WCAG 2.4.3; DEC-52).
    const resultado = await ejecutar(() => (accion === 'desactivar' ? desactivarTurista : reactivarTurista)(idUsuario))
    if (!resultado) return
    reemplazarFila(resultado)
    setDialogo(null)
    setAnuncio(t(accion === 'desactivar' ? 'admin.exito.desactivado' : 'admin.exito.reactivado', { nombre }))
    pedirFoco(idUsuario)
  }

  /**
   * RF45 (DEC-120). Modificar: la respuesta tiene la forma de la fila, que se reemplaza en su sitio, y el foco va a su
   * acción nueva. Asignar o revocar: la cuenta cambia de pestaña, así que se recarga la página actual (DEC-121 si queda
   * vacía) y el foco va al título.
   */
  const confirmarCambioRol = async (
    modo: ModoCambioRol,
    idUsuario: string,
    rolActual: RolCambio,
    rolNuevo: RolCambio,
  ) => {
    const resultado = await ejecutar(() => cambiarRol(idUsuario, rolNuevo, rolActual))
    if (!resultado) return
    setDialogo(null)
    const datos = { nombre: resultado.nombre ?? resultado.correo, rol: nombreRol(resultado.rol) }
    if (modo === 'modificar') {
      reemplazarFila(resultado)
      setAnuncio(t('admin.exito.rolModificado', datos))
      pedirFoco(idUsuario)
      return
    }
    setAnuncio(t(modo === 'asignar' ? 'admin.exito.rolAsignado' : 'admin.exito.rolRevocado', datos))
    pedirFoco()
    recargar()
  }

  const registrarBoton = (idUsuario: string) => (elemento: HTMLButtonElement | null) => {
    if (elemento) botones.current.set(idUsuario, elemento)
    else botones.current.delete(idUsuario)
  }

  const accionPrestador = (prestador: PrestadorResumen) => {
    const de = <span className="solo-lector">: {prestador.representanteLegal}</span>
    switch (prestador.estado) {
      case 'Pendiente':
        return (
          <button
            ref={registrarBoton(prestador.idUsuario)}
            type="button"
            className="boton-secundario"
            onClick={() => revisar(prestador.idUsuario)}
          >
            {t('admin.acciones.revisar')}
            {de}
          </button>
        )
      case 'Activo':
        return (
          <button
            ref={registrarBoton(prestador.idUsuario)}
            type="button"
            className="boton-secundario"
            onClick={() =>
              abrirDialogo({
                tipo: 'confirmacion',
                accion: 'inactivar',
                idUsuario: prestador.idUsuario,
                nombre: prestador.representanteLegal,
              })
            }
          >
            {t('admin.acciones.inactivar')}
            {de}
          </button>
        )
      case 'Rechazado':
        return (
          <button
            ref={registrarBoton(prestador.idUsuario)}
            type="button"
            className="boton-secundario"
            onClick={() =>
              abrirDialogo({ tipo: 'motivo', idUsuario: prestador.idUsuario, nombre: prestador.representanteLegal })
            }
          >
            {t('admin.acciones.verMotivo')}
            {de}
          </button>
        )
      case 'Inactivo':
        // DEC-37: el prestador inactivo no se reactiva; visible y deshabilitado (DEC-22).
        return (
          <BotonNoDisponible ref={registrarBoton(prestador.idUsuario)}>
            {t('admin.acciones.reactivar')}
            {de}
          </BotonNoDisponible>
        )
    }
  }

  /** Acción deshabilitada con su motivo visible (DEC-22, DEC-122): el lector de pantalla lo lee como descripción. */
  const noDisponible = (texto: string, nombre: string, idMotivo: string, motivo: string) => (
    <>
      <BotonNoDisponible descripcion={idMotivo}>
        {texto}
        <span className="solo-lector">: {nombre}</span>
      </BotonNoDisponible>
      <span id={idMotivo} className="motivo-no-disponible">
        {motivo}
      </span>
    </>
  )

  const accionTurista = (turista: TuristaResumen) => {
    const accion: AccionConfirmable = turista.estado === 'Activo' ? 'desactivar' : 'reactivar'
    return (
      <div className="acciones-contenido">
        <button
          ref={registrarBoton(turista.idUsuario)}
          type="button"
          className="boton-secundario"
          onClick={() => abrirDialogo({ tipo: 'confirmacion', accion, idUsuario: turista.idUsuario, nombre: turista.nombre })}
        >
          {t(`admin.acciones.${accion}`)}
          <span className="solo-lector">: {turista.nombre}</span>
        </button>
        {/* RF45: solo un turista Activo recibe un rol (DEC-105, DEC-117), sin esperar el 409 DESACTIVADA. */}
        {turista.estado === 'Activo' ? (
          <button
            type="button"
            className="boton-secundario"
            onClick={() =>
              abrirDialogo({
                tipo: 'cambioRol',
                modo: 'asignar',
                idUsuario: turista.idUsuario,
                nombre: turista.nombre,
                rolActual: 'Turista',
              })
            }
          >
            {t('admin.acciones.asignarRol')}
            <span className="solo-lector">: {turista.nombre}</span>
          </button>
        ) : (
          noDisponible(
            t('admin.acciones.asignarRol'),
            turista.nombre,
            `${id}-motivo-${turista.idUsuario}`,
            t('admin.motivos.desactivada'),
          )
        )}
      </div>
    )
  }

  /**
   * RF45 (DEC-116): Gestor → «Pasar a Administrador»; Administrador → «Pasar a Gestor»; «Revocar rol» solo si es
   * `revocable` (si no, deshabilitado con su motivo). La cuenta propia no tiene acciones: el backend respondería 409
   * CAMBIO_ROL_PROPIO.
   */
  const accionPersonal = (cuenta: PersonalResumen) => {
    if (cuenta.idUsuario === idPropio) {
      return <span className="cuenta-propia">{t('admin.acciones.tuCuenta')}</span>
    }
    const nombre = cuenta.nombre ?? cuenta.correo
    const otro: RolCambio = cuenta.rol === 'Administrador' ? 'Gestor' : 'Administrador'
    const abrir = (modo: ModoCambioRol, rolNuevo: RolCambio) =>
      abrirDialogo({ tipo: 'cambioRol', modo, idUsuario: cuenta.idUsuario, nombre, rolActual: cuenta.rol, rolNuevo })
    return (
      <div className="acciones-contenido">
        <button
          ref={registrarBoton(cuenta.idUsuario)}
          type="button"
          className="boton-secundario"
          onClick={() => abrir('modificar', otro)}
        >
          {t('admin.acciones.pasarA', { rol: nombreRol(otro) })}
          <span className="solo-lector">: {nombre}</span>
        </button>
        {cuenta.revocable ? (
          <button type="button" className="boton-secundario" onClick={() => abrir('revocar', 'Turista')}>
            {t('admin.acciones.revocarRol')}
            <span className="solo-lector">: {nombre}</span>
          </button>
        ) : (
          noDisponible(
            t('admin.acciones.revocarRol'),
            nombre,
            `${id}-motivo-${cuenta.idUsuario}`,
            t('admin.motivos.sinRolBase'),
          )
        )}
      </div>
    )
  }

  const tabla = (() => {
    if (estado.estado !== 'ok') return null
    const { lista } = estado
    const titulo = t(`admin.pestanas.${criterios.pestana}`)
    if (lista.tipo === 'turistas') {
      const columnas: ColumnaCuentas[] = ['nombre', 'correo', 'pais', 'estado', 'accion'].map((clave) => ({
        clave,
        titulo: t(`admin.columnas.${clave}`),
      }))
      const filas: FilaCuentas[] = lista.pagina.elementos.map((turista) => ({
        id: turista.idUsuario,
        celdas: {
          nombre: turista.nombre,
          correo: turista.correo,
          pais: turista.pais ? nombrePais(turista.pais, i18n.language) : '—',
          estado: <InsigniaCuenta estado={turista.estado} />,
          accion: accionTurista(turista),
        },
      }))
      return <TablaCuentas titulo={titulo} columnas={columnas} filas={filas} />
    }
    if (lista.tipo === 'personal') {
      const columnas: ColumnaCuentas[] = ['nombre', 'correo', 'rol', 'registrado', 'accion'].map((clave) => ({
        clave,
        titulo: t(`admin.columnas.${clave}`),
      }))
      const filas: FilaCuentas[] = lista.pagina.elementos.map((cuenta) => ({
        id: cuenta.idUsuario,
        celdas: {
          nombre: cuenta.nombre ?? '—',
          correo: cuenta.correo,
          rol: nombreRol(cuenta.rol),
          registrado: formatearFecha(cuenta.fechaRegistro, i18n.language),
          accion: accionPersonal(cuenta),
        },
      }))
      return <TablaCuentas titulo={titulo} columnas={columnas} filas={filas} />
    }
    const porValidar = criterios.pestana === 'pendientes'
    const claves = porValidar
      ? ['representante', 'rnt', 'correoCorporativo', 'registrado', 'accion']
      : ['representante', 'rnt', 'correoCorporativo', 'estado', 'accion']
    const columnas: ColumnaCuentas[] = claves.map((clave) => ({ clave, titulo: t(`admin.columnas.${clave}`) }))
    const filas: FilaCuentas[] = lista.pagina.elementos.map((prestador) => ({
      id: prestador.idUsuario,
      celdas: {
        representante: prestador.representanteLegal,
        rnt: prestador.rnt,
        correoCorporativo: prestador.correoCorporativo,
        registrado: formatearFecha(prestador.fechaRegistro, i18n.language),
        estado: <InsigniaCuenta estado={prestador.estado} />,
        accion: accionPrestador(prestador),
      },
    }))
    return <TablaCuentas titulo={titulo} columnas={columnas} filas={filas} />
  })()

  const pagina = estado.estado === 'ok' ? estado.lista.pagina : null
  const vacia = pagina !== null && pagina.elementos.length === 0
  const mensajeVacio = vacia
    ? t(hayFiltrosAdmin(criterios) ? 'admin.vacio.conFiltros' : `admin.vacio.${criterios.pestana}`)
    : ''
  const estadosFiltro = estadosDe(criterios.pestana)
  const rolesFiltro = rolesDe(criterios.pestana)
  const ayudaBusqueda: Record<Pestana, string> = {
    pendientes: 'admin.buscarPrestadores',
    prestadores: 'admin.buscarPrestadores',
    turistas: 'admin.buscarTuristas',
    personal: 'admin.buscarPersonal',
  }

  return (
    <div className="admin-cuentas">
      <h1>{t('admin.titulo')}</h1>
      <p className="admin-cuentas__apoyo">{t('admin.apoyo')}</p>

      <div className="pestanas-cuentas" role="tablist" aria-label={t('admin.pestanasEtiqueta')} onKeyDown={alPulsarEnPestanas}>
        {PESTANAS.map((pestana) => {
          const activa = pestana === criterios.pestana
          return (
            <button
              key={pestana}
              ref={(elemento) => {
                if (elemento) pestanas.current.set(pestana, elemento)
                else pestanas.current.delete(pestana)
              }}
              id={`${id}-pestana-${pestana}`}
              type="button"
              role="tab"
              aria-selected={activa}
              aria-controls={`${id}-panel`}
              tabIndex={activa ? 0 : -1}
              onClick={() => cambiarPestana(pestana)}
            >
              {pestana === 'pendientes' && pendientes !== null
                ? t('admin.pestanas.pendientesContador', { n: pendientes })
                : t(`admin.pestanas.${pestana}`)}
            </button>
          )
        })}
      </div>

      <div id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-pestana-${criterios.pestana}`}>
        <form
          className="admin-cuentas__filtros"
          role="search"
          noValidate
          onSubmit={(evento) => {
            evento.preventDefault()
            navegar({ q: texto.trim(), pagina: 1 })
          }}
        >
          <div className="campo">
            <label htmlFor={`${id}-q`}>{t('admin.buscar')}</label>
            <div className="campo__control">
              <input
                id={`${id}-q`}
                type="search"
                value={texto}
                maxLength={MAXIMO_BUSQUEDA}
                placeholder={t(ayudaBusqueda[criterios.pestana])}
                onChange={(evento) => setTexto(evento.target.value)}
              />
            </div>
          </div>
          {estadosFiltro.length > 0 && (
            <div className="campo">
              <label htmlFor={`${id}-estado`}>{t('admin.estado')}</label>
              <div className="campo__control">
                <select
                  id={`${id}-estado`}
                  value={criterios.estado}
                  onChange={(evento) => navegar({ estado: evento.target.value, pagina: 1 })}
                >
                  <option value="">{t('admin.todos')}</option>
                  {estadosFiltro.map((valor) => (
                    <option key={valor} value={valor}>
                      {t(`admin.estados.${valor}`)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}
          {rolesFiltro.length > 0 && (
            <div className="campo">
              <label htmlFor={`${id}-rol`}>{t('admin.rol')}</label>
              <div className="campo__control">
                <select
                  id={`${id}-rol`}
                  value={criterios.rol}
                  onChange={(evento) => navegar({ rol: evento.target.value, pagina: 1 })}
                >
                  <option value="">{t('admin.todos')}</option>
                  {rolesFiltro.map((valor) => (
                    <option key={valor} value={valor}>
                      {nombreRol(valor)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}
          <button type="submit" className="boton-secundario">
            {t('admin.botonBuscar')}
          </button>
        </form>

        <section className="admin-cuentas__resultados" aria-labelledby={`${id}-resultados`}>
          <h2 id={`${id}-resultados`} ref={tituloResultados} tabIndex={-1}>
            {pagina ? t('admin.contador', { count: pagina.totalElementos }) : t('admin.resultados')}
          </h2>
          {/* Región de estado de WF-12: éxito, 409/404 (DEC-39) y lista vacía. Siempre presente. */}
          <div className="admin-cuentas__region" role="status" aria-live="polite">
            {anuncio && <p className="aviso-cuentas">{anuncio}</p>}
            {vacia && <p className="estado-catalogo__mensaje">{mensajeVacio}</p>}
          </div>

          {estado.estado === 'cargando' && (
            <p className="estado-carga" role="status" aria-busy="true">
              {t('admin.cargando')}
            </p>
          )}
          {estado.estado === 'error' && (
            <div className="estado-catalogo estado-catalogo--error" role="alert">
              <p className="estado-catalogo__mensaje">{t(`admin.erroresLista.${estado.error}`)}</p>
              <button type="button" className="boton-secundario" onClick={recargar}>
                {t('catalogo.busqueda.reintentar')}
              </button>
            </div>
          )}
          {pagina && !vacia && (
            <>
              {tabla}
              {pagina.totalPaginas > 1 && (
                <div className="admin-cuentas__paginacion">
                  <p>{t('admin.paginaDe', { actual: criterios.pagina, total: pagina.totalPaginas })}</p>
                  <Paginacion
                    actual={criterios.pagina}
                    total={pagina.totalPaginas}
                    onCambiar={(numero) => navegar({ pagina: numero })}
                  />
                </div>
              )}
            </>
          )}
        </section>

        {detalle && (
          <DetallePrestador
            detalle={detalle}
            enviando={envio.enviando && dialogo === null}
            error={dialogo === null ? envio.error : null}
            onAprobar={(prestador) => void validar(prestador, 'Aprobar')}
            onRechazar={(prestador) => abrirDialogo({ tipo: 'rechazo', prestador })}
            onReintentar={() => revisar(detalle.idUsuario)}
            onCerrar={() => {
              const idUsuario = detalle.idUsuario
              setDetalle(null)
              setEnvio(SIN_ENVIO)
              botones.current.get(idUsuario)?.focus()
            }}
          />
        )}
      </div>

      {dialogo?.tipo === 'rechazo' && (
        <DialogoRechazo
          nombre={dialogo.prestador.representanteLegal}
          enviando={envio.enviando}
          errorCampo={envio.campo}
          error={envio.error}
          onConfirmar={(justificacion) => void validar(dialogo.prestador, 'Rechazar', justificacion)}
          onCerrar={cerrarDialogo}
        />
      )}
      {dialogo?.tipo === 'confirmacion' && (
        <DialogoConfirmacion
          accion={dialogo.accion}
          nombre={dialogo.nombre}
          enviando={envio.enviando}
          error={envio.error}
          onConfirmar={() => void confirmar(dialogo.accion, dialogo.idUsuario, dialogo.nombre)}
          onCerrar={cerrarDialogo}
        />
      )}
      {dialogo?.tipo === 'cambioRol' && (
        <DialogoCambioRol
          modo={dialogo.modo}
          nombre={dialogo.nombre}
          rolActual={dialogo.rolActual}
          rolNuevo={dialogo.rolNuevo}
          enviando={envio.enviando}
          error={envio.error}
          onConfirmar={(rolNuevo) =>
            void confirmarCambioRol(dialogo.modo, dialogo.idUsuario, dialogo.rolActual, rolNuevo)
          }
          onCerrar={cerrarDialogo}
        />
      )}
      {dialogo?.tipo === 'motivo' && (
        <DialogoMotivo idUsuario={dialogo.idUsuario} nombre={dialogo.nombre} onCerrar={cerrarDialogo} />
      )}
    </div>
  )
}
