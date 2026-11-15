package co.edu.unimagdalena.samaria360.auth.application.port.out;

/** Publicación de un evento del outbox en RabbitMQ, con confirmación del bróker (ADR-011, punto 4). */
public interface PublicadorEventosPort {

    /** @throws EntregaFallidaException si el bróker no confirma el mensaje, lo devuelve o no responde a tiempo */
    void publicar(PublicacionPendiente publicacion);
}
