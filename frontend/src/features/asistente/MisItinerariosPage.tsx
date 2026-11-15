import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiError } from '../../shared/api/httpClient'
import {
  ITINERARIOS_MAXIMOS,
  NOMBRE_ITINERARIO_MAXIMO,
  actualizarItinerario,
  eliminarItinerario,
  listarItinerarios,
  porDia,
  rutaPaso,
  type ItemItinerario,
  type Itinerario,
} from './asistenteApi'
import './asistente.css'

const DIAS = [1, 2, 3, 4, 5, 6, 7]

type Estado = 'cargando' | 'ok' | 'error'

/**
 * WF-31 «Mis itinerarios» (RF35; DEC-298), dentro del perfil y solo para el Turista: los itinerarios guardados desde el
 * asistente, del más reciente al más antiguo, con editar (nombre, día de cada paso y quitar pasos) y eliminar con
 * confirmación. Los pasos enlazan a su ficha pública.
 */
export function MisItinerariosPage() {
  const { t } = useTranslation()
  const [estado, setEstado] = useState<Estado>('cargando')
  const [itinerarios, setItinerarios] = useState<Itinerario[]>([])

  const cargar = useCallback(() => {
    setEstado('cargando')
    listarItinerarios()
      .then((lista) => {
        setItinerarios(lista)
        setEstado('ok')
      })
      .catch(() => setEstado('error'))
  }, [])

  useEffect(() => {
    cargar()
  }, [cargar])

  const reemplazar = (nuevo: Itinerario) =>
    setItinerarios((lista) => lista.map((i) => (i.id === nuevo.id ? nuevo : i)))
  const quitar = (id: string) => setItinerarios((lista) => lista.filter((i) => i.id !== id))

  return (
    <div className="mis-itinerarios">
      <p className="perfil__volver">
        <Link to="/perfil">
          <span aria-hidden="true">← </span>
          {t('itinerarios.volverPerfil')}
        </Link>
      </p>
      <h1>{t('itinerarios.titulo')}</h1>
      {estado === 'ok' && itinerarios.length > 0 && (
        <p className="mis-itinerarios__contador">
          {t('itinerarios.contador', { n: itinerarios.length, limite: ITINERARIOS_MAXIMOS })}
        </p>
      )}
      {estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('itinerarios.cargando')}
        </p>
      )}
      {estado === 'error' && (
        <div className="estado-catalogo estado-catalogo--error" role="alert">
          <p className="estado-catalogo__mensaje">{t('itinerarios.errorCarga')}</p>
          <button type="button" className="boton-secundario" onClick={cargar}>
            {t('catalogo.busqueda.reintentar')}
          </button>
        </div>
      )}
      {estado === 'ok' &&
        (itinerarios.length === 0 ? (
          <div className="estado-catalogo">
            <p className="estado-catalogo__mensaje">{t('itinerarios.vacio')}</p>
            <Link to="/asistente" className="boton-principal">
              {t('itinerarios.irAsistente')}
            </Link>
          </div>
        ) : (
          <ul className="lista-itinerarios">
            {itinerarios.map((itinerario) => (
              <TarjetaItinerario
                key={itinerario.id}
                itinerario={itinerario}
                alGuardar={reemplazar}
                alEliminar={quitar}
              />
            ))}
          </ul>
        ))}
    </div>
  )
}

function TarjetaItinerario({
  itinerario,
  alGuardar,
  alEliminar,
}: {
  itinerario: Itinerario
  alGuardar: (i: Itinerario) => void
  alEliminar: (id: string) => void
}) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const [editando, setEditando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)
  const [nombre, setNombre] = useState(itinerario.nombre)
  const [pasos, setPasos] = useState<ItemItinerario[]>(itinerario.items)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const empezar = () => {
    setNombre(itinerario.nombre)
    setPasos(itinerario.items)
    setError(null)
    setEditando(true)
  }

  const guardar = async (evento: FormEvent) => {
    evento.preventDefault()
    setOcupado(true)
    setError(null)
    try {
      alGuardar(await actualizarItinerario(itinerario.id, { nombre: nombre.trim(), items: pasos }))
      setEditando(false)
    } catch (causa) {
      setError(
        causa instanceof ApiError && causa.codigo === 'DATOS_INVALIDOS'
          ? t('itinerarios.errorDatos')
          : t('itinerarios.errorGuardar'),
      )
    } finally {
      setOcupado(false)
    }
  }

  const eliminar = async () => {
    setOcupado(true)
    try {
      await eliminarItinerario(itinerario.id)
      alEliminar(itinerario.id)
    } catch (causa) {
      if (causa instanceof ApiError && causa.status === 404) alEliminar(itinerario.id)
      else setError(t('itinerarios.errorEliminar'))
      setOcupado(false)
    }
  }

  const fecha = new Date(itinerario.fechaActualizacion).toLocaleDateString(i18n.language)

  return (
    <li className="tarjeta-itinerario">
      {editando ? (
        <form className="formulario-asistente" onSubmit={(e) => void guardar(e)}>
          <label>
            {t('itinerarios.nombre')}
            <input
              value={nombre}
              maxLength={NOMBRE_ITINERARIO_MAXIMO}
              required
              onChange={(e) => setNombre(e.target.value)}
            />
          </label>
          {pasos.map((paso, indice) => (
            <div key={`${paso.tipo}-${paso.refId}`} className="formulario-asistente__paso">
              <span>{paso.tituloActividad}</span>
              <label>
                {t('itinerarios.dia')}
                <select
                  value={paso.diaSugerido}
                  onChange={(e) =>
                    setPasos((lista) =>
                      lista.map((p, i) => (i === indice ? { ...p, diaSugerido: Number(e.target.value) } : p)),
                    )
                  }
                >
                  {DIAS.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="boton-texto boton-texto--peligro"
                disabled={pasos.length === 1}
                onClick={() => setPasos((lista) => lista.filter((_, i) => i !== indice))}
              >
                {t('itinerarios.quitarPaso')}
              </button>
            </div>
          ))}
          {error && (
            <p className="campo-error" role="alert">
              {error}
            </p>
          )}
          <div className="tarjeta-itinerario__acciones">
            <button type="submit" className="boton-principal" disabled={ocupado || nombre.trim() === ''}>
              {t('itinerarios.guardar')}
            </button>
            <button type="button" className="boton-secundario" onClick={() => setEditando(false)}>
              {t('itinerarios.cancelar')}
            </button>
          </div>
        </form>
      ) : (
        <>
          <header>
            <h2 id={`${id}-nombre`}>{itinerario.nombre}</h2>
            <p className="tarjeta-itinerario__fecha">{t('itinerarios.actualizado', { fecha })}</p>
          </header>
          <ol className="itinerario-sugerido__dias itinerario-sugerido" aria-labelledby={`${id}-nombre`}>
            {porDia(itinerario.items).map(({ dia, items }) => (
              <li key={dia}>
                <h3>{t('asistente.itinerario.dia', { n: dia })}</h3>
                <ul>
                  {items.map((paso) => (
                    <li key={`${paso.tipo}-${paso.refId}`}>
                      <Link to={rutaPaso(paso.tipo, paso.refId)}>{paso.tituloActividad}</Link>
                      <span>{t(`asistente.tipo.${paso.tipo}`)}</span>
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ol>
          {error && (
            <p className="campo-error" role="alert">
              {error}
            </p>
          )}
          <div className="tarjeta-itinerario__acciones">
            {confirmando ? (
              <>
                <span role="status">{t('itinerarios.confirmarEliminar', { nombre: itinerario.nombre })}</span>
                <button type="button" className="boton-principal" disabled={ocupado} onClick={() => void eliminar()}>
                  {t('itinerarios.siEliminar')}
                </button>
                <button type="button" className="boton-secundario" onClick={() => setConfirmando(false)}>
                  {t('itinerarios.cancelar')}
                </button>
              </>
            ) : (
              <>
                <button type="button" className="boton-secundario" onClick={empezar}>
                  {t('itinerarios.editar')}
                </button>
                <button type="button" className="boton-texto boton-texto--peligro" onClick={() => setConfirmando(true)}>
                  {t('itinerarios.eliminar')}
                </button>
              </>
            )}
          </div>
        </>
      )}
    </li>
  )
}
