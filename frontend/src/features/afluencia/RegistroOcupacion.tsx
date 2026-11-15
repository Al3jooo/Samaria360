import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../shared/api/httpClient'
import { OCUPACION_MAXIMA, registrarOcupacion, type Afluencia } from './afluenciaApi'
import './afluencia.css'

/**
 * «Registrar ocupación» del panel «Atractivos» del Gestor (WF-24; RF39, decisión 2 del equipo, DEC-306): las personas
 * que hay ahora en el atractivo. Responde con el nivel calculado sobre el límite de capacidad de carga (RF07).
 */
export function RegistroOcupacion({ id, nombre }: { id: string; nombre: string }) {
  const { t } = useTranslation()
  const idCampo = useId()
  const [valor, setValor] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<Afluencia | null>(null)
  const [error, setError] = useState<string | null>(null)

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    const numero = Number(valor)
    if (valor.trim() === '' || !Number.isInteger(numero) || numero < 0 || numero > OCUPACION_MAXIMA) {
      setError(t('afluencia.registro.invalida'))
      return
    }
    setEnviando(true)
    setError(null)
    try {
      setResultado(await registrarOcupacion(id, numero))
      setValor('')
    } catch (causa) {
      setError(
        causa instanceof ApiError && causa.codigo === 'DATOS_INVALIDOS'
          ? t('afluencia.registro.sinLimite')
          : t('afluencia.registro.error'),
      )
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="registro-ocupacion" noValidate onSubmit={(e) => void enviar(e)}>
      <label htmlFor={idCampo}>{t('afluencia.registro.campo', { nombre })}</label>
      <input
        id={idCampo}
        type="number"
        inputMode="numeric"
        min={0}
        max={OCUPACION_MAXIMA}
        value={valor}
        onChange={(e) => setValor(e.target.value)}
      />
      <button type="submit" className="boton-secundario" disabled={enviando}>
        {t('afluencia.registro.boton')}
      </button>
      {resultado && (
        <span role="status" className={`nivel-afluencia nivel-afluencia--${resultado.nivel}`}>
          {t('afluencia.registro.hecho', {
            nivel: t(`afluencia.niveles.${resultado.nivel}`),
            porcentaje: resultado.porcentaje,
          })}
        </span>
      )}
      {error && (
        <span role="alert" className="campo-error">
          {error}
        </span>
      )}
    </form>
  )
}
