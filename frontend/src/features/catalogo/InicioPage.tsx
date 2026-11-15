import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import {
  IconoAlojamiento,
  IconoAlquiler,
  IconoBuscar,
  IconoGastronomia,
  IconoTour,
} from '../../shared/components/Iconos'
import { useDisponibilidadResumen } from '../reservas/useDisponibilidadResumen'
import type { Categoria, CriteriosBusqueda } from './catalogoApi'
import { EstadoCatalogo } from './componentes/EstadoCatalogo'
import { TarjetaOferta } from './componentes/TarjetaOferta'
import { useBusquedaOferta } from './useBusquedaOferta'
import './catalogo.css'

const CATEGORIAS_INICIO: { categoria: Categoria; Icono: () => ReactNode }[] = [
  { categoria: 'Alojamiento', Icono: IconoAlojamiento },
  { categoria: 'Gastronomia', Icono: IconoGastronomia },
  { categoria: 'Tour', Icono: IconoTour },
  { categoria: 'AlquilerEquipo', Icono: IconoAlquiler },
]

/**
 * «Destacados esta semana» (DEC-317, decisión del equipo): los servicios mejor calificados (RF17, `orden=Calificacion`).
 * La búsqueda mezcla servicios y atractivos y no filtra por tipo, así que se piden 10 y se muestran los 3 primeros
 * servicios.
 */
const CRITERIOS_DESTACADOS: CriteriosBusqueda = { accesibilidad: [], orden: 'Calificacion', pagina: 1, tamano: 10 }
const DESTACADOS_VISIBLES = 3

/** WF-04: inicio y búsqueda (pública). El banner de avisos (RF40) no se muestra: no existe el modelo de avisos. */
export function InicioPage() {
  const { t } = useTranslation()
  const navegar = useNavigate()
  const [texto, setTexto] = useState('')
  const destacados = useBusquedaOferta(CRITERIOS_DESTACADOS)

  const buscar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const buscado = texto.trim()
    navegar(buscado ? `/explorar?${new URLSearchParams({ texto: buscado })}` : '/explorar')
  }

  const servicios =
    destacados.estado === 'ok'
      ? destacados.pagina.elementos.filter((e) => e.tipo === 'Servicio').slice(0, DESTACADOS_VISIBLES)
      : []
  const disponibilidad = useDisponibilidadResumen(destacados.estado === 'ok' ? servicios : null)

  return (
    <div className="inicio">
      <section className="inicio__portada" aria-labelledby="titulo-inicio">
        {/* Foto decorativa de la bahía de Santa Marta (LCP: sin carga diferida). Se oscurece de forma uniforme
            (--opacidad-velo-texto) y los textos son grandes y blancos: AA con cualquier foto (ver tokens.test.ts). */}
        <img
          className="inicio__portada-foto"
          src="/imagenes/ambiente/portada-inicio.webp"
          alt=""
          width={1920}
          height={1080}
          fetchPriority="high"
          decoding="async"
        />
        <div className="inicio__portada-contenido">
          <h1 id="titulo-inicio">{t('inicio.titular')}</h1>
          <p className="inicio__apoyo">{t('inicio.apoyo')}</p>
          <form className="buscador" role="search" onSubmit={buscar}>
            <label htmlFor="buscador-inicio" className="solo-lector">
              {t('inicio.buscarEtiqueta')}
            </label>
            <span className="buscador__icono" aria-hidden="true">
              <IconoBuscar />
            </span>
            <input
              id="buscador-inicio"
              type="search"
              maxLength={100}
              placeholder={t('inicio.buscarPlaceholder')}
              value={texto}
              onChange={(evento) => setTexto(evento.target.value)}
            />
            <button type="submit" className="boton-principal">
              {t('inicio.buscar')}
            </button>
          </form>
        </div>
      </section>

      <section className="inicio__categorias" aria-labelledby="titulo-categorias">
        <h2 id="titulo-categorias">{t('inicio.categorias')}</h2>
        <ul>
          {CATEGORIAS_INICIO.map(({ categoria, Icono }) => (
            <li key={categoria}>
              <Link to={`/explorar?categoria=${categoria}`} className="boton-categoria">
                <Icono />
                <span>{t(`catalogo.categorias.${categoria}`)}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="inicio__destacados" aria-labelledby="titulo-destacados">
        <h2 id="titulo-destacados">{t('inicio.destacados')}</h2>
        {destacados.estado === 'cargando' && <EstadoCatalogo tipo="cargando" />}
        {destacados.estado === 'error' && <EstadoCatalogo tipo={destacados.error} onReintentar={destacados.reintentar} />}
        {destacados.estado === 'ok' &&
          (servicios.length === 0 ? (
            <p className="estado-catalogo__ayuda">{t('inicio.sinDestacados')}</p>
          ) : (
            <ul className="lista-destacados">
              {servicios.map((servicio) => (
                <li key={servicio.id}>
                  <TarjetaOferta
                    resultado={servicio}
                    variante="destacado"
                    disponibilidad={disponibilidad?.[servicio.id] ?? null}
                  />
                </li>
              ))}
            </ul>
          ))}
      </section>
    </div>
  )
}
