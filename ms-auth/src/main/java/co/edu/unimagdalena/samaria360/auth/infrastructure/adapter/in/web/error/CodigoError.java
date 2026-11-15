package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.error;

/**
 * Valor del campo {@code codigo} de los ProblemDetail. Es estable: el frontend lo traduce a ES o EN
 * (RF03, RNF08), así que no se debe renombrar.
 */
public enum CodigoError {
    CREDENCIALES_INVALIDAS,
    CUENTA_BLOQUEADA,
    CUENTA_INACTIVA,
    /** Prestador Pendiente de Validación (RF02, decisión D3). */
    CUENTA_PENDIENTE_VALIDACION,
    /** Prestador Rechazado por el Administrador (RF28, decisión D3). */
    CUENTA_RECHAZADA,
    /** Registro de prestador con un RNT que ya existe (dato público, ADR-006). */
    RNT_YA_REGISTRADO,
    /** Registro sin aceptar la Política de Privacidad (turista y prestador). */
    POLITICAS_NO_ACEPTADAS,
    NO_AUTENTICADO,
    /** Cliente interno desconocido o secreto incorrecto al pedir un token de servicio (DEC-280). */
    CREDENCIALES_SERVICIO_INVALIDAS,
    /** Token de antes de un cambio de estado o de rol de la cuenta: su claim ver es menor que version_cuenta (DEC-29). */
    SESION_INVALIDADA,
    /** La sesión superó su duración máxima y no se renueva (DEC-317): hay que iniciar sesión de nuevo. */
    SESION_VENCIDA,
    /** La cuenta ya no está en el estado desde el que se pidió el cambio (DEC-39). */
    ESTADO_CAMBIADO,
    /** No existe un turista o prestador con ese id (administración de cuentas), o ninguna cuenta (cambio de rol). */
    CUENTA_NO_ENCONTRADA,
    /** La cuenta ya no tiene el rol desde el que se pidió el cambio (RF45, DEC-104); lleva {@code rolActual}. */
    ROL_CAMBIADO,
    /** La cuenta no admite el cambio de rol: Prestador, desactivada o sin rol base al revocar; lleva {@code motivo}. */
    CUENTA_NO_ELEGIBLE,
    /** El Administrador intentó cambiar el rol de su propia cuenta (DEC-82). */
    CAMBIO_ROL_PROPIO,
    /** El cambio dejaría el sistema sin Administradores activos (DEC-82). */
    ULTIMO_ADMINISTRADOR,
    /** Token de recuperación de contraseña inexistente, usado o vencido (RF34): un solo código para los tres casos. */
    TOKEN_INVALIDO,
    ACCESO_DENEGADO,
    DATOS_INVALIDOS,
    SOLICITUD_INVALIDA,
    ERROR_INTERNO
}
