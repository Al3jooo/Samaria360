package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.in.web.error;

/**
 * Valor del campo {@code codigo} de los ProblemDetail (§4 del plan), con el mismo formato que ms-auth y ms-catalog. Es
 * estable: el frontend lo traduce (RF03), así que no se debe renombrar.
 */
public enum CodigoError {
    DATOS_INVALIDOS,
    IDEMPOTENCIA_REQUERIDA,
    NO_AUTENTICADO,
    /** Token de antes de un cambio de estado o de rol de la cuenta: su claim ver es menor que la versión conocida (DEC-29). */
    SESION_INVALIDADA,
    ACCESO_DENEGADO,
    OFERTA_NO_DISPONIBLE,
    UNIDAD_NO_DISPONIBLE,
    RESERVA_NO_ENCONTRADA,
    FECHA_NO_DISPONIBLE,
    CUPO_AGOTADO,
    PRECIO_CAMBIADO,
    CANCELACION_FUERA_DE_PLAZO,
    RESERVA_NO_CANCELABLE,
    IDEMPOTENCIA_CONFLICTO,
    SERVICIO_NO_DISPONIBLE,
    /** RF19 (DEC-310): los cupos nuevos bajan de lo reservado; lleva {@code reservados}. */
    CUPOS_RESERVADOS,
    /** RF19 (DEC-310): la fecha u horario tiene reservas confirmadas y no se cierra. */
    FECHA_CON_RESERVAS,
    SOLICITUD_INVALIDA,
    ERROR_INTERNO
}
