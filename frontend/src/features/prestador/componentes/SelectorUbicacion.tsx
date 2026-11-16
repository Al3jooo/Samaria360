import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ATRIBUCION_OSM, SVG_PIN, URL_TESELAS, ZOOM_INICIAL, ZOOM_MAXIMO } from '../../../shared/mapa/mapaBase'
import { CENTRO_SANTA_MARTA } from '../../catalogo/geo'
import { aNumero } from '../borradorServicio'
import { idDeCampo, textoErrorServicio, type ErroresServicio } from '../rutasCampos'
import { CampoServicio, ErrorGrupo } from './CampoServicio'
import '../../catalogo/catalogo.css'

/** Seis decimales: unos 10 cm, de sobra para ubicar un negocio. */
const DECIMALES = 6
const ZOOM_PIN = 16

const redondear = (valor: number) => valor.toFixed(DECIMALES)

function iconoPin(tipo: 'servicio' | 'atractivo'): L.DivIcon {
  return L.divIcon({ className: `marcador marcador--${tipo}`, html: SVG_PIN, iconSize: [28, 36], iconAnchor: [14, 35] })
}

export interface Coordenadas {
  latitud: string
  longitud: string
}

/**
 * Ubicación del servicio en WF-08 (DEC-132 y DEC-165): Leaflet directo, con las teselas de OpenStreetMap y su
 * atribución (shared/mapa/mapaBase.ts). Un clic en el mapa pone el pin y el pin se puede arrastrar. Los campos
 * «Latitud» y «Longitud», siempre visibles, son la alternativa sin ratón y se sincronizan en los dos sentidos, igual
 * que «Usar el centro de Santa Marta». Sin geocodificación: la dirección es un campo aparte. El valor viaja como
 * `{longitud, latitud}` (el orden lo fija `construirCuerpo`).
 */
export function SelectorUbicacion({
  valor,
  errores,
  onCambiar,
  tipo = 'servicio',
}: {
  valor: Coordenadas
  errores: ErroresServicio
  onCambiar: (coordenadas: Coordenadas) => void
  /** WF-09 (DEC-223) lo reutiliza para un atractivo: el pin y los nombres accesibles del mapa son los suyos. */
  tipo?: 'servicio' | 'atractivo'
}) {
  const { t } = useTranslation()
  const id = useId()
  const contenedor = useRef<HTMLDivElement>(null)
  const mapa = useRef<L.Map | null>(null)
  const capa = useRef<L.LayerGroup | null>(null)
  const alCambiar = useRef(onCambiar)
  const textos = tipo === 'atractivo' ? 'gestor.ubicacion' : 'prestador.ubicacion'
  const nombrePin = useRef(t(`${textos}.pin`))
  // Un cambio que viene del mapa (clic o arrastre) no vuelve a centrarlo; uno de los campos, sí.
  const desdeMapa = useRef(false)

  useEffect(() => {
    alCambiar.current = onCambiar
    nombrePin.current = t(`${textos}.pin`)
  })

  const latitud = aNumero(valor.latitud)
  const longitud = aNumero(valor.longitud)
  const valida = latitud !== null && longitud !== null && Math.abs(latitud) <= 90 && Math.abs(longitud) <= 180

  useEffect(() => {
    const nuevo = L.map(contenedor.current!, {
      center: [CENTRO_SANTA_MARTA.latitud, CENTRO_SANTA_MARTA.longitud],
      zoom: ZOOM_INICIAL,
      maxZoom: ZOOM_MAXIMO,
      zoomControl: false,
      keyboard: true,
      scrollWheelZoom: false,
    })
    L.tileLayer(URL_TESELAS, { maxZoom: ZOOM_MAXIMO, attribution: ATRIBUCION_OSM, updateWhenIdle: true }).addTo(nuevo)
    nuevo.on('focus click', () => nuevo.scrollWheelZoom.enable())
    nuevo.on('blur mouseout', () => nuevo.scrollWheelZoom.disable())
    nuevo.on('click', (evento: L.LeafletMouseEvent) => {
      desdeMapa.current = true
      alCambiar.current({ latitud: redondear(evento.latlng.lat), longitud: redondear(evento.latlng.lng) })
    })
    capa.current = L.layerGroup().addTo(nuevo)
    mapa.current = nuevo
    return () => {
      nuevo.remove()
      mapa.current = null
      capa.current = null
    }
  }, [])

  useEffect(() => {
    if (!mapa.current) return
    const control = L.control
      .zoom({ position: 'topright', zoomInTitle: t('mapa.acercar'), zoomOutTitle: t('mapa.alejar') })
      .addTo(mapa.current)
    return () => {
      control.remove()
    }
  }, [t])

  useEffect(() => {
    const capaPin = capa.current
    if (!capaPin) return
    capaPin.clearLayers()
    if (!valida || latitud === null || longitud === null) return
    const marcador = L.marker([latitud, longitud], {
      icon: iconoPin(tipo),
      title: nombrePin.current,
      keyboard: true,
      draggable: true,
    })
    marcador.on('dragend', () => {
      const posicion = marcador.getLatLng()
      desdeMapa.current = true
      alCambiar.current({ latitud: redondear(posicion.lat), longitud: redondear(posicion.lng) })
    })
    capaPin.addLayer(marcador)
    marcador.getElement()?.setAttribute('aria-label', nombrePin.current)
    if (!desdeMapa.current && mapa.current) {
      mapa.current.setView([latitud, longitud], Math.max(mapa.current.getZoom(), ZOOM_PIN))
    }
    desdeMapa.current = false
  }, [valida, latitud, longitud, tipo])

  const errorGrupo = textoErrorServicio(t, 'coordenadas', errores.coordenadas)
  const error = (ruta: string) => textoErrorServicio(t, ruta, errores[ruta])

  return (
    <fieldset
      id={idDeCampo('coordenadas')}
      tabIndex={-1}
      className="prestador__grupo"
      aria-describedby={[`${id}-ayuda`, errorGrupo ? `${idDeCampo('coordenadas')}-error` : null].filter(Boolean).join(' ')}
    >
      <legend>{t('prestador.ubicacion.titulo')}</legend>
      <p id={`${id}-ayuda`} className="campo__ayuda">
        {t('prestador.ubicacion.ayuda')}
      </p>
      <ErrorGrupo ruta="coordenadas" error={errorGrupo} />
      <div className="mapa-oferta prestador__mapa">
        <div
          ref={contenedor}
          className="mapa-oferta__lienzo"
          role="region"
          aria-label={t(`${textos}.mapa`)}
          tabIndex={0}
        />
      </div>
      <div className="prestador__columnas">
        <CampoServicio ruta="coordenadas.latitud" etiqueta={t('prestador.ubicacion.latitud')} inputMode="decimal"
          valor={valor.latitud} onCambiar={(v) => onCambiar({ ...valor, latitud: v })} error={error('coordenadas.latitud')} />
        <CampoServicio ruta="coordenadas.longitud" etiqueta={t('prestador.ubicacion.longitud')} inputMode="decimal"
          valor={valor.longitud} onCambiar={(v) => onCambiar({ ...valor, longitud: v })}
          error={error('coordenadas.longitud')} />
      </div>
      <button
        type="button"
        className="boton-secundario"
        onClick={() =>
          onCambiar({ latitud: redondear(CENTRO_SANTA_MARTA.latitud), longitud: redondear(CENTRO_SANTA_MARTA.longitud) })
        }
      >
        {t('prestador.ubicacion.centro')}
      </button>
    </fieldset>
  )
}
