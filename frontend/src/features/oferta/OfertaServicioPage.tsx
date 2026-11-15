import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../shared/api/httpClient'
import { formatearFecha } from '../reservas/fechasBogota'
import { consultarMiServicio, type ServicioGestion } from '../prestador/serviciosPrestadorApi'
import {
  DIAS_SEMANA,
  ajustarFila,
  cambiarPlantilla,
  cerrarFila,
  consultarInventario,
  type FilaInventario,
  type Inventario,
} from './ofertaApi'
import './oferta.css'

type Carga = { estado: 'cargando' } | { estado: 'error'; codigo: string } | { estado: 'ok'; inventario: Inventario; servicio: ServicioGestion }

type Aviso = { tipo: 'exito' | 'error'; texto: string } | null

const CODIGOS_CONOCIDOS = new Set([
  'CUPOS_RESERVADOS',
  'FECHA_CON_RESERVAS',
  'OFERTA_NO_DISPONIBLE',
  'DATOS_INVALIDOS',
  'SERVICIO_NO_DISPONIBLE',
])

function codigoDe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'SIN_PERMISO'
    if (error.status === 404) return 'OFERTA_NO_DISPONIBLE'
    if (error.status >= 502) return 'SERVICIO_NO_DISPONIBLE'
    return CODIGOS_CONOCIDOS.has(error.codigo) ? error.codigo : 'OTRO'
  }
  return 'OTRO'
}

function claveFila(f: Pick<FilaInventario, 'fecha' | 'horaInicio' | 'idUnidad'>) {
  return `${f.fecha}|${f.horaInicio ?? ''}|${f.idUnidad ?? ''}`
}

function unidadesDe(servicio: ServicioGestion): { idUnidad: string; nombre: string }[] {
  const unidades = (servicio.detalleCategoria?.unidades as { idUnidad?: string; nombre?: string }[] | undefined) ?? []
  return unidades
    .filter((u) => typeof u.idUnidad === 'string')
    .map((u) => ({ idUnidad: u.idUnidad as string, nombre: u.nombre ?? (u.idUnidad as string) }))
}

/**
 * WF-38 «Oferta y disponibilidad» (RF19; DEC-310), solo para el Prestador: el inventario de las próximas fechas de su
 * servicio (Tour o Alojamiento), con los cupos de cada fecha o salida, cerrar y abrir fechas, agregar una salida y la
 * plantilla de días y horarios de las fechas que aún no tienen inventario. El rango vive en la URL; si falta, el
 * servidor decide (de mañana a 14 días). No se puede bajar de lo reservado ni cerrar una fecha con reservas
 * Confirmadas: lo decide ms-booking (409) y la pantalla lo explica.
 */
export function OfertaServicioPage() {
  const { t, i18n } = useTranslation()
  const { id = '' } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const desde = parametros.get('desde') ?? ''
  const hasta = parametros.get('hasta') ?? ''
  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [rango, setRango] = useState({ desde, hasta })
  const [cupos, setCupos] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<Aviso>(null)
  const [ocupado, setOcupado] = useState(false)
  const [nueva, setNueva] = useState({ fecha: '', horaInicio: '', idUnidad: '', cuposTotales: '' })
  const [plantilla, setPlantilla] = useState<{ dias: string[]; salidas: string } | null>(null)

  const cargar = useCallback(() => {
    let vigente = true
    setCarga({ estado: 'cargando' })
    Promise.all([consultarInventario(id, desde, hasta), consultarMiServicio(id)])
      .then(([inventario, servicio]) => {
        if (!vigente) return
        setCarga({ estado: 'ok', inventario, servicio })
        setCupos({})
        setPlantilla({ dias: inventario.diasOperacion, salidas: inventario.salidas.join(', ') })
      })
      .catch((error) => {
        if (vigente) setCarga({ estado: 'error', codigo: codigoDe(error) })
      })
    return () => {
      vigente = false
    }
  }, [id, desde, hasta])

  useEffect(() => cargar(), [cargar])

  const mensajeError = (error: unknown) => {
    const codigo = codigoDe(error)
    const reservados = error instanceof ApiError ? error.problema?.reservados : undefined
    return t(`oferta.errores.${codigo}`, { reservados: typeof reservados === 'number' ? reservados : '' })
  }

  const ejecutar = async (accion: () => Promise<unknown>, exito: string) => {
    setOcupado(true)
    setAviso(null)
    try {
      await accion()
      setAviso({ tipo: 'exito', texto: exito })
      cargar()
    } catch (error) {
      setAviso({ tipo: 'error', texto: mensajeError(error) })
    } finally {
      setOcupado(false)
    }
  }

  const filtrar = (evento: FormEvent) => {
    evento.preventDefault()
    const nuevos = new URLSearchParams()
    if (rango.desde) nuevos.set('desde', rango.desde)
    if (rango.hasta) nuevos.set('hasta', rango.hasta)
    setParametros(nuevos)
  }

  if (carga.estado === 'cargando') {
    return (
      <div className="oferta">
        <p className="estado-carga" role="status" aria-busy="true">
          {t('oferta.cargando')}
        </p>
      </div>
    )
  }
  if (carga.estado === 'error') {
    return (
      <div className="oferta">
        <p className="oferta__volver">
          <Link to={`/prestador/servicios/${encodeURIComponent(id)}`}>← {t('oferta.volver')}</Link>
        </p>
        <p className="oferta__aviso oferta__aviso--error" role="alert">
          {t(`oferta.errores.${carga.codigo}`, { reservados: '' })}
        </p>
      </div>
    )
  }

  const { inventario, servicio } = carga
  const esTour = inventario.categoria === 'Tour'
  const unidades = unidadesDe(servicio)
  const nombreUnidad = (idUnidad: string | null) =>
    idUnidad === null ? '' : (unidades.find((u) => u.idUnidad === idUnidad)?.nombre ?? idUnidad)
  const nombreDia = (dia: string) => t(`oferta.dias.${dia}`)

  const guardarCupos = (fila: FilaInventario) => {
    const valor = Number(cupos[claveFila(fila)])
    if (!Number.isInteger(valor) || valor < 1) {
      setAviso({ tipo: 'error', texto: t('oferta.errores.DATOS_INVALIDOS') })
      return
    }
    void ejecutar(
      () => ajustarFila(id, { fecha: fila.fecha, horaInicio: fila.horaInicio, idUnidad: fila.idUnidad, cuposTotales: valor }),
      fila.cerrado ? t('oferta.abierta') : t('oferta.guardado'),
    )
  }

  const agregar = (evento: FormEvent) => {
    evento.preventDefault()
    const valor = Number(nueva.cuposTotales)
    void ejecutar(
      () =>
        ajustarFila(id, {
          fecha: nueva.fecha,
          horaInicio: esTour ? nueva.horaInicio || null : null,
          idUnidad: esTour ? null : nueva.idUnidad || null,
          cuposTotales: valor,
        }),
      t('oferta.agregada'),
    )
  }

  const guardarPlantilla = (evento: FormEvent) => {
    evento.preventDefault()
    if (!plantilla) return
    const dias = DIAS_SEMANA.filter((d) => plantilla.dias.includes(d))
    const salidas = esTour
      ? plantilla.salidas
          .split(',')
          .map((s) => s.trim())
          .filter((s) => s !== '')
      : null
    void ejecutar(() => cambiarPlantilla(id, dias, salidas), t('oferta.plantilla.guardada'))
  }

  return (
    <div className="oferta">
      <p className="oferta__volver">
        <Link to={`/prestador/servicios/${encodeURIComponent(id)}`}>← {t('oferta.volver')}</Link>
      </p>
      <h1>{t('oferta.titulo', { nombre: inventario.nombre })}</h1>
      <p className="oferta__ayuda">{t('oferta.ayuda')}</p>

      {aviso && (
        <p className={`oferta__aviso oferta__aviso--${aviso.tipo}`} role={aviso.tipo === 'error' ? 'alert' : 'status'}>
          {aviso.texto}
        </p>
      )}

      <form className="oferta__filtros" onSubmit={filtrar} aria-label={t('oferta.rango')}>
        <label>
          {t('oferta.desde')}
          <input type="date" value={rango.desde} onChange={(e) => setRango({ ...rango, desde: e.target.value })} />
        </label>
        <label>
          {t('oferta.hasta')}
          <input type="date" value={rango.hasta} onChange={(e) => setRango({ ...rango, hasta: e.target.value })} />
        </label>
        <button type="submit" className="boton-secundario">
          {t('oferta.consultar')}
        </button>
      </form>

      <section className="tarjeta oferta__seccion" aria-labelledby="oferta-inventario">
        <h2 id="oferta-inventario">
          {t('oferta.inventario', {
            desde: formatearFecha(inventario.desde, i18n.language),
            hasta: formatearFecha(inventario.hasta, i18n.language),
          })}
        </h2>
        {inventario.filas.length === 0 ? (
          <p className="oferta__vacio">{t('oferta.sinFilas')}</p>
        ) : (
          <div className="oferta__tabla-contenedor">
            <table className="oferta__tabla">
              <thead>
                <tr>
                  <th scope="col">{t('oferta.columnas.fecha')}</th>
                  <th scope="col">{esTour ? t('oferta.columnas.salida') : t('oferta.columnas.unidad')}</th>
                  <th scope="col">{t('oferta.columnas.cupos')}</th>
                  <th scope="col">{t('oferta.columnas.reservados')}</th>
                  <th scope="col">{t('oferta.columnas.disponibles')}</th>
                  <th scope="col">{t('oferta.columnas.estado')}</th>
                  <th scope="col">{t('oferta.columnas.acciones')}</th>
                </tr>
              </thead>
              <tbody>
                {inventario.filas.map((fila) => {
                  const clave = claveFila(fila)
                  const etiqueta = `${formatearFecha(fila.fecha, i18n.language)} ${esTour ? (fila.horaInicio ?? '') : nombreUnidad(fila.idUnidad)}`
                  return (
                    <tr key={clave} className={fila.cerrado ? 'oferta__fila--cerrada' : undefined}>
                      <td>{formatearFecha(fila.fecha, i18n.language)}</td>
                      <td>{esTour ? fila.horaInicio : nombreUnidad(fila.idUnidad)}</td>
                      <td>
                        <input
                          type="number"
                          min={Math.max(1, fila.reservados)}
                          max={10000}
                          className="oferta__cupos"
                          aria-label={t('oferta.cuposDe', { fila: etiqueta })}
                          value={cupos[clave] ?? String(fila.cuposTotales)}
                          onChange={(e) => setCupos({ ...cupos, [clave]: e.target.value })}
                        />
                      </td>
                      <td>{fila.reservados}</td>
                      <td>{fila.cerrado ? 0 : fila.cuposDisponibles}</td>
                      <td>{fila.cerrado ? t('oferta.estado.cerrada') : t('oferta.estado.abierta')}</td>
                      <td className="oferta__acciones-fila">
                        <button
                          type="button"
                          className="boton-secundario"
                          disabled={ocupado}
                          onClick={() => guardarCupos(fila)}
                        >
                          {fila.cerrado ? t('oferta.abrir') : t('oferta.guardar')}
                        </button>
                        {!fila.cerrado && (
                          <button
                            type="button"
                            className="boton-secundario"
                            disabled={ocupado || fila.reservados > 0}
                            title={fila.reservados > 0 ? t('oferta.errores.FECHA_CON_RESERVAS') : undefined}
                            onClick={() => void ejecutar(() => cerrarFila(id, fila), t('oferta.cerrada'))}
                          >
                            {t('oferta.cerrar')}
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="tarjeta oferta__seccion" aria-labelledby="oferta-agregar">
        <h2 id="oferta-agregar">{esTour ? t('oferta.agregarSalida') : t('oferta.agregarFecha')}</h2>
        <form className="oferta__formulario" onSubmit={agregar}>
          <label>
            {t('oferta.columnas.fecha')}
            <input
              type="date"
              required
              value={nueva.fecha}
              onChange={(e) => setNueva({ ...nueva, fecha: e.target.value })}
            />
          </label>
          {esTour ? (
            <label>
              {t('oferta.columnas.salida')}
              <input
                type="time"
                required
                value={nueva.horaInicio}
                onChange={(e) => setNueva({ ...nueva, horaInicio: e.target.value })}
              />
            </label>
          ) : (
            <label>
              {t('oferta.columnas.unidad')}
              <select required value={nueva.idUnidad} onChange={(e) => setNueva({ ...nueva, idUnidad: e.target.value })}>
                <option value="">{t('oferta.elegirUnidad')}</option>
                {unidades.map((u) => (
                  <option key={u.idUnidad} value={u.idUnidad}>
                    {u.nombre}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label>
            {t('oferta.columnas.cupos')}
            <input
              type="number"
              min={1}
              max={10000}
              required
              value={nueva.cuposTotales}
              onChange={(e) => setNueva({ ...nueva, cuposTotales: e.target.value })}
            />
          </label>
          <button type="submit" className="boton-principal" disabled={ocupado}>
            {t('oferta.agregar')}
          </button>
        </form>
      </section>

      {plantilla && (
        <section className="tarjeta oferta__seccion" aria-labelledby="oferta-plantilla">
          <h2 id="oferta-plantilla">{t('oferta.plantilla.titulo')}</h2>
          <p className="oferta__ayuda">{t('oferta.plantilla.ayuda')}</p>
          <form className="oferta__formulario" onSubmit={guardarPlantilla}>
            <fieldset className="oferta__dias">
              <legend>{t('oferta.plantilla.dias')}</legend>
              {DIAS_SEMANA.map((dia) => (
                <label key={dia} className="oferta__dia">
                  <input
                    type="checkbox"
                    checked={plantilla.dias.includes(dia)}
                    onChange={(e) =>
                      setPlantilla({
                        ...plantilla,
                        dias: e.target.checked ? [...plantilla.dias, dia] : plantilla.dias.filter((d) => d !== dia),
                      })
                    }
                  />
                  {nombreDia(dia)}
                </label>
              ))}
            </fieldset>
            {esTour && (
              <label>
                {t('oferta.plantilla.salidas')}
                <input
                  type="text"
                  value={plantilla.salidas}
                  placeholder="08:00, 14:00"
                  onChange={(e) => setPlantilla({ ...plantilla, salidas: e.target.value })}
                />
              </label>
            )}
            <button type="submit" className="boton-principal" disabled={ocupado || plantilla.dias.length === 0}>
              {t('oferta.plantilla.guardar')}
            </button>
          </form>
        </section>
      )}

      <p className="oferta__enlaces">
        <Link to={`/prestador/asistentes?servicio=${encodeURIComponent(id)}`}>{t('oferta.verAsistentes')}</Link>
        {' · '}
        <Link to={`/prestador/historial-modificaciones?servicio=${encodeURIComponent(id)}`}>
          {t('oferta.verHistorial')}
        </Link>
      </p>
    </div>
  )
}
