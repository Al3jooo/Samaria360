import '../../afluencia/afluencia.css'
import type { TFunction } from 'i18next'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { TRAZO_CORAZON } from '../../../shared/components/Iconos'
import { ATRIBUCION_OSM, SVG_PIN, URL_TESELAS, ZOOM_INICIAL, ZOOM_MAXIMO } from '../../../shared/mapa/mapaBase'
import type { ResultadoOferta, Ubicacion } from '../catalogoApi'
import { rutaDetalleOferta } from '../detalle/navegacionDetalle'
import { CENTRO_SANTA_MARTA } from '../geo'
import { imagenDeCategoria } from './etiquetas'
import { claveResultado, type FavoritoPopup } from './contratoMapa'
export { claveResultado, type FavoritoPopup } from './contratoMapa'

// La política de uso de las teselas de OpenStreetMap está en shared/mapa/mapaBase.ts.
const ZOOM_MAXIMO_ENCUADRE = 15
const ZOOM_UBICACION = 14
const RELLENO_ENCUADRE: L.PointTuple = [32, 32]
const DESPLAZAMIENTO_RESALTADO = 500
/** Miniatura del popup (RF23), 3:2 como las fotos del catálogo: tamaño fijo para que la ventana no salte al cargar. */
const ANCHO_MINIATURA = 120
const ALTO_MINIATURA = 80

/** SVG fijo (sin datos de la API). El color sale de la clase del marcador, con los tokens de estilos.css. */
const SVG_UBICACION =
  '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" focusable="false">' +
  '<circle class="marcador__punto" cx="12" cy="12" r="8.5"/></svg>'

/** Los atractivos con afluencia vigente toman el color de su nivel (la leyenda de WF-05; RF39, DEC-306). */
function iconoResultado(tipo: ResultadoOferta['tipo'], afluencia?: ResultadoOferta['afluencia']): L.DivIcon {
  const nivel = tipo === 'Atractivo' && afluencia ? ` marcador--afluencia-${afluencia.toLowerCase()}` : ''
  return L.divIcon({
    className: `marcador marcador--${tipo === 'Servicio' ? 'servicio' : 'atractivo'}${nivel}`,
    html: SVG_PIN,
    iconSize: [28, 36],
    iconAnchor: [14, 35],
    popupAnchor: [0, -32],
  })
}

function iconoUbicacion(): L.DivIcon {
  return L.divIcon({ className: 'marcador marcador--ubicacion', html: SVG_UBICACION, iconSize: [24, 24], iconAnchor: [12, 12] })
}

/**
 * Botón del corazón armado con nodos (como el resto del popup). Lee el estado vigente al abrirse y al pulsarlo, así
 * que decide PUT o DELETE con el valor actual; si el corazón de la tarjeta cambia con el popup abierto, este se pone
 * al día al volver a abrirlo (limitación aceptada, DEC-94).
 */
function botonFavoritoPopup(
  resultado: ResultadoOferta,
  t: TFunction,
  favorito: () => FavoritoPopup | undefined,
): HTMLButtonElement | null {
  const inicial = favorito()
  if (!inicial) return null
  const boton = document.createElement('button')
  boton.type = 'button'
  boton.className = 'popup-oferta__favorito'
  boton.setAttribute('aria-label', t('favoritos.boton', { nombre: resultado.nombre }))
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('viewBox', '0 0 24 24')
  svg.setAttribute('width', '20')
  svg.setAttribute('height', '20')
  svg.setAttribute('aria-hidden', 'true')
  svg.setAttribute('focusable', 'false')
  const trazo = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  trazo.setAttribute('d', TRAZO_CORAZON)
  svg.append(trazo)
  boton.append(svg)

  if (inicial.modo === 'visitante') {
    boton.addEventListener('click', () => favorito()?.irALogin())
    return boton
  }
  const pintar = (marcado: boolean) => {
    boton.setAttribute('aria-pressed', String(marcado))
    trazo.setAttribute('fill', marcado ? 'currentColor' : 'none')
  }
  pintar(inicial.listo && inicial.esFavorito(resultado))
  if (!inicial.listo) boton.setAttribute('aria-disabled', 'true')
  boton.addEventListener('click', () => {
    const actual = favorito()
    if (!actual || actual.modo !== 'turista' || !actual.listo || boton.getAttribute('aria-busy') === 'true') return
    boton.removeAttribute('aria-disabled')
    pintar(!actual.esFavorito(resultado))
    boton.setAttribute('aria-busy', 'true')
    void actual
      .alternar(resultado)
      .then(pintar)
      .finally(() => boton.removeAttribute('aria-busy'))
  })
  return boton
}

/**
 * Ventana emergente de un resultado (RF23): miniatura, nombre y «Ver detalle» (la ficha del servicio o del atractivo,
 * DEC-224); con la sesión de un
 * turista o sin sesión, también el corazón (DEC-94). Se arma con nodos y textContent, nunca con HTML que lleve datos de
 * la API. El enlace es un <a href> real (abre en otra pestaña con las teclas de siempre); un clic normal navega dentro
 * de la app con `onVerDetalle`.
 */
function contenidoPopup(
  resultado: ResultadoOferta,
  t: TFunction,
  onVerDetalle: (ruta: string) => void,
  favorito: () => FavoritoPopup | undefined,
): HTMLElement {
  const contenedor = document.createElement('div')
  contenedor.className = 'popup-oferta'

  const respaldo = imagenDeCategoria(resultado.categoria, resultado.tipo)
  const miniatura = document.createElement('img')
  miniatura.className = 'popup-oferta__miniatura'
  miniatura.alt = t('mapa.miniatura', { nombre: resultado.nombre })
  miniatura.width = ANCHO_MINIATURA
  miniatura.height = ALTO_MINIATURA
  miniatura.decoding = 'async'
  miniatura.addEventListener('error', () => {
    if (miniatura.getAttribute('src') !== respaldo) miniatura.src = respaldo
  })
  miniatura.src = resultado.imagen ?? respaldo

  const nombre = document.createElement('p')
  nombre.className = 'popup-oferta__nombre'
  nombre.textContent = resultado.nombre
  contenedor.append(miniatura, nombre)

  const enlace = document.createElement('a')
  enlace.className = 'popup-oferta__enlace'
  enlace.href = rutaDetalleOferta(resultado.tipo, resultado.id)
  enlace.textContent = t('catalogo.tarjeta.verDetalle')
  enlace.addEventListener('click', (evento) => {
    if (evento.button !== 0 || evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) return
    evento.preventDefault()
    onVerDetalle(enlace.getAttribute('href') ?? '')
  })
  contenedor.append(enlace)
  const corazon = botonFavoritoPopup(resultado, t, favorito)
  if (corazon) contenedor.append(corazon)
  return contenedor
}

/** Ventana emergente solo con un texto (el marcador «Tu ubicación»). */
function popupTexto(texto: string): HTMLElement {
  const contenedor = document.createElement('div')
  contenedor.className = 'popup-oferta'
  const parrafo = document.createElement('p')
  parrafo.className = 'popup-oferta__nombre'
  parrafo.textContent = texto
  contenedor.append(parrafo)
  return contenedor
}

function agregarMarcador(
  capa: L.LayerGroup,
  posicion: Ubicacion,
  icono: L.DivIcon,
  nombre: string,
  zIndexOffset = 0,
  popup?: () => HTMLElement,
) {
  const marcador = L.marker([posicion.latitud, posicion.longitud], { icon: icono, title: nombre, keyboard: true, zIndexOffset })
  if (popup) marcador.bindPopup(popup)
  capa.addLayer(marcador)
  // Leaflet pone el title; el nombre accesible del marcador (role="button") se fija explícitamente.
  marcador.getElement()?.setAttribute('aria-label', nombre)
  return marcador
}

function encuadrar(mapa: L.Map | null, puntos: L.LatLngTuple[]) {
  if (mapa && puntos.length > 0) {
    mapa.fitBounds(L.latLngBounds(puntos), { padding: RELLENO_ENCUADRE, maxZoom: ZOOM_MAXIMO_ENCUADRE })
  }
}

interface PropiedadesMapa {
  /** Resultados de la página actual; null mientras carga (se conservan los marcadores anteriores). */
  resultados: ResultadoOferta[] | null
  ubicacion?: Ubicacion
  /** Ubicación a más de RADIO_SANTA_MARTA_KM: se dibuja, pero no entra en el encuadre ni se centra en ella. */
  ubicacionLejana: boolean
  /** Sube cada vez que hay que centrar el mapa en la ubicación. */
  centrado: number
  /** Falso cuando el selector «Lista / Mapa» oculta el mapa. */
  visible: boolean
  resaltada: string | null
  onSeleccionar: (clave: string) => void
  /** «Ver detalle» del popup: recibe la ruta de la ficha (servicio o atractivo). */
  onVerDetalle?: (ruta: string) => void
  /** Corazón del popup (DEC-94); sin él (otro rol o sesión validándose), no se muestra. */
  favorito?: FavoritoPopup
}

/**
 * Mapa de WF-05 con Leaflet directo (sin react-leaflet, por su licencia). El mapa se crea una sola vez y se
 * actualiza por capas; la limpieza llama a map.remove(), así que con el doble montaje de StrictMode queda uno vivo.
 */
export function MapaOferta({
  resultados,
  ubicacion,
  ubicacionLejana,
  centrado,
  visible,
  resaltada,
  onSeleccionar,
  onVerDetalle,
  favorito,
}: PropiedadesMapa) {
  const { t } = useTranslation()
  const id = useId()
  const contenedor = useRef<HTMLDivElement>(null)
  const mapa = useRef<L.Map | null>(null)
  const capaResultados = useRef<L.LayerGroup | null>(null)
  const capaUbicacion = useRef<L.LayerGroup | null>(null)
  const marcadores = useRef(new Map<string, L.Marker>())
  const puntosEncuadre = useRef<L.LatLngTuple[]>([])
  const alSeleccionar = useRef(onSeleccionar)
  const alVerDetalle = useRef(onVerDetalle)
  const favoritoActual = useRef(favorito)
  // El popup se arma al abrirse: usa el idioma y el manejador vigentes.
  const traducir = useRef(t)

  useEffect(() => {
    alSeleccionar.current = onSeleccionar
    alVerDetalle.current = onVerDetalle
    favoritoActual.current = favorito
    traducir.current = t
  })

  useEffect(() => {
    const marcadoresDelMapa = marcadores.current
    const nuevo = L.map(contenedor.current!, {
      center: [CENTRO_SANTA_MARTA.latitud, CENTRO_SANTA_MARTA.longitud],
      zoom: ZOOM_INICIAL,
      maxZoom: ZOOM_MAXIMO,
      zoomControl: false,
      keyboard: true,
      // La rueda no hace zoom hasta que el mapa tiene el foco o un clic: no atrapa el desplazamiento de la página.
      scrollWheelZoom: false,
    })
    L.tileLayer(URL_TESELAS, { maxZoom: ZOOM_MAXIMO, attribution: ATRIBUCION_OSM, updateWhenIdle: true }).addTo(nuevo)
    nuevo.on('focus click', () => nuevo.scrollWheelZoom.enable())
    nuevo.on('blur mouseout', () => nuevo.scrollWheelZoom.disable())
    capaResultados.current = L.layerGroup().addTo(nuevo)
    capaUbicacion.current = L.layerGroup().addTo(nuevo)
    mapa.current = nuevo
    return () => {
      nuevo.remove()
      mapa.current = null
      capaResultados.current = null
      capaUbicacion.current = null
      marcadoresDelMapa.clear()
    }
  }, [])

  // Control de zoom con títulos traducidos (Leaflet los usa como title y aria-label): se reemplaza al cambiar el idioma.
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
    const capa = capaResultados.current
    if (!capa || resultados === null) return
    capa.clearLayers()
    marcadores.current.clear()
    for (const resultado of resultados) {
      const clave = claveResultado(resultado)
      const marcador = agregarMarcador(capa, resultado.coordenada, iconoResultado(resultado.tipo, resultado.afluencia), resultado.nombre, 0, () =>
        contenidoPopup(
          resultado,
          traducir.current,
          (idServicio) => alVerDetalle.current?.(idServicio),
          () => favoritoActual.current,
        ),
      )
      marcador.on('click', () => alSeleccionar.current(clave))
      marcadores.current.set(clave, marcador)
    }
    const puntos: L.LatLngTuple[] = resultados.map(({ coordenada }) => [coordenada.latitud, coordenada.longitud])
    if (ubicacion && !ubicacionLejana) puntos.push([ubicacion.latitud, ubicacion.longitud])
    puntosEncuadre.current = puntos
    encuadrar(mapa.current, puntos)
    // Solo al llegar los resultados: la ubicación cambia antes que ellos (la consulta la incluye).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultados])

  useEffect(() => {
    const capa = capaUbicacion.current
    if (!capa) return
    capa.clearLayers()
    if (ubicacion) {
      const texto = t('mapa.tuUbicacion')
      agregarMarcador(capa, ubicacion, iconoUbicacion(), texto, 1000, () => popupTexto(texto))
    }
  }, [ubicacion, t])

  useEffect(() => {
    if (centrado === 0 || !ubicacion || ubicacionLejana || !mapa.current) return
    mapa.current.setView([ubicacion.latitud, ubicacion.longitud], Math.max(mapa.current.getZoom(), ZOOM_UBICACION))
    // Solo ante una petición explícita de centrar: actualizar la ubicación no debe mover el mapa del usuario.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [centrado])

  // Al mostrarse (selector «Lista / Mapa»), el contenedor cambió de tamaño estando oculto.
  useEffect(() => {
    if (!visible || !mapa.current) return
    mapa.current.invalidateSize()
    encuadrar(mapa.current, puntosEncuadre.current)
  }, [visible])

  useEffect(() => {
    marcadores.current.forEach((marcador, clave) => {
      const activo = clave === resaltada
      marcador.getElement()?.classList.toggle('marcador--resaltado', activo)
      marcador.setZIndexOffset(activo ? DESPLAZAMIENTO_RESALTADO : 0)
    })
  }, [resaltada, resultados])

  return (
    <div className="mapa-oferta">
      <div
        ref={contenedor}
        className="mapa-oferta__lienzo"
        role="region"
        aria-label={t('explorar.mapa')}
        aria-describedby={`${id}-rueda`}
        tabIndex={0}
      />
      <p id={`${id}-rueda`} className="mapa-oferta__indicacion">
        {t('mapa.indicacionRueda')}
      </p>
    </div>
  )
}
