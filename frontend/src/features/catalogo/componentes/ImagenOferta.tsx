import { useState } from 'react'
import type { ResultadoOferta } from '../catalogoApi'
import { imagenDeCategoria } from './etiquetas'

/** Proporción de todas las fotos del catálogo (frontend/scripts/optimizar-imagenes.mjs garantiza 3:2). */
const ANCHO = 600
const ALTO = 400

/**
 * Imagen de la tarjeta: la foto del lugar (/imagenes/catalogo/<slug>.webp) o, si no carga, la ilustración propia de su
 * categoría. El marco fija el tamaño (3:2 en destacados y en la lista apilada; toda la altura de la tarjeta en la lista
 * de escritorio) y la imagen lo llena con object-fit: cover. width y height se mantienen para la carga diferida.
 * Es decorativa (alt vacío): el nombre del lugar ya está en el título de la tarjeta o del detalle, y no es un enlace.
 */
export function ImagenOferta({
  resultado,
  prioritaria = false,
}: {
  resultado: Pick<ResultadoOferta, 'imagen' | 'categoria' | 'tipo'>
  prioritaria?: boolean
}) {
  const respaldo = imagenDeCategoria(resultado.categoria, resultado.tipo)
  const [origen, setOrigen] = useState(resultado.imagen ?? respaldo)

  return (
    <div className="imagen-oferta">
      <img
        className="imagen-oferta__foto"
        src={origen}
        alt=""
        loading={prioritaria ? 'eager' : 'lazy'}
        fetchPriority={prioritaria ? 'high' : undefined}
        decoding="async"
        width={ANCHO}
        height={ALTO}
        onError={() => {
          if (origen !== respaldo) setOrigen(respaldo)
        }}
      />
    </div>
  )
}
