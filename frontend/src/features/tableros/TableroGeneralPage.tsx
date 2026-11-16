import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { formatearFecha } from '../reservas/fechasBogota'
import { Barras, Cifra, Columnas } from './Graficos'
import { consultarTableroGeneral, type TableroGeneral } from './tablerosApi'
import './tableros.css'

type Estado = { estado: 'cargando' } | { estado: 'error' } | { estado: 'ok'; datos: TableroGeneral }

/** «2030-03-10» → «10/03» en español y «03/10» en inglés (mes primero). */
export function diaCorto(dia: string, idioma: string): string {
  const [, mes, d] = dia.split('-')
  return idioma.startsWith('en') ? `${mes}/${d}` : `${d}/${mes}`
}

/** Nombre del país en el idioma de la interfaz (ISO 3166-1 alfa-2). */
export function nombrePais(codigo: string | null, idioma: string, sinPais: string): string {
  if (!codigo) return sinPais
  try {
    return new Intl.DisplayNames([idioma], { type: 'region' }).of(codigo) ?? codigo
  } catch {
    return codigo
  }
}

/**
 * WF-35 «Tableros» (RF14; respuesta 4 del equipo, DEC-308), para el Administrador y el Gestor: el total de reservas
 * del rango con las reservas por día, el top 3 de atractivos más consultados y los turistas registrados por país
 * (decisión 6: «zona» es el país). El rango (por defecto, los últimos 30 días) va en la URL.
 */
export function TableroGeneralPage() {
  const { t, i18n } = useTranslation()
  const [parametros, setParametros] = useSearchParams()
  const desde = parametros.get('desde') ?? ''
  const hasta = parametros.get('hasta') ?? ''
  const [borrador, setBorrador] = useState({ desde, hasta })
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })

  useEffect(() => {
    let vigente = true
    setEstado({ estado: 'cargando' })
    consultarTableroGeneral(desde, hasta)
      .then((datos) => {
        if (vigente) setEstado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (vigente) setEstado({ estado: 'error' })
      })
    return () => {
      vigente = false
    }
  }, [desde, hasta])

  const filtrar = (evento: FormEvent) => {
    evento.preventDefault()
    const nuevos = new URLSearchParams()
    if (borrador.desde) nuevos.set('desde', borrador.desde)
    if (borrador.hasta) nuevos.set('hasta', borrador.hasta)
    setParametros(nuevos)
  }

  return (
    <div className="tablero">
      <h1>{t('tableros.general.titulo')}</h1>
      <form className="tablero__filtros" onSubmit={filtrar}>
        <label>
          {t('tableros.desde')}
          <input type="date" value={borrador.desde} onChange={(e) => setBorrador({ ...borrador, desde: e.target.value })} />
        </label>
        <label>
          {t('tableros.hasta')}
          <input type="date" value={borrador.hasta} onChange={(e) => setBorrador({ ...borrador, hasta: e.target.value })} />
        </label>
        <button type="submit" className="boton-secundario">
          {t('tableros.aplicar')}
        </button>
      </form>
      {estado.estado === 'cargando' && (
        <p className="estado-carga" role="status" aria-busy="true">
          {t('tableros.cargando')}
        </p>
      )}
      {estado.estado === 'error' && (
        <p className="tablero__error" role="alert">
          {t('tableros.error')}
        </p>
      )}
      {estado.estado === 'ok' && (
        <>
          <p>{t('tableros.periodo', {
            desde: formatearFecha(estado.datos.desde, i18n.language),
            hasta: formatearFecha(estado.datos.hasta, i18n.language),
          })}</p>
          <div className="tablero__cuadricula">
            <Cifra
              etiqueta={t('tableros.general.totalReservas')}
              valor={estado.datos.totalReservas.toLocaleString(i18n.language)}
            />
          </div>
          <Columnas
            titulo={t('tableros.general.reservasPorDia')}
            unidad={t('tableros.general.reservas')}
            datos={estado.datos.reservasPorDia.map((d) => ({ clave: d.dia, etiqueta: diaCorto(d.dia, i18n.language), valor: d.reservas }))}
          />
          <div className="tablero__cuadricula">
            <Barras
              titulo={t('tableros.general.topAtractivos')}
              vacio={t('tableros.general.sinConsultas')}
              datos={estado.datos.atractivosMasConsultados.map((a) => ({
                clave: a.id,
                etiqueta: a.nombre ?? a.id,
                valor: a.consultas,
                texto: t('tableros.general.consultas', { count: a.consultas }),
              }))}
            />
            <Barras
              titulo={t('tableros.general.visitantesPorZona')}
              vacio={t('tableros.general.sinTuristas')}
              datos={estado.datos.visitantesPorZona.map((z) => ({
                clave: z.pais ?? 'sin-pais',
                etiqueta: nombrePais(z.pais, i18n.language, t('tableros.general.sinPais')),
                valor: z.turistas,
              }))}
            />
          </div>
        </>
      )}
    </div>
  )
}
