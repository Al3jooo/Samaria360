package co.edu.unimagdalena.samaria360.chatbot.domain.exception;

/**
 * Un tope del asistente (DEC-297, decisión 5 del equipo): 20 mensajes por turista al día ({@code LIMITE_MENSAJES}, 429)
 * o 20 itinerarios guardados ({@code LIMITE_ITINERARIOS}, 409).
 */
public class LimiteSuperadoException extends RuntimeException {

    private final String codigo;

    public LimiteSuperadoException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
