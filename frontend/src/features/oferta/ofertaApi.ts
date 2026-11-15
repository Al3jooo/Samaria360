import { solicitar } from '../../shared/api/httpClient'
import { horaCorta } from '../reservas/fechasBogota'

/**
 * Iteración I del Prestador (DEC-310 a DEC-313; docs/sad/api/ms-booking.md y ms-catalog.md):
 * - RF19, inventario y plantilla de su servicio (ms-booking);
 * - RF42, asistentes de una fecha (ms-booking, con el contacto de ms-auth);
 * - RF41, historial de modificaciones de los últimos 30 días (ms-catalog);
 * - RF48, galería de un servicio aprobado (ms-catalog).
 */

export interface FilaInventario {
  categoria: 'Tour' | 'Alojamiento'
  idUnidad: string | null
  fecha: string
  horaInicio: string | null
  cuposTotales: number
  cuposDisponibles: number
  reservados: number
  cerrado: boolean
}

export interface Inventario {
  idServicio: string
  nombre: string
  categoria: 'Tour' | 'Alojamiento'
  desde: string
  hasta: string
  diasOperacion: string[]
  salidas: string[]
  filas: FilaInventario[]
}

export interface CambioFila {
  fecha: string
  horaInicio?: string | null
  idUnidad?: string | null
  cuposTotales: number
}

/** ms-booking serializa `LocalTime` como «08:00:00»; la pantalla muestra y envía «08:00» (lo que pide la API). */
function hora(valor: string | null): string | null {
  return valor === null ? null : horaCorta(valor)
}

function normalizarFila(fila: FilaInventario): FilaInventario {
  return { ...fila, horaInicio: hora(fila.horaInicio) }
}

const BASE_RESERVAS = '/api/v1/reservas/prestador'
const BASE_CATALOGO = '/api/v1/catalogo/prestador'

export async function consultarInventario(id: string, desde: string, hasta: string): Promise<Inventario> {
  const consulta = new URLSearchParams()
  if (desde) consulta.set('desde', desde)
  if (hasta) consulta.set('hasta', hasta)
  const sufijo = consulta.toString() === '' ? '' : `?${consulta}`
  const inventario = await solicitar<Inventario>(`${BASE_RESERVAS}/servicios/${encodeURIComponent(id)}/inventario${sufijo}`)
  return { ...inventario, salidas: inventario.salidas.map(horaCorta), filas: inventario.filas.map(normalizarFila) }
}

/** Ajusta los cupos de una fecha (o la abre, o agrega una salida); 409 CUPOS_RESERVADOS si baja de lo reservado. */
export async function ajustarFila(id: string, cambio: CambioFila): Promise<FilaInventario> {
  return normalizarFila(await solicitar<FilaInventario>(`${BASE_RESERVAS}/servicios/${encodeURIComponent(id)}/inventario`, {
    method: 'PUT',
    cuerpo: cambio,
  }))
}

/** Cierra una fecha o una salida; 409 FECHA_CON_RESERVAS si tiene reservas Confirmadas. */
export async function cerrarFila(id: string, fila: Pick<FilaInventario, 'fecha' | 'horaInicio' | 'idUnidad'>): Promise<FilaInventario> {
  const consulta = new URLSearchParams({ fecha: fila.fecha })
  if (fila.horaInicio) consulta.set('horaInicio', fila.horaInicio)
  if (fila.idUnidad) consulta.set('idUnidad', fila.idUnidad)
  return normalizarFila(
    await solicitar<FilaInventario>(`${BASE_RESERVAS}/servicios/${encodeURIComponent(id)}/inventario?${consulta}`, {
      method: 'DELETE',
    }),
  )
}

/** La plantilla de las fechas que aún no tienen inventario (202: el catálogo se actualiza por evento). */
export function cambiarPlantilla(id: string, diasOperacion: string[], salidas: string[] | null): Promise<void> {
  return solicitar<void>(`${BASE_RESERVAS}/servicios/${encodeURIComponent(id)}/plantilla`, {
    method: 'PUT',
    cuerpo: { diasOperacion, salidas },
  })
}

export interface Asistente {
  idReserva: string
  nombre: string | null
  correo: string | null
  telefono: string | null
  personas: number
  horaInicio: string | null
  nombreUnidad: string | null
  fechaEntrada: string
  /** Solo en Alojamiento; en Tour es null. */
  fechaSalida: string | null
}

export interface ListadoAsistentes {
  idServicio: string
  fecha: string
  horaInicio: string | null
  asistentes: Asistente[]
  totalPersonas: number
}

export async function consultarAsistentes(servicio: string, fecha: string, horaInicio: string): Promise<ListadoAsistentes> {
  const consulta = new URLSearchParams({ servicio, fecha })
  if (horaInicio) consulta.set('horaInicio', horaInicio)
  const listado = await solicitar<ListadoAsistentes>(`${BASE_RESERVAS}/asistentes?${consulta}`)
  return {
    ...listado,
    horaInicio: hora(listado.horaInicio),
    asistentes: listado.asistentes.map((a) => ({ ...a, horaInicio: hora(a.horaInicio) })),
  }
}

export type TipoModificacion = 'Precio' | 'Cupos' | 'Apertura' | 'Cierre' | 'Plantilla'

export interface Modificacion {
  idServicio: string
  nombreServicio: string | null
  tipo: TipoModificacion
  detalle: string | null
  valorAnterior: string | null
  valorNuevo: string
  fecha: string
}

export interface PaginaModificaciones {
  elementos: Modificacion[]
  pagina: number
  tamano: number
  totalElementos: number
}

/** La página de la pantalla empieza en 1; la de la API, en 0. */
export function consultarHistorialModificaciones(servicio: string, pagina: number): Promise<PaginaModificaciones> {
  const consulta = new URLSearchParams()
  if (servicio) consulta.set('servicio', servicio)
  consulta.set('pagina', String(Math.max(0, pagina - 1)))
  return solicitar<PaginaModificaciones>(`${BASE_CATALOGO}/historial-modificaciones?${consulta}`)
}

export function agregarImagen(id: string, imagen: File): Promise<{ imagenes: string[] }> {
  const formulario = new FormData()
  formulario.append('imagen', imagen)
  return solicitar<{ imagenes: string[] }>(`${BASE_CATALOGO}/servicios/${encodeURIComponent(id)}/imagenes`, {
    method: 'POST',
    formulario,
  })
}

export function quitarImagen(id: string, ruta: string): Promise<{ imagenes: string[] }> {
  return solicitar<{ imagenes: string[] }>(
    `${BASE_CATALOGO}/servicios/${encodeURIComponent(id)}/imagenes?${new URLSearchParams({ ruta })}`,
    { method: 'DELETE' },
  )
}

/** RF42: CSV con BOM (Excel lo abre en UTF-8), separado por punto y coma, con cada campo entre comillas. */
export function asistentesCsv(listado: ListadoAsistentes, encabezados: string[]): string {
  const campo = (valor: string | number | null) => `"${String(valor ?? '').replace(/"/g, '""')}"`
  const filas = listado.asistentes.map((a) =>
    [a.nombre, a.correo, a.telefono, a.personas, a.horaInicio ?? a.nombreUnidad, a.fechaEntrada, a.fechaSalida]
      .map(campo)
      .join(';'),
  )
  return '﻿' + [encabezados.map(campo).join(';'), ...filas].join('\r\n') + '\r\n'
}

export const DIAS_SEMANA = ['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado', 'Domingo'] as const
