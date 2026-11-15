import { useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { idDeCampo, type ErroresServicio, type MotivoServicio } from '../../prestador/rutasCampos'
import { textoErrorAtractivo } from '../borradorAtractivo'
import { MAXIMO_IMAGENES, motivoImagen } from '../imagenesAtractivo'

/** Una imagen elegida en el alta: el archivo, su vista previa (URL de objeto) y su motivo si no sirve. */
export interface ImagenElegida {
  clave: string
  archivo: File
  vistaPrevia: string
  motivo: MotivoServicio | null
}

let siguienteClave = 0

/** Lee el motivo de cada archivo nuevo (firma y tamaño) y crea su vista previa. */
export async function elegirImagenes(archivos: File[]): Promise<ImagenElegida[]> {
  return Promise.all(
    archivos.map(async (archivo) => ({
      clave: `imagen-${(siguienteClave += 1)}`,
      archivo,
      vistaPrevia: URL.createObjectURL(archivo),
      motivo: await motivoImagen(archivo),
    })),
  )
}

/**
 * «Fotos del atractivo» de WF-09 en el alta (RF06; DEC-209, DEC-226): de 1 a 5 imágenes JPEG o PNG con vista previa.
 * El control no deja pasar de 5 (los archivos de más no se agregan y se avisa). Cada error va junto a su miniatura
 * (`imagenes[i]`, del cliente o del 400 del servidor) y el de la cantidad, en el grupo (`imagenes`). «Quitar» libera la
 * vista previa y lleva el foco al «Quitar» anterior o, si no hay, al botón de agregar.
 */
export function SelectorImagenes({
  imagenes,
  errores,
  excedidas,
  onAgregar,
  onQuitar,
  titulo,
}: {
  imagenes: ImagenElegida[]
  errores: ErroresServicio
  /** Cuántos archivos de la última selección no se agregaron por el tope de 5. */
  excedidas: number
  onAgregar: (archivos: File[]) => void
  onQuitar: (indice: number) => void
  /** Título de la sección: «Fotos del atractivo» por defecto; WF-08 usa «Fotos del servicio» (DEC-318). */
  titulo?: string
}) {
  const { t } = useTranslation()
  const id = useId()
  const entrada = useRef<HTMLInputElement>(null)
  const botones = useRef(new Map<number, HTMLButtonElement>())
  const lleno = imagenes.length >= MAXIMO_IMAGENES
  const idGrupo = idDeCampo('imagenes')
  const errorGrupo = textoErrorAtractivo(t, 'imagenes', errores.imagenes)

  const quitar = (indice: number) => {
    onQuitar(indice)
    setTimeout(() => {
      const anterior = botones.current.get(indice - 1)
      if (anterior) anterior.focus()
      else entrada.current?.focus()
    }, 0)
  }

  return (
    <section className="tarjeta prestador__seccion" aria-labelledby={`${id}-titulo`}>
      <h2 id={`${id}-titulo`}>{titulo ?? t('gestor.imagenes.titulo')}</h2>
      <div
        id={idGrupo}
        tabIndex={-1}
        role="group"
        aria-labelledby={`${id}-titulo`}
        aria-describedby={[`${id}-ayuda`, errorGrupo ? `${idGrupo}-error` : null].filter(Boolean).join(' ')}
        className="gestor-imagenes"
      >
        <p id={`${id}-ayuda`} className="campo__ayuda">
          {t('gestor.imagenes.ayuda')}
        </p>
        <label className={`boton-secundario gestor-imagenes__agregar${lleno ? ' boton-no-disponible' : ''}`}>
          {t('gestor.imagenes.agregar')}
          <input
            ref={entrada}
            className="solo-lector"
            type="file"
            accept="image/jpeg,image/png"
            multiple
            disabled={lleno}
            aria-describedby={`${id}-contador`}
            onChange={(evento) => {
              const archivos = Array.from(evento.target.files ?? [])
              evento.target.value = ''
              if (archivos.length > 0) onAgregar(archivos)
            }}
          />
        </label>
        <p id={`${id}-contador`} className="prestador__contador" aria-live="polite">
          {t('gestor.imagenes.contador', { n: imagenes.length, maximo: MAXIMO_IMAGENES })}
          {excedidas > 0 && ` ${t('gestor.imagenes.excedidas', { count: excedidas })}`}
        </p>
        {errorGrupo && (
          <p id={`${idGrupo}-error`} className="campo__error">
            {errorGrupo}
          </p>
        )}
        {imagenes.length > 0 && (
          <ol className="gestor-imagenes__lista">
            {imagenes.map((imagen, indice) => {
              const ruta = `imagenes[${indice}]`
              const error = textoErrorAtractivo(t, ruta, errores[ruta])
              const idImagen = idDeCampo(ruta)
              return (
                <li key={imagen.clave} className={`gestor-imagenes__item${error ? ' gestor-imagenes__item--error' : ''}`}>
                  <img
                    src={imagen.vistaPrevia}
                    alt={t('gestor.imagenes.vistaPrevia', { numero: indice + 1, nombre: imagen.archivo.name })}
                    width={120}
                    height={80}
                  />
                  <span className="gestor-imagenes__nombre">{imagen.archivo.name}</span>
                  {error && (
                    <p id={`${idImagen}-error`} className="campo__error">
                      {error}
                    </p>
                  )}
                  <button
                    id={idImagen}
                    ref={(elemento) => {
                      if (elemento) botones.current.set(indice, elemento)
                      else botones.current.delete(indice)
                    }}
                    type="button"
                    className="boton-secundario"
                    aria-describedby={error ? `${idImagen}-error` : undefined}
                    onClick={() => quitar(indice)}
                  >
                    {t('gestor.imagenes.quitar', { numero: indice + 1 })}
                  </button>
                </li>
              )
            })}
          </ol>
        )}
      </div>
    </section>
  )
}
