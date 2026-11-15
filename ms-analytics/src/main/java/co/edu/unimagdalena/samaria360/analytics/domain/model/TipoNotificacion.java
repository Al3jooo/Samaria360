package co.edu.unimagdalena.samaria360.analytics.domain.model;

/**
 * Notificaciones internas de RF63: al Turista, «cambios en el estado de sus reservas»; al Prestador, «nuevas reservas
 * recibidas». El texto es el valor de {@code notificaciones.tipo}; la interfaz lo traduce con {@code datos} (RF03).
 */
public enum TipoNotificacion {
    /** Al Turista: su reserva quedó Confirmada (RF20). */
    RESERVA_CONFIRMADA,
    /** Al Turista: su reserva quedó Cancelada (RF18). */
    RESERVA_CANCELADA,
    /** Al Turista: su reserva quedó Completada (RF52); ya puede calificar el servicio (RF17). */
    RESERVA_COMPLETADA,
    /** Al Prestador: recibió una reserva nueva (RF30, RF63). */
    NUEVA_RESERVA
}
