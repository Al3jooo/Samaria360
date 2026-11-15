package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

/** ms-catalog no respondió: el asistente responde con la base de conocimientos y lo dice. */
public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
