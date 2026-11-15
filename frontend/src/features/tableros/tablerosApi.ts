import { solicitar } from '../../shared/api/httpClient'

/** Tableros de ms-analytics (`docs/sad/api/ms-analytics.md`, «Tableros»; DEC-308). Días de Santa Marta. */

export interface ReservasDia {
  dia: string
  reservas: number
}

export interface EntidadConsultada {
  id: string
  nombre: string | null
  consultas: number
}

export interface VisitantesZona {
  pais: string | null
  turistas: number
}

export interface TableroGeneral {
  desde: string
  hasta: string
  reservasPorDia: ReservasDia[]
  totalReservas: number
  atractivosMasConsultados: EntidadConsultada[]
  visitantesPorZona: VisitantesZona[]
}

export interface ServicioPanel {
  idServicio: string
  nombre: string | null
  consultas: number
  reservas: number
}

export interface PanelPrestador {
  dias: 7 | 30
  desde: string
  hasta: string
  servicios: ServicioPanel[]
}

export interface AfluenciaAtractivo {
  idAtractivo: string
  nombre: string
  lecturas: number
  porcentajePromedio: number | null
  porcentajeMaximo: number
  ultimoNivel: 'Bajo' | 'Medio' | 'Alto'
  ultimaLectura: string
}

export interface AfluenciaDia {
  dia: string
  porcentajePromedio: number | null
  porcentajeMaximo: number
  lecturas: number
}

export interface ReporteAfluencia {
  desde: string
  hasta: string
  atractivo: string | null
  atractivos: AfluenciaAtractivo[]
  porDia: AfluenciaDia[]
  atractivosDisponibles: { idAtractivo: string; nombre: string }[]
}

function consulta(parametros: Record<string, string | undefined>): string {
  const p = new URLSearchParams()
  Object.entries(parametros).forEach(([clave, valor]) => {
    if (valor) p.set(clave, valor)
  })
  const texto = p.toString()
  return texto ? `?${texto}` : ''
}

/** RF14: Administrador y Gestor. */
export function consultarTableroGeneral(desde?: string, hasta?: string): Promise<TableroGeneral> {
  return solicitar<TableroGeneral>(`/api/v1/analytics/tableros/general${consulta({ desde, hasta })}`)
}

/** RF31: el Prestador del token, 7 o 30 días. */
export function consultarPanelPrestador(dias: 7 | 30): Promise<PanelPrestador> {
  return solicitar<PanelPrestador>(`/api/v1/analytics/prestador/panel?dias=${dias}`)
}

/** RF64: el Gestor. */
export function consultarReporteAfluencia(desde?: string, hasta?: string, atractivo?: string): Promise<ReporteAfluencia> {
  return solicitar<ReporteAfluencia>(`/api/v1/analytics/gestor/afluencia${consulta({ desde, hasta, atractivo })}`)
}
