import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, ERROR_RED, servicioNoDisponible } from '../../../shared/api/httpClient'
import { SinPermiso } from '../../../shared/components/SinPermiso'
import { formatearDia } from '../../../shared/formato'
import { etiquetaCategoria } from '../../catalogo/componentes/etiquetas'
import { Dato, DatosServicio } from '../../servicios/DatosServicio'
import { InsigniaEstadoServicio } from '../../servicios/InsigniaEstadoServicio'
import { ESTADOS_PUBLICACION } from '../../servicios/servicioGestion'
import { ESTADOS_PRESTADOR, type EstadoPrestador } from '../adminApi'
import { DialogoRechazo } from '../componentes/DialogoRechazo'
import { InsigniaCuenta } from '../componentes/InsigniaCuenta'
import { clasificarError, type ErrorCuentas } from '../useCuentas'
import type { EstadoVueltaCola } from './AprobarServiciosPage'
import { consultarServicioAdmin, decidirServicio, type DecisionServicio, type ServicioAdmin } from './serviciosAdminApi'
import '../../catalogo/catalogo.css'
import '../admin.css'

type Carga = { estado: 'cargando' } | { estado: 'ok'; datos: ServicioAdmin } | { estado: 'error'; error: ErrorCuentas }

interface Envio {
  enviando: boolean
  /** Código de `admin.errores` del último intento (red, catálogo caído…). */
  error: string | null
  /** Código de `campos.justificacion` del último 400. */
  campo: string | null
}

const SIN_ENVIO: Envio = { enviando: false, error: null, campo: null }

/** Código que se muestra para un error que no cambia la cola ni la sesión. */
function codigoMostrado(error: ApiError): string {
  if (servicioNoDisponible(error)) return 'CATALOGO_NO_DISPONIBLE'
  return error.codigo === ERROR_RED ? 'ERROR_RED' : 'GENERICO'
}

const esEstadoPrestador = (valor: string): valor is EstadoPrestador =>
  (ESTADOS_PRESTADOR as readonly string[]).includes(valor)

/**
 * WF-19, revisión de un servicio (CU «Aprobar o rechazar servicio», RF21), solo para el Administrador (DEC-174).
 * Muestra el registro completo con el mapa de solo lectura, el prestador (id, RNT y estado de la cuenta; sin nombre,
 * DEC-144) y, si ya se decidió, la revisión (DEC-179). «Aprobar → Publicado» envía la decisión sin diálogo, como WF-12
 * (DEC-177); «Rechazar» abre «Justificar rechazo de servicio». Tras el 200, el 409 ESTADO_CAMBIADO o el 404, vuelve a la
 * cola con el aviso (DEC-52, DEC-178).
 */
export function RevisarServicioPage() {
  const { t, i18n } = useTranslation()
  const id = useId()
  const { id: idServicio = '' } = useParams()
  const ubicacion = useLocation()
  const navegar = useNavigate()
  const cola = `/admin/servicios${(ubicacion.state as { cola?: string } | null)?.cola ?? ''}`
  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [intento, setIntento] = useState(0)
  const [dialogo, setDialogo] = useState(false)
  const [envio, setEnvio] = useState<Envio>(SIN_ENVIO)
  const [sinPermiso, setSinPermiso] = useState(false)
  // Doble clic: el segundo llega antes de que React aplique `envio.enviando`.
  const enviando = useRef(false)
  const titulo = useRef<HTMLHeadingElement>(null)
  const botonRechazar = useRef<HTMLButtonElement>(null)

  const volverALaCola = useCallback((anuncio: string) => {
    const estado: EstadoVueltaCola = { anuncio }
    navegar(cola, { replace: true, state: estado })
  }, [navegar, cola])

  useEffect(() => {
    let vigente = true
    setCarga({ estado: 'cargando' })
    consultarServicioAdmin(idServicio)
      .then((datos) => {
        if (vigente) setCarga({ estado: 'ok', datos })
      })
      .catch((error: unknown) => {
        if (!vigente) return
        if (error instanceof ApiError && error.status === 404) {
          volverALaCola(t('admin.servicios.errores.SERVICIO_NO_ENCONTRADO'))
        } else {
          setCarga({ estado: 'error', error: clasificarError(error) })
        }
      })
    return () => {
      vigente = false
    }
  }, [idServicio, intento, volverALaCola, t])

  const cargado = carga.estado === 'ok'
  useEffect(() => {
    if (cargado) titulo.current?.focus()
  }, [cargado])

  if (sinPermiso || (carga.estado === 'error' && carga.error === 'sinPermiso')) return <SinPermiso />

  /** 409 con `estadoActual` (DEC-52): el texto lleva el estado nuevo si es uno conocido. */
  const mensajeEstadoCambiado = (error: ApiError, nombre: string): string => {
    const actual = error.problema?.estadoActual
    return typeof actual === 'string' && (ESTADOS_PUBLICACION as readonly string[]).includes(actual)
      ? t('admin.servicios.errores.ESTADO_CAMBIADO', { nombre, estado: t(`prestador.estados.${actual}`) })
      : t('admin.servicios.errores.ESTADO_CAMBIADO_SIN_ESTADO', { nombre })
  }

  const decidir = async (servicio: ServicioAdmin, decision: DecisionServicio, justificacion?: string) => {
    if (enviando.current) return
    enviando.current = true
    setEnvio({ enviando: true, error: null, campo: null })
    try {
      const resultado = await decidirServicio(servicio.id, decision, justificacion)
      volverALaCola(
        t(decision === 'Aprobar' ? 'admin.servicios.exito.aprobado' : 'admin.servicios.exito.rechazado', {
          nombre: resultado.nombreComercial,
        }),
      )
    } catch (error: unknown) {
      enviando.current = false
      if (!(error instanceof ApiError)) {
        setEnvio({ enviando: false, error: 'GENERICO', campo: null })
      } else if (error.codigo === 'ESTADO_CAMBIADO') {
        volverALaCola(mensajeEstadoCambiado(error, servicio.nombreComercial))
      } else if (error.status === 404) {
        volverALaCola(t('admin.servicios.errores.SERVICIO_NO_ENCONTRADO'))
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
    }
  }

  const cerrarDialogo = () => {
    setDialogo(false)
    setEnvio(SIN_ENVIO)
    botonRechazar.current?.focus()
  }

  const servicio = carga.estado === 'ok' ? carga.datos : null
  const pendiente = servicio?.estadoPublicacion === 'Pendiente'
  const cuenta = servicio?.estadoCuentaPrestador

  return (
    <div className="admin-cuentas admin-servicios">
      <nav className="migas" aria-label={t('admin.servicios.migas')}>
        <ol>
          <li>{t('admin.servicios.migaAdministracion')}</li>
          <li>
            <Link to={cola}>{t('admin.servicios.migaServicios')}</Link>
          </li>
          {servicio && <li aria-current="page">{servicio.nombreComercial}</li>}
        </ol>
      </nav>

      {carga.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('admin.servicios.revision.cargando')}
        </p>
      )}
      {carga.estado === 'error' && (
        <div className="estado-catalogo estado-catalogo--error" role="alert">
          <p className="estado-catalogo__mensaje">{t(`admin.servicios.erroresLista.${carga.error}`)}</p>
          <button type="button" className="boton-secundario" onClick={() => setIntento((n) => n + 1)}>
            {t('catalogo.busqueda.reintentar')}
          </button>
        </div>
      )}

      {servicio && (
        <article className="revision-servicio" aria-labelledby={`${id}-titulo`}>
          <h1 id={`${id}-titulo`} ref={titulo} tabIndex={-1}>
            {t('admin.servicios.revision.titulo', {
              nombre: servicio.nombreComercial,
              categoria: etiquetaCategoria(t, servicio.categoria),
            })}
          </h1>
          <dl className="datos-servicio revision-servicio__estado">
            <Dato etiqueta={t('admin.servicios.revision.estado')}>
              <InsigniaEstadoServicio estado={servicio.estadoPublicacion} />
            </Dato>
            <Dato etiqueta={t('admin.servicios.revision.registrado')}>
              {formatearDia(servicio.fechaRegistro, i18n.language)}
            </Dato>
          </dl>

          {!servicio.prestadorActivo && (
            <p className="aviso-cuentas aviso-cuentas--advertencia">{t('admin.servicios.revision.avisoInactivo')}</p>
          )}

          <section className="revision-servicio__seccion" aria-labelledby={`${id}-prestador`}>
            <h2 id={`${id}-prestador`}>{t('admin.servicios.revision.prestador')}</h2>
            <dl className="datos-servicio">
              <Dato etiqueta={t('admin.servicios.revision.idPrestador')}>{servicio.idPrestador}</Dato>
              <Dato etiqueta={t('admin.servicios.revision.rnt')}>{servicio.rnt}</Dato>
              <Dato etiqueta={t('admin.servicios.revision.cuenta')}>
                {cuenta && (esEstadoPrestador(cuenta) ? <InsigniaCuenta estado={cuenta} /> : cuenta)}
              </Dato>
              <Dato etiqueta={t('admin.servicios.columnas.prestadorActivo')}>
                {servicio.prestadorActivo ? t('admin.servicios.si') : t('admin.servicios.no')}
              </Dato>
            </dl>
          </section>

          {!pendiente && (
            <section className="revision-servicio__seccion" aria-labelledby={`${id}-revision`}>
              <h2 id={`${id}-revision`}>{t('admin.servicios.revision.revision')}</h2>
              <dl className="datos-servicio">
                <Dato etiqueta={t('admin.servicios.revision.revisadoEl')}>
                  {servicio.fechaRevision && formatearDia(servicio.fechaRevision, i18n.language)}
                </Dato>
                <Dato etiqueta={t('admin.servicios.revision.revisadoPor')}>{servicio.revisadoPor}</Dato>
                {servicio.estadoPublicacion === 'Rechazado' && (
                  <Dato etiqueta={t('admin.servicios.revision.motivoRechazo')}>{servicio.motivoRechazo}</Dato>
                )}
              </dl>
            </section>
          )}

          <section className="revision-servicio__seccion" aria-labelledby={`${id}-datos`}>
            <h2 id={`${id}-datos`}>{t('admin.servicios.revision.datos')}</h2>
            <DatosServicio servicio={servicio} mapa />
          </section>

          {envio.error && !dialogo && (
            <p className="aviso-cuentas aviso-cuentas--error" role="alert">
              {t(`admin.errores.${envio.error}`, { defaultValue: t('admin.errores.GENERICO') })}
            </p>
          )}
          <div className="revision-servicio__acciones">
            <Link className="boton-secundario" to={cola}>
              {t('admin.servicios.revision.volver')}
            </Link>
            {/* Publicado y Rechazado son finales (DEC-124): sin acciones de decisión (DEC-179). */}
            {pendiente && (
              <>
                <button
                  ref={botonRechazar}
                  type="button"
                  className="boton-secundario"
                  onClick={() => {
                    if (!envio.enviando) setDialogo(true)
                  }}
                >
                  {t('admin.servicios.revision.rechazar')}
                </button>
                <button
                  type="button"
                  className={`boton-principal${envio.enviando ? ' deshabilitado' : ''}`}
                  aria-disabled={envio.enviando ? true : undefined}
                  aria-busy={envio.enviando && !dialogo ? true : undefined}
                  onClick={() => {
                    void decidir(servicio, 'Aprobar')
                  }}
                >
                  {envio.enviando && !dialogo ? t('admin.dialogo.enviando') : t('admin.servicios.revision.aprobar')}
                </button>
              </>
            )}
          </div>
        </article>
      )}

      {servicio && dialogo && (
        <DialogoRechazo
          nombre={servicio.nombreComercial}
          titulo={t('admin.servicios.rechazo.titulo', { nombre: servicio.nombreComercial })}
          aviso={t('admin.servicios.rechazo.aviso')}
          enviando={envio.enviando}
          errorCampo={envio.campo}
          error={envio.error}
          onConfirmar={(justificacion) => {
            void decidir(servicio, 'Rechazar', justificacion)
          }}
          onCerrar={cerrarDialogo}
        />
      )}
    </div>
  )
}
