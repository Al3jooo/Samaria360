package co.edu.unimagdalena.samaria360.analytics.domain.model;

/**
 * Acciones críticas del log de auditoría (RF13: «aprobaciones, cancelaciones, cambios de roles»), más la moderación de
 * calificaciones (RF29, iteración H). El texto es el valor de {@code logs_auditoria.accion}.
 */
public enum AccionAuditada {
    PRESTADOR_APROBADO,
    PRESTADOR_RECHAZADO,
    SERVICIO_APROBADO,
    SERVICIO_RECHAZADO,
    ROL_CAMBIADO,
    RESERVA_CANCELADA,
    CALIFICACION_OCULTADA,
    CALIFICACION_MOSTRADA,
    CALIFICACION_ELIMINADA;

    public static AccionAuditada desdeTexto(String texto) {
        for (AccionAuditada accion : values()) {
            if (accion.name().equals(texto)) {
                return accion;
            }
        }
        throw new IllegalArgumentException("Acción desconocida: " + texto);
    }
}
