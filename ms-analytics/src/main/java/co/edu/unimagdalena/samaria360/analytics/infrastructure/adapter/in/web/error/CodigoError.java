package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error;

/** Códigos estables de los ProblemDetail de ms-analytics (contrato en {@code docs/sad/api/ms-analytics.md}). */
public enum CodigoError {
    NO_AUTENTICADO,
    /** Token de antes de un cambio de la cuenta: su claim ver es menor que la versión conocida (DEC-29, DEC-110). */
    SESION_INVALIDADA,
    ACCESO_DENEGADO,
    DATOS_INVALIDOS,
    /** La notificación no existe o no es del usuario autenticado (RF63). */
    NOTIFICACION_NO_ENCONTRADA,
    /** RF17 (decisión 1): una calificación por turista y entidad, sin edición. */
    CALIFICACION_DUPLICADA,
    /** RF17: un servicio se califica solo con una reserva Completada. */
    SIN_RESERVA_COMPLETADA,
    /** RF17, RF57: la cuenta del turista no está Activa. */
    CUENTA_INACTIVA,
    CALIFICACION_NO_ENCONTRADA,
    /** RF57 (DEC-301): ya hay un reporte pendiente del turista para la entidad. */
    REPORTE_DUPLICADO,
    REPORTE_NO_ENCONTRADO,
    /** RF29, RF57: la calificación o el reporte ya cambió de estado; lleva {@code estadoActual}. */
    ESTADO_CAMBIADO,
    ERROR_INTERNO
}
