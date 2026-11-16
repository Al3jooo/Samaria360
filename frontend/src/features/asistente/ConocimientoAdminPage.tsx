import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../shared/api/httpClient'
import { formatearDia } from '../../shared/formato'
import {
  CATEGORIA_CONOCIMIENTO_MAXIMA,
  CONOCIMIENTO_MAXIMO,
  actualizarConocimiento,
  crearConocimiento,
  eliminarConocimiento,
  listarConocimiento,
  type EntradaConocimiento,
  type PaginaConocimiento,
} from './asistenteApi'
import './asistente.css'

type Estado = 'cargando' | 'ok' | 'error'

/**
 * WF-32 «Base de conocimientos del asistente» (RF05; DEC-297), solo para el Administrador: agregar, editar y eliminar
 * las entradas (contenido de 1 a 2000 y categoría de 1 a 50) que el asistente usa para responder, de 10 en 10.
 */
export function ConocimientoAdminPage() {
  const { t, i18n } = useTranslation()
  const [estado, setEstado] = useState<Estado>('cargando')
  const [pagina, setPagina] = useState(0)
  const [datos, setDatos] = useState<PaginaConocimiento | null>(null)
  const [editando, setEditando] = useState<EntradaConocimiento | null>(null)
  const [contenido, setContenido] = useState('')
  const [categoria, setCategoria] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [mensaje, setMensaje] = useState<{ tipo: 'exito' | 'error'; texto: string } | null>(null)

  const cargar = useCallback((numero: number) => {
    setEstado('cargando')
    listarConocimiento(numero)
      .then((resultado) => {
        setDatos(resultado)
        setEstado('ok')
      })
      .catch(() => setEstado('error'))
  }, [])

  useEffect(() => {
    cargar(pagina)
  }, [cargar, pagina])

  const limpiar = () => {
    setEditando(null)
    setContenido('')
    setCategoria('')
  }

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    setOcupado(true)
    setMensaje(null)
    try {
      if (editando) await actualizarConocimiento(editando.id, contenido.trim(), categoria.trim())
      else await crearConocimiento(contenido.trim(), categoria.trim())
      setMensaje({ tipo: 'exito', texto: t(editando ? 'conocimiento.actualizada' : 'conocimiento.creada') })
      limpiar()
      cargar(pagina)
    } catch (causa) {
      const codigo = causa instanceof ApiError ? causa.codigo : ''
      setMensaje({
        tipo: 'error',
        texto:
          codigo === 'LIMITE_CONOCIMIENTO'
            ? t('conocimiento.errorLimite')
            : codigo === 'DATOS_INVALIDOS'
              ? t('conocimiento.errorDatos')
              : t('conocimiento.errorGuardar'),
      })
    } finally {
      setOcupado(false)
    }
  }

  const eliminar = async (entrada: EntradaConocimiento) => {
    if (!window.confirm(t('conocimiento.confirmarEliminar'))) return
    setMensaje(null)
    try {
      await eliminarConocimiento(entrada.id)
      setMensaje({ tipo: 'exito', texto: t('conocimiento.eliminada') })
      if (editando?.id === entrada.id) limpiar()
      cargar(pagina)
    } catch {
      setMensaje({ tipo: 'error', texto: t('conocimiento.errorEliminar') })
    }
  }

  const editar = (entrada: EntradaConocimiento) => {
    setEditando(entrada)
    setContenido(entrada.contenido)
    setCategoria(entrada.categoria)
    setMensaje(null)
  }

  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.totalElementos / datos.tamano)) : 1

  return (
    <div className="conocimiento-admin">
      <h1>{t('conocimiento.titulo')}</h1>
      <p className="conocimiento-admin__contador">{t('conocimiento.descripcion')}</p>

      <form className="formulario-asistente tarjeta-conocimiento" onSubmit={(e) => void enviar(e)}>
        <h2>{t(editando ? 'conocimiento.editarTitulo' : 'conocimiento.nuevaTitulo')}</h2>
        <label>
          {t('conocimiento.categoria')}
          <input
            value={categoria}
            maxLength={CATEGORIA_CONOCIMIENTO_MAXIMA}
            required
            onChange={(e) => setCategoria(e.target.value)}
          />
        </label>
        <label>
          {t('conocimiento.contenido')}
          <textarea
            value={contenido}
            maxLength={CONOCIMIENTO_MAXIMO}
            required
            onChange={(e) => setContenido(e.target.value)}
          />
          <span className="conocimiento-admin__contador">
            {t('conocimiento.caracteres', { n: [...contenido].length, max: CONOCIMIENTO_MAXIMO })}
          </span>
        </label>
        <div className="tarjeta-conocimiento__acciones">
          <button
            type="submit"
            className="boton-principal"
            disabled={ocupado || contenido.trim() === '' || categoria.trim() === ''}
          >
            {t(editando ? 'conocimiento.guardar' : 'conocimiento.agregar')}
          </button>
          {editando && (
            <button type="button" className="boton-secundario" onClick={limpiar}>
              {t('conocimiento.cancelar')}
            </button>
          )}
        </div>
      </form>

      {mensaje && (
        <p className={mensaje.tipo === 'exito' ? 'asistente__exito' : 'asistente__error'} role={mensaje.tipo === 'exito' ? 'status' : 'alert'}>
          {mensaje.texto}
        </p>
      )}

      {estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('conocimiento.cargando')}
        </p>
      )}
      {estado === 'error' && (
        <div className="estado-catalogo estado-catalogo--error" role="alert">
          <p className="estado-catalogo__mensaje">{t('conocimiento.errorCarga')}</p>
          <button type="button" className="boton-secundario" onClick={() => cargar(pagina)}>
            {t('catalogo.busqueda.reintentar')}
          </button>
        </div>
      )}
      {estado === 'ok' && datos && (
        <>
          <p className="conocimiento-admin__contador">{t('conocimiento.total', { count: datos.totalElementos })}</p>
          {datos.elementos.length === 0 ? (
            <p className="estado-catalogo__mensaje">{t('conocimiento.vacia')}</p>
          ) : (
            <ul className="lista-conocimiento">
              {datos.elementos.map((entrada) => (
                <li key={entrada.id} className="tarjeta-conocimiento">
                  <header>
                    <h2>{entrada.categoria}</h2>
                    <p className="tarjeta-conocimiento__fecha">
                      {formatearDia(entrada.fechaActualizacion, i18n.language)}
                    </p>
                  </header>
                  <p>{entrada.contenido}</p>
                  <div className="tarjeta-conocimiento__acciones">
                    <button type="button" className="boton-secundario" onClick={() => editar(entrada)}>
                      {t('conocimiento.editar')}
                    </button>
                    <button
                      type="button"
                      className="boton-texto boton-texto--peligro"
                      onClick={() => void eliminar(entrada)}
                    >
                      {t('conocimiento.eliminar')}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {totalPaginas > 1 && (
            <nav className="tarjeta-conocimiento__acciones" aria-label={t('conocimiento.paginacion')}>
              <button
                type="button"
                className="boton-secundario"
                disabled={pagina === 0}
                onClick={() => setPagina((p) => p - 1)}
              >
                {t('conocimiento.anterior')}
              </button>
              <span>{t('conocimiento.pagina', { n: pagina + 1, total: totalPaginas })}</span>
              <button
                type="button"
                className="boton-secundario"
                disabled={pagina + 1 >= totalPaginas}
                onClick={() => setPagina((p) => p + 1)}
              >
                {t('conocimiento.siguiente')}
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
