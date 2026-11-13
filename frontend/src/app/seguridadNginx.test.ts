import { describe, expect, it } from 'vitest'
import seguridad from '../../nginx/seguridad.conf?raw'

/**
 * Guardia de la Content-Security-Policy que sirve nginx (frontend/nginx/seguridad.conf). Vite no la aplica en
 * desarrollo, así que un recurso que bloquea solo se ve en el contenedor (O38, DEC-243).
 */

function directivas(conf: string): Map<string, string[]> {
  const politica = /add_header Content-Security-Policy "([^"]+)"/.exec(conf)?.[1]
  if (politica === undefined) throw new Error('seguridad.conf no define la Content-Security-Policy')
  const mapa = new Map<string, string[]>()
  for (const directiva of politica.split(';')) {
    const [nombre, ...fuentes] = directiva.trim().split(/\s+/)
    if (nombre) mapa.set(nombre, fuentes)
  }
  return mapa
}

describe('Content-Security-Policy de nginx', () => {
  const csp = directivas(seguridad)

  it('permite blob: en img-src para la vista previa de las fotos del alta de un atractivo (WF-09)', () => {
    expect(csp.get('img-src')).toContain('blob:')
  })

  it('conserva las teselas de OpenStreetMap como único origen externo de imágenes', () => {
    expect(csp.get('img-src')).toEqual(["'self'", 'data:', 'blob:', 'https://tile.openstreetmap.org'])
  })

  it("no abre blob: ni otros orígenes fuera de img-src: scripts, conexiones y el resto quedan en 'self'", () => {
    expect(csp.get('default-src')).toEqual(["'self'"])
    expect(csp.get('script-src')).toEqual(["'self'"])
    expect(csp.get('connect-src')).toEqual(["'self'"])
    expect(csp.get('font-src')).toEqual(["'self'"])
    expect(csp.get('frame-ancestors')).toEqual(["'none'"])
  })
})

describe('Permissions-Policy de nginx', () => {
  const politica = /add_header Permissions-Policy "([^"]+)" always;/.exec(seguridad)?.[1]

  it('permite la geolocalización solo al propio origen (WF-05, RF38) y niega cámara, micrófono y pagos (DEC-266)', () => {
    expect(politica?.split(/,\s*/)).toEqual(['geolocation=(self)', 'camera=()', 'microphone=()', 'payment=()'])
  })
})
