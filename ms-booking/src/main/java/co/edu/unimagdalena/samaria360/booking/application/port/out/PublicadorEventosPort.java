package co.edu.unimagdalena.samaria360.booking.application.port.out;

/** Publicación de un evento del outbox en RabbitMQ, con confirmación del bróker (ADR-011, punto 4). */
public interface PublicadorEventosPort {

    /** @throws PublicacionFallidaException si el bróker no confirma, devuelve el mensaje o no responde a tiempo */
    void publicar(PublicacionPendiente publicacion);
}
