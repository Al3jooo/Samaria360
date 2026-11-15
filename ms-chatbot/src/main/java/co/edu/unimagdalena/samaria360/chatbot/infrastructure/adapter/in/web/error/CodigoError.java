package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web.error;

/** Códigos estables de los ProblemDetail de ms-chatbot (contrato en {@code docs/sad/api/ms-chatbot.md}). */
public enum CodigoError {
    NO_AUTENTICADO,
    /** Token de antes de un cambio de la cuenta: su claim ver es menor que la versión conocida (DEC-110). */
    SESION_INVALIDADA,
    ACCESO_DENEGADO,
    DATOS_INVALIDOS,
    /** El turista ya envió 20 mensajes hoy (DEC-297). */
    LIMITE_MENSAJES,
    /** El turista ya tiene 20 itinerarios guardados (DEC-297). */
    LIMITE_ITINERARIOS,
    /** La base de conocimientos llegó a 500 entradas (DEC-297). */
    LIMITE_CONOCIMIENTO,
    ITINERARIO_NO_ENCONTRADO,
    CONOCIMIENTO_NO_ENCONTRADO,
    ERROR_INTERNO
}
