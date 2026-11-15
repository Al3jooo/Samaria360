import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, ERROR_RED, servicioNoDisponible } from '../../shared/api/httpClient'
import { CATEGORIAS, CONDICIONES_ACCESIBILIDAD, SERVICIOS_INCLUIDOS, type Categoria } from '../catalogo/catalogoApi'
import { etiquetaCategoria } from '../catalogo/componentes/etiquetas'
import { formatearCentavos, centavos } from '../reservas/dinero'
import {
  CATEGORIAS_CON_OFERTA,
  borradorVacio,
  construirCuerpo,
  ordenarSalidas,
  precioDesde,
  rutasConControl,
  validarBorrador,
  type BorradorServicio,
} from './borradorServicio'
import { SelectorImagenes, elegirImagenes, type ImagenElegida } from '../gestor/componentes/SelectorImagenes'
import { MAXIMO_IMAGENES, motivoCantidad } from '../gestor/imagenesAtractivo'
import { CamposCategoria } from './componentes/CamposCategoria'
import { CampoServicio, ErrorGrupo } from './componentes/CampoServicio'
import { EditorOferta } from './componentes/EditorOferta'
import { EditorUnidades } from './componentes/EditorUnidades'
import { SelectorUbicacion } from './componentes/SelectorUbicacion'
import { erroresAnidados, idDeCampo, textoErrorServicio, type ErroresServicio } from './rutasCampos'
import { registrarServicio } from './serviciosPrestadorApi'
import { usePerfilPrestador } from './usePerfilPrestador'
import './prestador.css'
import '../gestor/gestor.css'

/** Estado de la navegación al detalle propio después del 201 (DEC-168). */
export interface EstadoEnviado {
  enviado: true
}

/** Un rechazo del POST: errores por campo o un mensaje general (clave de `prestador.registro.errores`). */
function aRechazo(error: unknown, conControl: readonly string[]): { campos: ErroresServicio; general: string | null } {
  if (!(error instanceof ApiError)) return { campos: {}, general: 'GENERICO' }
  if (error.codigo === 'DATOS_INVALIDOS') {
    const todos = erroresAnidados(error.problema?.campos)
    const propios = Object.fromEntries(Object.entries(todos).filter(([ruta]) => conControl.includes(ruta)))
    const sinControl = Object.keys(todos).length - Object.keys(propios).length
    return { campos: propios, general: sinControl > 0 || Object.keys(todos).length === 0 ? 'DATOS_INVALIDOS' : null }
  }
  // DEC-162: la cuenta no está Activa o no tiene RNT; se informa al recibir el 403, sin perder lo escrito.
  if (error.status === 403 && error.codigo === 'PRESTADOR_NO_HABILITADO') return { campos: {}, general: 'PRESTADOR_NO_HABILITADO' }
  if (error.status === 403 && error.codigo === 'ACCESO_DENEGADO') return { campos: {}, general: 'ACCESO_DENEGADO' }
  if (error.status === 413 || error.codigo === 'CARGA_DEMASIADO_GRANDE') return { campos: {}, general: 'CARGA_DEMASIADO_GRANDE' }
  if (servicioNoDisponible(error)) return { campos: {}, general: 'SERVICIO_NO_DISPONIBLE' }
  if (error.codigo === ERROR_RED) return { campos: {}, general: 'ERROR_RED' }
  return { campos: {}, general: 'GENERICO' }
}

const esRutaImagen = (ruta: string) => ruta === 'imagenes' || ruta.startsWith('imagenes[')

/** Las rutas de las fotos que tienen control en pantalla: el grupo y cada miniatura (DEC-318). */
const rutasImagenes = (cantidad: number) => ['imagenes', ...Array.from({ length: cantidad }, (_, i) => `imagenes[${i}]`)]

/** Los errores de las fotos salen de sus motivos: se recalculan con cada cambio de la lista. */
function conErroresDeImagenes(actuales: ErroresServicio, lista: ImagenElegida[]): ErroresServicio {
  const resto = Object.fromEntries(Object.entries(actuales).filter(([ruta]) => !esRutaImagen(ruta)))
  lista.forEach((imagen, indice) => {
    if (imagen.motivo) resto[`imagenes[${indice}]`] = imagen.motivo
  })
  return resto
}

/** Elemento de WF-08 que no está en el contrato (DEC-167): visible, deshabilitado (DEC-22) y sin acción. */
function DatoNoDisponible({ etiqueta }: { etiqueta: string }) {
  const { t } = useTranslation()
  return (
    <div role="group" aria-disabled="true" aria-label={etiqueta} className="prestador__no-disponible">
      <span>{etiqueta}</span>
      <span className="deshabilitado">
        <span aria-hidden="true">—</span>
        <span className="solo-lector">{t('comun.noDisponible')}</span>
      </span>
    </div>
  )
}

/**
 * WF-08 «Registrar servicio comercial» (RF09, RF10, RF37; parte de RF27 y RF58), solo para el Prestador
 * (RutaProtegida, DEC-161). Una sola página con secciones en el orden del wireframe (DEC-163). El borrador conserva
 * lo escrito de cada categoría, pero solo se envía la elegida. Validación en el cliente de obligatorios, formatos y
 * longitudes (DEC-166); el resto lo decide el servidor y su 400 marca cada control por su ruta. El 201 lleva al
 * detalle propio con «Pendiente de aprobación» (DEC-168). Un 403 PRESTADOR_NO_HABILITADO (DEC-162) o un error del
 * servidor no borran lo escrito; un 401 cierra la sesión (httpClient) y RutaProtegida lleva al login.
 *
 * El RNT se muestra con el de `GET /api/v1/auth/prestador/perfil` (O16, DEC-203): esa llamada no bloquea el
 * formulario; mientras carga, si falla o si no hay RNT, queda el texto de DEC-167. El RNT nunca viaja en el cuerpo.
 */
export function RegistrarServicioPage() {
  const { t, i18n } = useTranslation()
  const id = useId()
  const navegar = useNavigate()
  const [borrador, setBorrador] = useState<BorradorServicio>(borradorVacio)
  const [errores, setErrores] = useState<ErroresServicio>({})
  const [general, setGeneral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const mensajeGeneral = useRef<HTMLParagraphElement>(null)
  // «Fotos del servicio» (DEC-318): de 1 a 5, con las reglas de los atractivos y de la galería (RF48).
  const [imagenes, setImagenes] = useState<ImagenElegida[]>([])
  const [excedidas, setExcedidas] = useState(0)
  const vistas = useRef<string[]>([])
  vistas.current = imagenes.map((imagen) => imagen.vistaPrevia)
  useEffect(() => () => vistas.current.forEach((url) => URL.revokeObjectURL(url)), [])
  const { carga: perfil } = usePerfilPrestador()
  const rnt = perfil.estado === 'ok' && perfil.perfil.rnt ? perfil.perfil.rnt : null

  const cambiar = (cambios: Partial<BorradorServicio>, rutas: string[] = []) => {
    setBorrador((actual) => ({ ...actual, ...cambios }))
    if (rutas.length > 0) {
      setErrores((actuales) => Object.fromEntries(Object.entries(actuales).filter(([ruta]) => !rutas.includes(ruta))))
    }
  }
  const cambiarSeccion = <S extends 'tour' | 'gastronomia' | 'alquiler' | 'alojamiento'>(
    seccion: S,
    cambios: Partial<BorradorServicio[S]>,
  ) => {
    setBorrador((actual) => ({ ...actual, [seccion]: { ...actual[seccion], ...cambios } }))
    // Un cambio en la sección limpia sus errores de detalle (el servidor los vuelve a marcar si siguen).
    const campos = Object.keys(cambios).map((campo) => `detalleCategoria.${campo}`)
    setErrores((actuales) => Object.fromEntries(Object.entries(actuales).filter(([ruta]) => !campos.includes(ruta))))
  }
  /** Al quitar una fila los índices cambian: se limpian los errores de esas filas (DEC-164). */
  const limpiarFilas = (prefijo: string) =>
    setErrores((actuales) => Object.fromEntries(Object.entries(actuales).filter(([ruta]) => !ruta.startsWith(prefijo))))
  const error = (ruta: string) => textoErrorServicio(t, ruta, errores[ruta])

  const agregarImagenes = async (archivos: File[]) => {
    const libres = Math.max(0, MAXIMO_IMAGENES - imagenes.length)
    setExcedidas(Math.max(0, archivos.length - libres))
    const nuevas = await elegirImagenes(archivos.slice(0, libres))
    setImagenes((actuales) => {
      const lista = [...actuales, ...nuevas]
      setErrores((previos) => conErroresDeImagenes(previos, lista))
      return lista
    })
  }

  const quitarImagen = (indice: number) => {
    const quitada = imagenes[indice]
    if (quitada) URL.revokeObjectURL(quitada.vistaPrevia)
    setExcedidas(0)
    const lista = imagenes.filter((_, actual) => actual !== indice)
    setImagenes(lista)
    setErrores((actuales) => conErroresDeImagenes(actuales, lista))
  }

  const conControl = (objetivo: BorradorServicio) => [...rutasConControl(objetivo), ...rutasImagenes(imagenes.length)]

  const enfocarPrimero = (encontrados: ErroresServicio, objetivo: BorradorServicio) => {
    const primera = conControl(objetivo).find((ruta) => encontrados[ruta])
    if (primera) setTimeout(() => document.getElementById(idDeCampo(primera))?.focus(), 0)
  }

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    if (enviando) return
    // Las salidas se ordenan antes de validar y enviar: el índice de un error es el de la fila (DEC-164).
    const ordenado = ordenarSalidas(borrador)
    setBorrador(ordenado)
    setGeneral(null)
    const locales = conErroresDeImagenes(validarBorrador(ordenado), imagenes)
    const cantidad = motivoCantidad(imagenes.length)
    if (cantidad) locales.imagenes = cantidad
    setErrores(locales)
    if (Object.keys(locales).length > 0) {
      enfocarPrimero(locales, ordenado)
      return
    }
    setEnviando(true)
    try {
      const servicio = await registrarServicio(
        construirCuerpo(ordenado),
        imagenes.map((imagen) => imagen.archivo),
      )
      const estado: EstadoEnviado = { enviado: true }
      navegar(`/prestador/servicios/${encodeURIComponent(servicio.id)}`, { state: estado })
    } catch (fallo) {
      // Un 401 ya cerró la sesión: RutaProtegida lleva al login.
      if (fallo instanceof ApiError && fallo.status === 401) return
      const rechazo = aRechazo(fallo, conControl(ordenado))
      setErrores(rechazo.campos)
      setGeneral(rechazo.general)
      if (Object.keys(rechazo.campos).length > 0) enfocarPrimero(rechazo.campos, ordenado)
      else setTimeout(() => mensajeGeneral.current?.focus(), 0)
    } finally {
      setEnviando(false)
    }
  }

  const categoria = borrador.categoria
  const desde = precioDesde(borrador.alojamiento.unidades)
  const desdeCentavos = desde === null ? null : centavos(desde)
  const alternar = <T extends string>(lista: T[], valor: T) =>
    lista.includes(valor) ? lista.filter((v) => v !== valor) : [...lista, valor]
  const idCategoria = idDeCampo('categoria')

  return (
    <div className="prestador">
      <nav aria-label={t('prestador.migas')} className="prestador__migas">
        <ol>
          <li>
            <Link to="/prestador/servicios">{t('prestador.misServicios.titulo')}</Link>
          </li>
          <li aria-current="page">{t('prestador.registro.titulo')}</li>
        </ol>
      </nav>
      <h1>{t('prestador.registro.titulo')}</h1>
      <p className="prestador__apoyo">{t('prestador.registro.apoyo')}</p>
      <p className="prestador__aviso">{t('prestador.registro.avisoPendiente')}</p>

      <form className="prestador__formulario" noValidate onSubmit={(evento) => void enviar(evento)} aria-labelledby={`${id}-titulo`}>
        <span id={`${id}-titulo`} hidden>
          {t('prestador.registro.titulo')}
        </span>

        <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-datos`}>
          <h2 id={`${id}-datos`}>{t('prestador.registro.secciones.datos')}</h2>
          <CampoServicio ruta="nombreComercial" etiqueta={t('prestador.campos.nombreComercial')}
            valor={borrador.nombreComercial} onCambiar={(v) => cambiar({ nombreComercial: v }, ['nombreComercial'])}
            error={error('nombreComercial')} />
          <div className="campo">
            <label htmlFor={idCategoria}>{t('prestador.campos.categoria')}</label>
            <div className="campo__control">
              <select
                id={idCategoria}
                value={categoria}
                aria-invalid={error('categoria') ? true : undefined}
                aria-describedby={[`${idCategoria}-ayuda`, error('categoria') ? `${idCategoria}-error` : null].filter(Boolean).join(' ')}
                onChange={(evento) => cambiar({ categoria: evento.target.value as Categoria | '' }, ['categoria'])}
              >
                <option value="">{t('prestador.registro.elegirCategoria')}</option>
                {CATEGORIAS.map((valor) => (
                  <option key={valor} value={valor}>
                    {etiquetaCategoria(t, valor)}
                  </option>
                ))}
              </select>
            </div>
            <p id={`${idCategoria}-ayuda`} className="campo__ayuda">
              {t('prestador.registro.categoriaAyuda')}
            </p>
            <ErrorGrupo ruta="categoria" error={error('categoria')} />
          </div>
          <div className="prestador__rnt">
            <span className="prestador__rnt-etiqueta">{t('prestador.campos.rnt')}</span>
            <span>{rnt ?? t('prestador.registro.rntDeTuCuenta')}</span>
            {rnt && <span className="campo__ayuda">{t('prestador.registro.rntAyuda')}</span>}
          </div>
          <CampoServicio ruta="descripcion" etiqueta={t('prestador.campos.descripcion')} multilinea
            valor={borrador.descripcion} onCambiar={(v) => cambiar({ descripcion: v }, ['descripcion'])}
            error={error('descripcion')} />
        </section>

        <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-contacto`}>
          <h2 id={`${id}-contacto`}>{t('prestador.registro.secciones.contacto')}</h2>
          <div className="prestador__columnas">
            <CampoServicio ruta="contacto.telefono" etiqueta={t('prestador.campos.telefono')} type="tel"
              autoComplete="tel" valor={borrador.telefono}
              onCambiar={(v) => cambiar({ telefono: v }, ['contacto.telefono'])} error={error('contacto.telefono')} />
            <CampoServicio ruta="contacto.correo" etiqueta={t('prestador.campos.correo')} type="email"
              autoComplete="email" valor={borrador.correo}
              onCambiar={(v) => cambiar({ correo: v }, ['contacto.correo'])} error={error('contacto.correo')} />
          </div>
          <CampoServicio ruta="direccion" etiqueta={t('prestador.campos.direccion')} autoComplete="street-address"
            valor={borrador.direccion} onCambiar={(v) => cambiar({ direccion: v }, ['direccion'])}
            error={error('direccion')} />
          <SelectorUbicacion
            valor={{ latitud: borrador.latitud, longitud: borrador.longitud }}
            errores={errores}
            onCambiar={({ latitud, longitud }) =>
              cambiar({ latitud, longitud }, ['coordenadas', 'coordenadas.latitud', 'coordenadas.longitud'])
            }
          />
          <CampoServicio ruta="horarioAtencion" etiqueta={t('prestador.campos.horarioAtencion')} opcional multilinea
            ayuda={t('prestador.registro.horarioAyuda')} valor={borrador.horarioAtencion}
            onCambiar={(v) => cambiar({ horarioAtencion: v }, ['horarioAtencion'])} error={error('horarioAtencion')} />
        </section>

        <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-precio`}>
          <h2 id={`${id}-precio`}>{t('prestador.registro.secciones.precio')}</h2>
          {categoria === 'Alojamiento' ? (
            <div className="campo">
              <span className="prestador__rnt-etiqueta" id={`${id}-desde`}>
                {t('prestador.campos.precioBase')}
              </span>
              <output aria-labelledby={`${id}-desde`} className="prestador__desde">
                {desdeCentavos === null ? '—' : formatearCentavos(desdeCentavos, i18n.language)}
              </output>
              <p className="campo__ayuda">{t('prestador.registro.precioDerivado')}</p>
            </div>
          ) : (
            <CampoServicio ruta="precioBase" etiqueta={t('prestador.campos.precioBase')} inputMode="decimal" sufijo="COP"
              ayuda={categoria === '' ? t('prestador.registro.precioSinCategoria') : undefined}
              valor={borrador.precioBase} onCambiar={(v) => cambiar({ precioBase: v }, ['precioBase'])}
              error={error('precioBase')} />
          )}
          <div className="prestador__columnas">
            <DatoNoDisponible etiqueta={t('prestador.registro.cobraPor')} />
            <DatoNoDisponible etiqueta={t('prestador.registro.incluyeImpuestos')} />
          </div>
        </section>

        <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-categoria`}>
          <h2 id={`${id}-categoria`}>
            {categoria === ''
              ? t('prestador.registro.secciones.categoria')
              : t('prestador.registro.secciones.categoriaDe', { categoria: etiquetaCategoria(t, categoria) })}
          </h2>
          {categoria === '' && <p className="campo__ayuda">{t('prestador.registro.sinCategoria')}</p>}
          {categoria === 'Alojamiento' && (
            <>
              <fieldset
                id={idDeCampo('detalleCategoria.serviciosIncluidos')}
                tabIndex={-1}
                className="prestador__grupo"
                aria-describedby={error('detalleCategoria.serviciosIncluidos') ? `${idDeCampo('detalleCategoria.serviciosIncluidos')}-error` : undefined}
              >
                <legend>{t('prestador.campos.serviciosIncluidos')}</legend>
                <ul className="prestador__casillas">
                  {SERVICIOS_INCLUIDOS.map((servicio) => (
                    <li key={servicio}>
                      <label>
                        <input
                          type="checkbox"
                          checked={borrador.alojamiento.serviciosIncluidos.includes(servicio)}
                          onChange={() =>
                            cambiarSeccion('alojamiento', {
                              serviciosIncluidos: alternar(borrador.alojamiento.serviciosIncluidos, servicio),
                            })
                          }
                        />
                        {t(`catalogo.serviciosIncluidos.${servicio}`)}
                      </label>
                    </li>
                  ))}
                </ul>
                <ErrorGrupo ruta="detalleCategoria.serviciosIncluidos" error={error('detalleCategoria.serviciosIncluidos')} />
              </fieldset>
              <EditorUnidades
                unidades={borrador.alojamiento.unidades}
                errores={errores}
                onCambiar={(unidades) => cambiarSeccion('alojamiento', { unidades })}
                onQuitar={(unidades) => {
                  cambiarSeccion('alojamiento', { unidades })
                  limpiarFilas('detalleCategoria.unidades')
                }}
              />
            </>
          )}
          <CamposCategoria borrador={borrador} errores={errores} onCambiar={cambiarSeccion} />
        </section>

        <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-oferta`}>
          <h2 id={`${id}-oferta`}>{t('prestador.registro.secciones.oferta')}</h2>
          {categoria !== '' && CATEGORIAS_CON_OFERTA.includes(categoria) ? (
            <>
              <p className="campo__ayuda">{t('prestador.registro.ofertaAyuda')}</p>
              <EditorOferta
                conSalidas={categoria === 'Tour'}
                dias={categoria === 'Tour' ? borrador.tour.diasOperacion : borrador.alojamiento.diasOperacion}
                salidas={borrador.tour.salidas}
                errores={errores}
                onCambiarDias={(diasOperacion) => {
                  if (categoria === 'Tour') cambiarSeccion('tour', { diasOperacion })
                  else cambiarSeccion('alojamiento', { diasOperacion })
                  setErrores((actuales) => {
                    const { ['oferta.diasOperacion']: _quitado, oferta: _oferta, ...resto } = actuales
                    return resto
                  })
                }}
                onCambiarSalidas={(salidas) => {
                  cambiarSeccion('tour', { salidas })
                  limpiarFilas('oferta.salidas')
                }}
                onQuitarSalida={(salidas) => {
                  cambiarSeccion('tour', { salidas })
                  limpiarFilas('oferta.salidas')
                }}
              />
            </>
          ) : (
            <p className="campo__ayuda">
              {t(categoria === '' ? 'prestador.registro.ofertaSinCategoria' : 'prestador.registro.sinOferta')}
            </p>
          )}
        </section>

        <SelectorImagenes
          titulo={t('prestador.registro.secciones.fotos')}
          imagenes={imagenes}
          errores={errores}
          excedidas={excedidas}
          onAgregar={(archivos) => void agregarImagenes(archivos)}
          onQuitar={quitarImagen}
        />

        <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-accesibilidad`}>
          <h2 id={`${id}-accesibilidad`}>{t('prestador.registro.secciones.accesibilidad')}</h2>
          <fieldset
            id={idDeCampo('condicionesAccesibilidad')}
            tabIndex={-1}
            className="prestador__grupo"
            aria-describedby={error('condicionesAccesibilidad') ? `${idDeCampo('condicionesAccesibilidad')}-error` : undefined}
          >
            <legend className="solo-lector">{t('prestador.registro.secciones.accesibilidad')}</legend>
            <ul className="prestador__casillas">
              {CONDICIONES_ACCESIBILIDAD.map((condicion) => (
                <li key={condicion}>
                  <label>
                    <input
                      type="checkbox"
                      checked={borrador.condicionesAccesibilidad.includes(condicion)}
                      onChange={() =>
                        cambiar(
                          { condicionesAccesibilidad: alternar(borrador.condicionesAccesibilidad, condicion) },
                          ['condicionesAccesibilidad'],
                        )
                      }
                    />
                    {t(`catalogo.accesibilidad.${condicion}`)}
                  </label>
                </li>
              ))}
            </ul>
            <ErrorGrupo ruta="condicionesAccesibilidad" error={error('condicionesAccesibilidad')} />
          </fieldset>
        </section>

        <DatoNoDisponible etiqueta={t('prestador.registro.veracidad')} />

        <p
          ref={mensajeGeneral}
          tabIndex={-1}
          className={general ? 'prestador__error-general' : undefined}
          role="alert"
        >
          {general && t(`prestador.registro.errores.${general}`)}
        </p>

        <div className="prestador__acciones">
          <button type="submit" className="boton-principal" disabled={enviando} aria-busy={enviando || undefined}>
            {enviando ? t('prestador.registro.enviando') : t('prestador.registro.enviar')}
          </button>
          <Link className="boton-secundario" to="/prestador/servicios">
            {t('prestador.registro.cancelar')}
          </Link>
        </div>
      </form>
    </div>
  )
}
