import i18n, { type TFunction } from 'i18next'
import { longitudTexto } from '../admin/textoAcotado'
import { localeDe } from '../catalogo/formato'
import { aNumero } from '../prestador/borradorServicio'
import { rutaSinIndices, type ErroresServicio, type MotivoServicio } from '../prestador/rutasCampos'
import type { AtractivoGestion, DatosAtractivo } from './atractivosGestorApi'

/**
 * Borrador de WF-09 (alta y edición). Los números van como texto, como en WF-08. Las reglas son las de
 * `RegistroAtractivo` (ms-catalog): el cliente avisa antes de enviar y el servidor sigue siendo la fuente.
 */
export interface BorradorAtractivo {
  nombre: string
  categoria: string
  descripcion: string
  latitud: string
  longitud: string
  limiteCapacidadCarga: string
  /** Solo en el alta (DEC-210); en la edición el estado cambia con su diálogo. */
  estadoOperativo: string
  normasAmbientales: string
  horarioAtencion: string
  condicionesAccesibilidad: string[]
}

/** Máximos del validador v2 (M012), en puntos de código (DEC-227). */
export const MAXIMO_ATRACTIVO: Record<string, number> = {
  nombre: 120,
  descripcion: 1000,
  normasAmbientales: 1000,
  horarioAtencion: 500,
}
export const CAPACIDAD_MAXIMA = 100_000

export function borradorVacio(): BorradorAtractivo {
  return {
    nombre: '',
    categoria: '',
    descripcion: '',
    latitud: '',
    longitud: '',
    limiteCapacidadCarga: '',
    estadoOperativo: 'Abierto',
    normasAmbientales: '',
    horarioAtencion: '',
    condicionesAccesibilidad: [],
  }
}

/** El borrador de la edición, con los datos guardados. */
export function borradorDe(atractivo: AtractivoGestion): BorradorAtractivo {
  return {
    nombre: atractivo.nombre,
    categoria: atractivo.categoria ?? '',
    descripcion: atractivo.descripcion ?? '',
    latitud: String(atractivo.coordenadas.latitud),
    longitud: String(atractivo.coordenadas.longitud),
    limiteCapacidadCarga: String(atractivo.limiteCapacidadCarga),
    estadoOperativo: atractivo.estadoOperativo ?? '',
    normasAmbientales: atractivo.normasAmbientales ?? '',
    horarioAtencion: atractivo.horarioAtencion ?? '',
    condicionesAccesibilidad: [...atractivo.condicionesAccesibilidad],
  }
}

/** Cuerpo de `datos` (registro) o de la edición. El orden de las coordenadas lo fija el contrato. */
export function construirDatos(borrador: BorradorAtractivo, conEstado: boolean): DatosAtractivo {
  const capacidad = borrador.limiteCapacidadCarga.trim()
  return {
    nombre: borrador.nombre,
    categoria: borrador.categoria,
    descripcion: borrador.descripcion,
    coordenadas: { longitud: aNumero(borrador.longitud), latitud: aNumero(borrador.latitud) },
    limiteCapacidadCarga: /^\d+$/.test(capacidad) ? Number(capacidad) : null,
    ...(conEstado ? { estadoOperativo: borrador.estadoOperativo } : {}),
    normasAmbientales: borrador.normasAmbientales,
    horarioAtencion: borrador.horarioAtencion,
    condicionesAccesibilidad: borrador.condicionesAccesibilidad,
  }
}

/** Validación en el cliente: obligatorios, formatos, rangos y longitudes. */
export function validarBorrador(borrador: BorradorAtractivo, conEstado: boolean): ErroresServicio {
  const errores: ErroresServicio = {}
  const texto = (ruta: string, valor: string, obligatorio: boolean) => {
    const longitud = longitudTexto(valor)
    if (longitud === 0 && obligatorio) errores[ruta] = 'REQUERIDO'
    else if (longitud > MAXIMO_ATRACTIVO[ruta]) errores[ruta] = 'LONGITUD_MAXIMA'
  }
  const coordenada = (ruta: string, valor: string, limite: number) => {
    const numero = aNumero(valor)
    if (valor.trim() === '') errores[ruta] = 'REQUERIDO'
    else if (numero === null) errores[ruta] = 'FORMATO'
    else if (numero < -limite) errores[ruta] = 'VALOR_MINIMO'
    else if (numero > limite) errores[ruta] = 'VALOR_MAXIMO'
  }

  texto('nombre', borrador.nombre, true)
  if (borrador.categoria === '') errores.categoria = 'REQUERIDO'
  texto('descripcion', borrador.descripcion, true)
  coordenada('coordenadas.latitud', borrador.latitud, 90)
  coordenada('coordenadas.longitud', borrador.longitud, 180)
  const capacidad = borrador.limiteCapacidadCarga.trim()
  if (capacidad === '') errores.limiteCapacidadCarga = 'REQUERIDO'
  else if (!/^\d+$/.test(capacidad)) errores.limiteCapacidadCarga = 'FORMATO'
  else if (Number(capacidad) < 1) errores.limiteCapacidadCarga = 'VALOR_MINIMO'
  else if (Number(capacidad) > CAPACIDAD_MAXIMA) errores.limiteCapacidadCarga = 'VALOR_MAXIMO'
  if (conEstado && borrador.estadoOperativo === '') errores.estadoOperativo = 'REQUERIDO'
  texto('normasAmbientales', borrador.normasAmbientales, false)
  texto('horarioAtencion', borrador.horarioAtencion, false)
  return errores
}

/**
 * Rutas con un control, en el orden de WF-09: el foco del primer error y qué error del servidor no tiene lugar.
 *
 * @param imagenes cuántas imágenes hay en el alta; sin él (edición, DEC-226), las imágenes no tienen control
 */
export function rutasConControl(conEstado: boolean, imagenes?: number): string[] {
  const rutas = [
    'nombre',
    'categoria',
    'descripcion',
    'coordenadas',
    'coordenadas.latitud',
    'coordenadas.longitud',
    'condicionesAccesibilidad',
    'limiteCapacidadCarga',
    ...(conEstado ? ['estadoOperativo'] : []),
  ]
  if (imagenes !== undefined) {
    rutas.push('imagenes', ...Array.from({ length: imagenes }, (_, indice) => `imagenes[${indice}]`))
  }
  rutas.push('normasAmbientales', 'horarioAtencion')
  return rutas
}

/** Texto de `prestador.errores.<motivo>` con el máximo del atractivo (no el del servicio). */
export function textoErrorAtractivo(t: TFunction, ruta: string, motivo: MotivoServicio | undefined): string | undefined {
  if (!motivo) return undefined
  if (motivo === 'VALOR_MAXIMO' && ruta === 'limiteCapacidadCarga') {
    return t('gestor.errores.capacidadMaxima', { maximo: CAPACIDAD_MAXIMA.toLocaleString(localeDe(i18n.language)) })
  }
  if (rutaSinIndices(ruta) === 'imagenes') {
    return t(`gestor.imagenes.errores.${motivo}`, { defaultValue: t(`prestador.errores.${motivo}`, { maximo: '' }) })
  }
  return t(`prestador.errores.${motivo}`, { maximo: MAXIMO_ATRACTIVO[ruta] ?? '' })
}
