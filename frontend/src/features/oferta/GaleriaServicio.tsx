import { useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../shared/api/httpClient'
import { agregarImagen, quitarImagen } from './ofertaApi'
import './oferta.css'

const MAXIMO = 5
const TAMANO_MAXIMO = 5 * 1024 * 1024
const TIPOS = new Set(['image/jpeg', 'image/png'])

function codigoDe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.codigo === 'LIMITE_IMAGENES' || error.codigo === 'IMAGEN_MINIMA') return error.codigo
    if (error.status === 400) return 'IMAGEN_INVALIDA'
    if (error.status === 413) return 'IMAGEN_INVALIDA'
    if (error.status === 409) return 'ESTADO_CAMBIADO'
  }
  return 'OTRO'
}

/**
 * Galería de un servicio aprobado en WF-18 (RF48; DEC-313): de 1 a 5 imágenes JPEG o PNG de hasta 5 MB, una por
 * subida; quitar deja al menos una. El servidor decide los límites (409 LIMITE_IMAGENES, IMAGEN_MINIMA) y la pantalla
 * solo adelanta los controles evidentes. Los cambios se ven en la ficha pública al instante, sin nueva aprobación.
 */
export function GaleriaServicio({ idServicio, imagenesIniciales }: { idServicio: string; imagenesIniciales: string[] }) {
  const { t } = useTranslation()
  const [imagenes, setImagenes] = useState(imagenesIniciales)
  const [ocupado, setOcupado] = useState(false)
  const [aviso, setAviso] = useState<{ tipo: 'exito' | 'error'; texto: string } | null>(null)
  const entrada = useRef<HTMLInputElement>(null)

  const ejecutar = async (accion: () => Promise<{ imagenes: string[] }>, exito: string) => {
    setOcupado(true)
    setAviso(null)
    try {
      const respuesta = await accion()
      setImagenes(respuesta.imagenes)
      setAviso({ tipo: 'exito', texto: exito })
    } catch (error) {
      setAviso({ tipo: 'error', texto: t(`galeria.errores.${codigoDe(error)}`) })
    } finally {
      setOcupado(false)
    }
  }

  const elegir = (evento: ChangeEvent<HTMLInputElement>) => {
    const archivo = evento.target.files?.[0]
    evento.target.value = ''
    if (!archivo) return
    if (!TIPOS.has(archivo.type) || archivo.size > TAMANO_MAXIMO) {
      setAviso({ tipo: 'error', texto: t('galeria.errores.IMAGEN_INVALIDA') })
      return
    }
    void ejecutar(() => agregarImagen(idServicio, archivo), t('galeria.agregada'))
  }

  return (
    <section className="galeria-servicio" aria-labelledby="galeria-servicio">
      <h2 id="galeria-servicio">{t('galeria.titulo')}</h2>
      <p className="oferta__ayuda">{t('galeria.ayuda', { n: imagenes.length, max: MAXIMO })}</p>
      {aviso && (
        <p className={`oferta__aviso oferta__aviso--${aviso.tipo}`} role={aviso.tipo === 'error' ? 'alert' : 'status'}>
          {aviso.texto}
        </p>
      )}
      {imagenes.length === 0 ? (
        <p className="oferta__vacio">{t('galeria.vacia')}</p>
      ) : (
        <ul className="galeria-servicio__lista">
          {imagenes.map((ruta, i) => (
            <li key={ruta}>
              <img src={ruta} alt={t('galeria.imagen', { n: i + 1 })} loading="lazy" />
              <button
                type="button"
                className="boton-secundario"
                disabled={ocupado || imagenes.length <= 1}
                aria-label={t('galeria.quitarImagen', { n: i + 1 })}
                onClick={() => void ejecutar(() => quitarImagen(idServicio, ruta), t('galeria.quitada'))}
              >
                {t('galeria.quitar')}
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        ref={entrada}
        type="file"
        accept="image/jpeg,image/png"
        className="solo-lector"
        aria-label={t('galeria.elegir')}
        onChange={elegir}
        tabIndex={-1}
      />
      <button
        type="button"
        className="boton-principal"
        disabled={ocupado || imagenes.length >= MAXIMO}
        onClick={() => entrada.current?.click()}
      >
        {t('galeria.agregar')}
      </button>
    </section>
  )
}
