import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ATRIBUCION_OSM, SVG_PIN, URL_TESELAS, ZOOM_MAXIMO } from '../../shared/mapa/mapaBase'
import '../catalogo/catalogo.css'
import './servicio.css'

const ZOOM_PIN = 16

/**
 * Ubicación de un servicio en la revisión de WF-19 (DEC-176) o de un atractivo en su ficha (DEC-224): Leaflet directo,
 * con las teselas de OpenStreetMap y su atribución (shared/mapa/mapaBase.ts), centrado en el pin. Solo lectura: el pin
 * no se arrastra y un clic no lo mueve. Las coordenadas se muestran también en texto, fuera del mapa.
 */
export function MapaSoloLectura({
  latitud,
  longitud,
  nombre,
  tipo = 'servicio',
  etiqueta,
}: {
  latitud: number
  longitud: number
  nombre: string
  /** El pin de WF-05 de su tipo (`marcador--servicio` o `marcador--atractivo`). */
  tipo?: 'servicio' | 'atractivo'
  /** Nombre accesible del mapa; por defecto, el de la revisión de WF-19. */
  etiqueta?: string
}) {
  const { t } = useTranslation()
  const contenedor = useRef<HTMLDivElement>(null)
  const mapa = useRef<L.Map | null>(null)
  const capa = useRef<L.LayerGroup | null>(null)

  useEffect(() => {
    const nuevo = L.map(contenedor.current!, {
      center: [latitud, longitud],
      zoom: ZOOM_PIN,
      maxZoom: ZOOM_MAXIMO,
      zoomControl: false,
      keyboard: true,
      scrollWheelZoom: false,
    })
    L.tileLayer(URL_TESELAS, { maxZoom: ZOOM_MAXIMO, attribution: ATRIBUCION_OSM, updateWhenIdle: true }).addTo(nuevo)
    nuevo.on('focus click', () => nuevo.scrollWheelZoom.enable())
    nuevo.on('blur mouseout', () => nuevo.scrollWheelZoom.disable())
    capa.current = L.layerGroup().addTo(nuevo)
    mapa.current = nuevo
    return () => {
      nuevo.remove()
      mapa.current = null
      capa.current = null
    }
    // El mapa se crea una vez; el pin y el encuadre siguen a las coordenadas (efecto de abajo).
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    if (!capaPin || !mapa.current) return
    capaPin.clearLayers()
    const marcador = L.marker([latitud, longitud], {
      icon: L.divIcon({ className: `marcador marcador--${tipo}`, html: SVG_PIN, iconSize: [28, 36], iconAnchor: [14, 35] }),
      title: nombre,
      keyboard: false,
    })
    capaPin.addLayer(marcador)
    marcador.getElement()?.setAttribute('aria-hidden', 'true')
    mapa.current.setView([latitud, longitud], ZOOM_PIN)
  }, [latitud, longitud, nombre, tipo])

  return (
    <div className="mapa-oferta mapa-servicio">
      <div
        ref={contenedor}
        className="mapa-oferta__lienzo"
        role="region"
        aria-label={etiqueta ?? t('admin.servicios.revision.mapa', { nombre })}
        tabIndex={0}
      />
    </div>
  )
}
