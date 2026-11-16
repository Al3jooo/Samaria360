import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Paginacion } from '../../../shared/components/Paginacion'
import { SinPermiso } from '../../../shared/components/SinPermiso'
import { formatearDia } from '../../../shared/formato'
import { etiquetaCategoria } from '../../catalogo/componentes/etiquetas'
import { ESTADOS_PUBLICACION } from '../../servicios/servicioGestion'
import { TablaCuentas } from '../componentes/TablaCuentas'
import { clasificarError, type ErrorCuentas } from '../useCuentas'
import {
  escribirCriteriosServiciosAdmin,
  leerCriteriosServiciosAdmin,
  type CriteriosServiciosAdmin,
} from './criteriosServiciosAdmin'
import { listarServiciosAdmin, type PaginaCola } from './serviciosAdminApi'
import '../../catalogo/catalogo.css'
import '../admin.css'

/** Lo que la revisión deja al volver a la cola: el texto para la región de estado (éxito, 409 o 404; DEC-177, DEC-178). */
export interface EstadoVueltaCola {
  anuncio?: string
}

type Carga = { estado: 'cargando' } | { estado: 'ok'; datos: PaginaCola } | { estado: 'error'; error: ErrorCuentas }

/**
 * WF-19 «Servicios por aprobar» (CU «Aprobar o rechazar servicio», «include» «Consultar listado pendiente»; RF21),
 * solo para el Administrador (DEC-174). Filtro de estado y página en la URL (DEC-175), del más antiguo al más reciente
 * (DEC-144). La región de estado anuncia lo que pasó en la revisión: aprobado, rechazado o ya revisado por otro (DEC-52).
 */
export function AprobarServiciosPage() {
  const { t, i18n } = useTranslation()
  const id = useId()
  const ubicacion = useLocation()
  const navegar = useNavigate()
  const [parametros, setParametros] = useSearchParams()
  const criterios = useMemo(() => leerCriteriosServiciosAdmin(parametros), [parametros])
  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [intento, setIntento] = useState(0)
  const [anuncio] = useState(() => (ubicacion.state as EstadoVueltaCola | null)?.anuncio ?? '')
  const titulo = useRef<HTMLHeadingElement>(null)

  // El anuncio de la revisión se lee una vez: el foco va al título y se quita del historial (no se repite al recargar).
  useEffect(() => {
    if (!anuncio) return
    titulo.current?.focus()
    navegar({ pathname: ubicacion.pathname, search: ubicacion.search }, { replace: true, state: null })
    // Consume el anuncio inicial una sola vez; cambiar filtros no debe volver a limpiar el historial ni mover el foco.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    let vigente = true
    setCarga({ estado: 'cargando' })
    listarServiciosAdmin(criterios.estado, criterios.pagina)
      .then((datos) => {
        if (vigente) setCarga({ estado: 'ok', datos })
      })
      .catch((error: unknown) => {
        // Un 401 ya cerró la sesión (httpClient) y RutaProtegida lleva al login.
        if (vigente) setCarga({ estado: 'error', error: clasificarError(error) })
      })
    return () => {
      vigente = false
    }
  }, [criterios.estado, criterios.pagina, intento])

  // Un filtro o una página que no son válidos se normalizan en la URL.
  useEffect(() => {
    const normalizados = escribirCriteriosServiciosAdmin(criterios)
    if (normalizados.toString() !== parametros.toString()) setParametros(normalizados, { replace: true })
  }, [criterios, parametros, setParametros])

  const datos = carga.estado === 'ok' ? carga.datos : null
  // Página fuera de rango (la cola cambió): se va a la última, sin dejar la anterior en el historial (DEC-121).
  useEffect(() => {
    if (datos && datos.totalPaginas > 0 && criterios.pagina > datos.totalPaginas) {
      setParametros(escribirCriteriosServiciosAdmin({ ...criterios, pagina: datos.totalPaginas }), { replace: true })
    }
  }, [datos, criterios, setParametros])

  if (carga.estado === 'error' && carga.error === 'sinPermiso') return <SinPermiso />

  const cambiar = (cambios: Partial<CriteriosServiciosAdmin>) =>
    setParametros(escribirCriteriosServiciosAdmin({ ...criterios, ...cambios }))
  const vacia = datos !== null && datos.elementos.length === 0
  const columnas = [
    { clave: 'nombre', titulo: t('admin.servicios.columnas.nombre') },
    { clave: 'categoria', titulo: t('admin.servicios.columnas.categoria') },
    { clave: 'rnt', titulo: t('admin.servicios.columnas.rnt') },
    { clave: 'prestadorActivo', titulo: t('admin.servicios.columnas.prestadorActivo') },
    { clave: 'registrado', titulo: t('admin.servicios.columnas.registrado') },
    { clave: 'accion', titulo: t('admin.servicios.columnas.accion') },
  ]

  return (
    <div className="admin-cuentas admin-servicios">
      <nav className="migas" aria-label={t('admin.servicios.migas')}>
        <ol>
          <li>{t('admin.servicios.migaAdministracion')}</li>
          <li aria-current="page">{t('admin.servicios.migaServicios')}</li>
        </ol>
      </nav>
      <h1>{t('admin.servicios.titulo')}</h1>
      <p className="admin-cuentas__apoyo">{t('admin.servicios.apoyo')}</p>

      <div className="admin-cuentas__filtros">
        <div className="campo">
          <label htmlFor={`${id}-estado`}>{t('admin.servicios.filtroEstado')}</label>
          <div className="campo__control">
            <select
              id={`${id}-estado`}
              value={criterios.estado}
              onChange={(evento) =>
                cambiar({ estado: evento.target.value as CriteriosServiciosAdmin['estado'], pagina: 1 })
              }
            >
              {ESTADOS_PUBLICACION.map((estado) => (
                <option key={estado} value={estado}>
                  {t(`prestador.estados.${estado}`)}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <section className="admin-cuentas__resultados" aria-labelledby={`${id}-resultados`}>
        <h2 id={`${id}-resultados`} ref={titulo} tabIndex={-1}>
          {datos ? t('admin.servicios.contador', { count: datos.totalElementos }) : t('admin.resultados')}
        </h2>
        {/* Región de estado de WF-19: aprobado, rechazado, 409 y 404 (DEC-52) y cola vacía. Siempre presente. */}
        <div className="admin-cuentas__region" role="status" aria-live="polite">
          {anuncio && <p className="aviso-cuentas">{anuncio}</p>}
          {vacia && <p className="estado-catalogo__mensaje">{t(`admin.servicios.vacio.${criterios.estado}`)}</p>}
        </div>

        {carga.estado === 'cargando' && (
          <p className="estado-carga" role="status" aria-busy="true">
            {t('admin.servicios.cargando')}
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
        {datos && !vacia && (
          <TablaCuentas
            titulo={t('admin.servicios.tabla')}
            columnas={columnas}
            filas={datos.elementos.map((servicio) => ({
              id: servicio.id,
              celdas: {
                nombre: servicio.nombreComercial,
                categoria: etiquetaCategoria(t, servicio.categoria),
                rnt: servicio.rnt ?? t('comun.sinDatos'),
                prestadorActivo: servicio.prestadorActivo ? t('admin.servicios.si') : t('admin.servicios.noOculto'),
                registrado: formatearDia(servicio.fechaRegistro, i18n.language),
                accion: (
                  <Link
                    to={`/admin/servicios/${encodeURIComponent(servicio.id)}`}
                    state={{ cola: ubicacion.search }}
                    aria-label={t('admin.servicios.revisarDe', { nombre: servicio.nombreComercial })}
                  >
                    {t('admin.servicios.revisar')}
                  </Link>
                ),
              },
            }))}
          />
        )}
        {datos && datos.totalPaginas > 1 && (
          <Paginacion actual={criterios.pagina} total={datos.totalPaginas} onCambiar={(pagina) => cambiar({ pagina })} />
        )}
      </section>
    </div>
  )
}
