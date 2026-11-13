import type { ReactNode } from 'react'

export interface ColumnaCuentas {
  clave: string
  titulo: string
}

export interface FilaCuentas {
  id: string
  celdas: Record<string, ReactNode>
}

/**
 * Tabla de WF-12. Por debajo de 40 rem cada fila pasa a tarjeta (admin.css) y cada celda muestra su columna con
 * `data-etiqueta`; los roles ARIA explícitos conservan la semántica de tabla aunque cambie el `display` (DEC-53).
 */
export function TablaCuentas({
  titulo,
  columnas,
  filas,
}: {
  titulo: string
  columnas: ColumnaCuentas[]
  filas: FilaCuentas[]
}) {
  return (
    <table className="tabla-cuentas" role="table">
      <caption className="solo-lector">{titulo}</caption>
      <thead role="rowgroup">
        <tr role="row">
          {columnas.map((columna) => (
            <th key={columna.clave} role="columnheader" scope="col">
              {columna.titulo}
            </th>
          ))}
        </tr>
      </thead>
      <tbody role="rowgroup">
        {filas.map((fila) => (
          <tr key={fila.id} role="row">
            {columnas.map((columna) => (
              <td key={columna.clave} role="cell" data-etiqueta={columna.titulo} data-columna={columna.clave}>
                {fila.celdas[columna.clave]}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
