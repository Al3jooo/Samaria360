package co.edu.unimagdalena.samaria360.auth.domain.model;

/**
 * Destino de una entrega del outbox. {@code MS_CATALOG} y {@code MS_BOOKING} mantienen una proyección de las cuentas y la
 * reciben por HTTP (ADR-008; el texto es el valor de {@code aud} y de la columna). {@code RABBITMQ} es la publicación en
 * el exchange de eventos (ADR-011, DEC-284).
 */
public enum Destino {
    MS_CATALOG("ms-catalog"),
    MS_BOOKING("ms-booking"),
    RABBITMQ("rabbitmq");

    private final String texto;

    Destino(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }

    /** Los que reciben el evento por el relevo HTTP de ADR-008. */
    public boolean esHttp() {
        return this != RABBITMQ;
    }

    public static Destino desdeTexto(String texto) {
        for (Destino destino : values()) {
            if (destino.texto.equals(texto)) {
                return destino;
            }
        }
        throw new IllegalArgumentException("Destino desconocido: " + texto);
    }
}
