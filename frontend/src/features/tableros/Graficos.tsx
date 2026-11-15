import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import './tableros.css'

export interface Dato {
  clave: string
  etiqueta: string
  valor: number
  /** Texto del valor (p. ej. «45 %»); por defecto, el número con el formato del idioma. */
  texto?: string
}

/**
 * Columnas de una sola serie (DEC-308; guía de visualización: un tono, marcas delgadas con el extremo redondeado sobre
 * la línea base, grilla tenue, sin leyenda porque el título la nombra). Cada columna muestra su valor al pasar el
 * puntero o al enfocarla, y la tabla con los mismos datos queda disponible para lectores de pantalla y teclado.
 */
export function Columnas({ titulo, datos, unidad }: { titulo: string; datos: Dato[]; unidad: string }) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const [activo, setActivo] = useState<string | null>(null)
  const maximo = Math.max(1, ...datos.map((d) => d.valor))
  const formato = (d: Dato) => d.texto ?? d.valor.toLocaleString(i18n.language)
  const seleccionado = datos.find((d) => d.clave === activo)
  return (
    <figure className="grafico" aria-labelledby={`${id}-titulo`}>
      <figcaption id={`${id}-titulo`} className="grafico__titulo">
        {titulo}
      </figcaption>
      <p className="grafico__tooltip" aria-live="polite">
        {seleccionado ? `${seleccionado.etiqueta}: ${formato(seleccionado)} ${unidad}` : ' '}
      </p>
      <div className="grafico__columnas" aria-hidden="true">
        {datos.map((d) => (
          <span
            key={d.clave}
            className={`grafico__columna${activo === d.clave ? ' grafico__columna--activa' : ''}`}
            onMouseEnter={() => setActivo(d.clave)}
            onMouseLeave={() => setActivo(null)}
          >
            <span className="grafico__barra" style={{ height: `${(d.valor / maximo) * 100}%` }} />
          </span>
        ))}
      </div>
      <div className="grafico__eje" aria-hidden="true">
        <span>{datos[0]?.etiqueta}</span>
        <span>{datos[datos.length - 1]?.etiqueta}</span>
      </div>
      <details className="grafico__tabla">
        <summary>{t('tableros.verTabla')}</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">{t('tableros.dia')}</th>
              <th scope="col">{unidad}</th>
            </tr>
          </thead>
          <tbody>
            {datos.map((d) => (
              <tr key={d.clave} tabIndex={0} onFocus={() => setActivo(d.clave)} onBlur={() => setActivo(null)}>
                <th scope="row">{d.etiqueta}</th>
                <td>{formato(d)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}

/**
 * Barras horizontales de una sola serie con la etiqueta y el valor como texto (identidad nunca solo por color). Es una
 * lista: se lee igual con lector de pantalla.
 */
export function Barras({ titulo, datos, vacio }: { titulo: string; datos: Dato[]; vacio: string }) {
  const { i18n } = useTranslation()
  const id = useId()
  const maximo = Math.max(1, ...datos.map((d) => d.valor))
  return (
    <figure className="grafico" aria-labelledby={`${id}-titulo`}>
      <figcaption id={`${id}-titulo`} className="grafico__titulo">
        {titulo}
      </figcaption>
      {datos.length === 0 ? (
        <p className="grafico__vacio">{vacio}</p>
      ) : (
        <ol className="grafico__barras">
          {datos.map((d) => (
            <li key={d.clave} title={`${d.etiqueta}: ${d.texto ?? d.valor.toLocaleString(i18n.language)}`}>
              <span className="grafico__etiqueta">{d.etiqueta}</span>
              <span className="grafico__pista" aria-hidden="true">
                <span className="grafico__barra-h" style={{ width: `${(d.valor / maximo) * 100}%` }} />
              </span>
              <span className="grafico__valor">{d.texto ?? d.valor.toLocaleString(i18n.language)}</span>
            </li>
          ))}
        </ol>
      )}
    </figure>
  )
}

/** Cifra destacada (un número, no un gráfico). */
export function Cifra({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <p className="cifra">
      <span className="cifra__valor">{valor}</span>
      <span className="cifra__etiqueta">{etiqueta}</span>
    </p>
  )
}
