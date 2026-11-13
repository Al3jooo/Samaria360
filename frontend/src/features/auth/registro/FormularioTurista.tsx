import { useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { IconoCandado, IconoSobre } from '../../../shared/components/Iconos'
import { RUTA_POLITICA_PRIVACIDAD } from '../../../shared/rutasLegales'
import { registrarTurista, type DatosRegistroTurista } from '../authApi'
import { Campo } from './Campo'
import { CasillasCategorias, idCasillaCategoria } from './CasillasCategorias'
import { aRechazoRegistro, enfocarPrimerError, textoErrorCampo } from './envioRegistro'
import { SelectorPais } from './SelectorPais'
import { usePaises } from './usePaises'
import { CATEGORIAS_INTERES, normalizarCorreo, validarTurista, type ErroresCampos } from './validacionRegistro'

const ID = (campo: string) => `turista-${campo}`
const ORDEN = [
  'primerNombre',
  'segundoNombre',
  'primerApellido',
  'segundoApellido',
  'correo',
  'contrasena',
  'paisOrigen',
  'categoriasInteres',
  'aceptoPoliticas',
] as const

/** Id del control que recibe el foco por campo: las categorías, en la primera casilla. */
const ID_FOCO = (campo: string) =>
  campo === 'categoriasInteres' ? idCasillaCategoria('turista', CATEGORIAS_INTERES[0]) : ID(campo)

/** WF-02: Formulario – Turista (RF01). Al terminar vuelve al login con el correo escrito (sin iniciar sesión, ADR-006). */
export function FormularioTurista({ onRegistrado }: { onRegistrado: (correo: string) => void }) {
  const { t } = useTranslation()
  const paises = usePaises()
  const [datos, setDatos] = useState<DatosRegistroTurista>({
    primerNombre: '',
    segundoNombre: '',
    primerApellido: '',
    segundoApellido: '',
    correo: '',
    contrasena: '',
    paisOrigen: '',
    categoriasInteres: [],
    aceptoPoliticas: false,
  })
  const [errores, setErrores] = useState<ErroresCampos>({})
  const [general, setGeneral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const cambiar = <K extends keyof DatosRegistroTurista>(campo: K, valor: DatosRegistroTurista[K]) =>
    setDatos((actual) => ({ ...actual, [campo]: valor }))

  const enviar = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setGeneral(null)
    const encontrados = validarTurista(datos, paises.codigos)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) {
      enfocarPrimerError(ORDEN, encontrados, ID_FOCO)
      return
    }
    setEnviando(true)
    try {
      await registrarTurista({ ...datos, correo: normalizarCorreo(datos.correo) })
      onRegistrado(normalizarCorreo(datos.correo))
    } catch (error) {
      const rechazo = aRechazoRegistro(error)
      setErrores(rechazo.campos)
      setGeneral(rechazo.general)
      enfocarPrimerError(ORDEN, rechazo.campos, ID_FOCO)
      setEnviando(false)
    }
  }

  const error = (campo: string) => textoErrorCampo(t, campo, errores[campo])
  const cantidad = Object.keys(errores).length

  return (
    <form className="tarjeta__cuerpo" noValidate onSubmit={enviar}>
      <div className="registro__resumen" role="alert" aria-atomic="true">
        {cantidad > 0 && <p>{t('registro.resumen', { count: cantidad })}</p>}
        {general && <p>{t([`registro.errorGeneral.${general}`, `errores.${general}`])}</p>}
      </div>

      <Campo id={ID('primerNombre')} etiqueta={t('registro.primerNombre')} autoComplete="given-name"
        placeholder={t('registro.primerNombrePlaceholder')} value={datos.primerNombre}
        onChange={(e) => cambiar('primerNombre', e.target.value)} error={error('primerNombre')} />
      <Campo id={ID('segundoNombre')} etiqueta={t('registro.segundoNombre')} opcional autoComplete="additional-name"
        placeholder={t('registro.segundoNombrePlaceholder')} value={datos.segundoNombre}
        onChange={(e) => cambiar('segundoNombre', e.target.value)} error={error('segundoNombre')} />
      <Campo id={ID('primerApellido')} etiqueta={t('registro.primerApellido')} autoComplete="family-name"
        placeholder={t('registro.primerApellidoPlaceholder')} value={datos.primerApellido}
        onChange={(e) => cambiar('primerApellido', e.target.value)} error={error('primerApellido')} />
      <Campo id={ID('segundoApellido')} etiqueta={t('registro.segundoApellido')} opcional autoComplete="family-name"
        placeholder={t('registro.segundoApellidoPlaceholder')} value={datos.segundoApellido}
        onChange={(e) => cambiar('segundoApellido', e.target.value)} error={error('segundoApellido')} />
      <Campo id={ID('correo')} etiqueta={t('registro.correo')} icono={<IconoSobre />} type="email" inputMode="email"
        autoComplete="email" placeholder={t('login.correoPlaceholder')} value={datos.correo}
        onChange={(e) => cambiar('correo', e.target.value)} error={error('correo')} />
      <Campo id={ID('contrasena')} etiqueta={t('registro.crearContrasena')} icono={<IconoCandado />} type="password"
        autoComplete="new-password" ayuda={t('registro.ayudaContrasena')} value={datos.contrasena}
        onChange={(e) => cambiar('contrasena', e.target.value)} error={error('contrasena')} />

      <SelectorPais id={ID('paisOrigen')} etiqueta={t('registro.paisOrigen')} paises={paises} valor={datos.paisOrigen}
        onCambiar={(codigo) => cambiar('paisOrigen', codigo)} error={error('paisOrigen')} />

      <CasillasCategorias prefijo="turista" leyenda={t('registro.categoriasInteres')} ayuda={t('registro.ayudaCategorias')}
        seleccionadas={datos.categoriasInteres} onCambiar={(categorias) => cambiar('categoriasInteres', categorias)}
        error={error('categoriasInteres')} />

      <CasillaPoliticas id={ID('aceptoPoliticas')} marcada={datos.aceptoPoliticas}
        onCambiar={(valor) => cambiar('aceptoPoliticas', valor)} error={error('aceptoPoliticas')} />

      <button type="submit" className="boton-principal" disabled={enviando} aria-busy={enviando}>
        {enviando ? t('registro.enviando') : t('registro.botonTurista')}
      </button>
    </form>
  )
}

/**
 * «Acepto la Política de Privacidad y el tratamiento de mis datos» (RF01; para el prestador, WF-03 y E1 §3.2.7.4).
 * «Política de Privacidad» va al mismo destino que el pie de página (RUTA_POLITICA_PRIVACIDAD, la sección 4 de WF-15).
 * Se abre en una pestaña nueva para no perder lo escrito en el formulario (DEC-76), y lo anuncia a los lectores de
 * pantalla. Sin ruta (`rutaPolitica = null`) se muestra deshabilitada («No disponible»), igual que en el pie (DEC-270).
 */
export function CasillaPoliticas({
  id,
  marcada,
  onCambiar,
  error,
  rutaPolitica = RUTA_POLITICA_PRIVACIDAD,
}: {
  id: string
  marcada: boolean
  onCambiar: (valor: boolean) => void
  error?: string
  rutaPolitica?: string | null
}) {
  const { t } = useTranslation()
  const politica = rutaPolitica ? (
    <Link to={rutaPolitica} target="_blank" rel="noopener noreferrer" aria-describedby={`${id}-pestana`} />
  ) : (
    <span className="enlace-deshabilitado" />
  )
  return (
    <div className="registro__politicas">
      <label className="casilla">
        <input
          id={id}
          type="checkbox"
          checked={marcada}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          onChange={(e) => onCambiar(e.target.checked)}
        />
        <span>
          <Trans i18nKey="registro.aceptoPoliticas" components={{ politica }} />
          {!rutaPolitica && (
            <>
              {' '}
              <span className="solo-lector">({t('comun.noDisponible')})</span>
            </>
          )}
        </span>
      </label>
      {/* Fuera de la etiqueta: describe el enlace sin cambiar el nombre de la casilla. */}
      {rutaPolitica && (
        <span id={`${id}-pestana`} className="solo-lector">
          {t('registro.pestanaNueva')}
        </span>
      )}
      {error && (
        <p id={`${id}-error`} className="campo__error">
          {error}
        </p>
      )}
    </div>
  )
}
