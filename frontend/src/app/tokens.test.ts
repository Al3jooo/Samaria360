import { describe, expect, it } from 'vitest'
import estilos from './estilos.css?raw'

/**
 * Guardia de contraste WCAG 2.x AA sobre los tokens reales de estilos.css. Si una pantalla futura cambia
 * un color y rompe un par, esta prueba falla. Agrega aquí los pares nuevos que use cada pantalla.
 */

const TEXTO = 4.5 // texto normal (AA)
const NO_TEXTUAL = 3 // bordes de campos, anillos de foco, iconos con significado (AA 1.4.11)

function tokens(css: string): Map<string, string> {
  const raiz = css.slice(css.indexOf(':root'), css.indexOf('}', css.indexOf(':root')))
  const mapa = new Map<string, string>()
  for (const [, nombre, valor] of raiz.matchAll(/--color-([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})/g)) {
    mapa.set(nombre, valor)
  }
  return mapa
}

function luminancia(hex: string): number {
  const [r, g, b] = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contraste(a: string, b: string): number {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x)
  return (claro + 0.05) / (oscuro + 0.05)
}

const colores = tokens(estilos)

const PARES: [frente: string, fondo: string, minimo: number, uso: string][] = [
  ['tinta', 'arena', TEXTO, 'texto principal sobre la página'],
  ['tinta', 'superficie', TEXTO, 'texto en tarjetas'],
  ['tinta', 'arena-200', TEXTO, 'aviso de intentos'],
  ['tinta', 'sol-100', TEXTO, 'texto seleccionado, aviso de sesión'],
  ['mar-700', 'sol-100', TEXTO, 'enlace del aviso de sesión'],
  ['tinta-suave', 'arena', TEXTO, 'subtítulos'],
  ['tinta-suave', 'superficie', TEXTO, 'ayudas en tarjetas, correo del menú'],
  ['tinta-suave', 'mar-100', TEXTO, 'texto de la portada de inicio'],
  ['tinta-suave', 'sol-100', TEXTO, 'texto de la portada de inicio (zona del sol)'],
  ['deshabilitado', 'superficie', TEXTO, 'deshabilitado en tarjetas, placeholder'],
  ['deshabilitado', 'arena', TEXTO, 'deshabilitado en la página'],
  ['superficie', 'atardecer-600', TEXTO, 'botón principal'],
  ['superficie', 'atardecer-700', TEXTO, 'botón principal en hover'],
  ['superficie', 'mar-700', TEXTO, 'idioma activo, saltar al contenido, vista Lista/Mapa activa'],
  ['superficie', 'mar-900', TEXTO, 'iniciales del avatar'],
  ['mar-700', 'superficie', TEXTO, 'idioma inactivo, pestaña activa, vista Lista/Mapa inactiva, enlaces legales del login y del registro'],
  ['mar-900', 'superficie', TEXTO, 'enlaces legales del login y del registro en hover'],
  ['mar-700', 'arena', TEXTO, 'navegación activa'],
  ['mar-700', 'mar-100', TEXTO, 'idioma en hover'],
  ['mar-900', 'mar-100', TEXTO, 'titular de la portada, chip, opción del menú en hover, aviso de ubicación'],
  ['mar-900', 'sol-100', TEXTO, 'titular de la portada (zona del sol)'],
  ['mar-900', 'arena', TEXTO, 'título del login'],
  ['error', 'superficie', TEXTO, 'error de campo'],
  ['error', 'error-fondo', TEXTO, 'mensaje de error'],
  ['cafe-700', 'sol-100', TEXTO, 'texto café sobre sol (avisos del Administrador)'],
  ['pie-texto', 'mar-900', TEXTO, 'títulos del pie'],
  ['pie-suave', 'mar-900', TEXTO, 'enlaces y derechos del pie'],
  ['borde-campo', 'superficie', NO_TEXTUAL, 'borde de los campos'],
  ['borde-campo', 'arena', NO_TEXTUAL, 'borde de los campos sobre la página'],
  ['mar-700', 'superficie', NO_TEXTUAL, 'anillo de foco en tarjetas, tarjeta resaltada, marcador de atractivo (pin con borde blanco)'],
  ['mar-700', 'arena', NO_TEXTUAL, 'anillo de foco en la página'],
  ['sol-400', 'mar-900', NO_TEXTUAL, 'anillo de foco en el pie'],
  ['atardecer-600', 'arena-200', NO_TEXTUAL, 'icono y borde del aviso de intentos'],
  ['error', 'superficie', NO_TEXTUAL, 'borde de campo inválido'],
  ['atardecer-600', 'superficie', NO_TEXTUAL, 'marcador de servicio (pin con borde blanco)'],
  ['mar-900', 'sol-400', NO_TEXTUAL, 'borde del marcador «Tu ubicación»'],
]

describe('Tokens de color (WCAG AA)', () => {
  it.each(PARES)('%s sobre %s ≥ %s:1 (%s)', (frente, fondo, minimo) => {
    const a = colores.get(frente)
    const b = colores.get(fondo)
    expect(a, `falta el token --color-${frente}`).toBeDefined()
    expect(b, `falta el token --color-${fondo}`).toBeDefined()
    expect(contraste(a!, b!)).toBeGreaterThanOrEqual(minimo)
  })
})

/**
 * Mezcla alfa en sRGB (como compone el navegador): `frente` con opacidad `alfa` sobre `fondo`. Cada canal se redondea
 * hacia el valor del fondo cuando el fondo es más claro, así el resultado nunca es más oscuro que el real y el
 * contraste calculado nunca supera al medido en el navegador.
 */
function mezcla(frente: string, fondo: string, alfa: number): string {
  const canal = (hex: string, i: number) => parseInt(hex.slice(i, i + 2), 16)
  return `#${[1, 3, 5]
    .map((i) => {
      const valor = alfa * canal(frente, i) + (1 - alfa) * canal(fondo, i)
      return canal(fondo, i) >= canal(frente, i) ? Math.ceil(valor) : Math.floor(valor)
    })
    .map((c) => c.toString(16).padStart(2, '0'))
    .join('')}`
}

const opacidadVelo = Number(estilos.match(/--opacidad-velo-texto:\s*([0-9.]+)/)?.[1])

/** Texto grande según WCAG 1.4.3: ≥ 24 px, o ≥ 18,67 px (14 pt) en negrita (≥ 700). Le basta 3:1. */
const TEXTO_GRANDE = 3

/**
 * Texto sobre fotos (portada del inicio). La foto se oscurece de forma uniforme con mar-900 al --opacidad-velo-texto.
 * Como el contraste depende de la foto, se comprueba el peor caso: ese oscurecimiento sobre un píxel BLANCO, el más
 * claro posible. Si cumple ahí, cumple con cualquier foto. El título (32–44 px) y el texto de apoyo (19 px, 700) son
 * texto grande, en blanco. El contraste medido sobre la foto real se reporta aparte y debe ser mayor o igual que este.
 */
describe('Texto sobre la portada oscurecida (peor caso: píxel blanco)', () => {
  it('el token --opacidad-velo-texto existe y está entre 0 y 1', () => {
    expect(opacidadVelo).toBeGreaterThan(0)
    expect(opacidadVelo).toBeLessThanOrEqual(1)
  })

  it.each([
    ['superficie', 'título de la portada (32–44 px)'],
    ['superficie', 'texto de apoyo de la portada (19 px, 700)'],
  ])('%s sobre la foto oscurecida ≥ 3:1, texto grande (%s)', (texto) => {
    const fondoPeorCaso = mezcla(colores.get('mar-900')!, '#ffffff', opacidadVelo)
    expect(contraste(colores.get(texto)!, fondoPeorCaso)).toBeGreaterThanOrEqual(TEXTO_GRANDE)
  })
})
