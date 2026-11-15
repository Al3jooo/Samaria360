package co.edu.unimagdalena.samaria360.chatbot.domain.exception;

/**
 * El itinerario (o la entrada de la base de conocimientos) no existe o no es del usuario: 404 con el {@code codigo}
 * indicado ({@code ITINERARIO_NO_ENCONTRADO} o {@code CONOCIMIENTO_NO_ENCONTRADO}).
 */
public class RecursoNoEncontradoException extends RuntimeException {

    private final String codigo;

    public RecursoNoEncontradoException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
