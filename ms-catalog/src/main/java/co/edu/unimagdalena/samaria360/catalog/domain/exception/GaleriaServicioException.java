package co.edu.unimagdalena.samaria360.catalog.domain.exception;

/**
 * RF48 (DEC-313): la galería de un servicio aprobado siempre tiene de 1 a 5 imágenes. {@code codigo}:
 * {@code LIMITE_IMAGENES} (ya tiene 5), {@code IMAGEN_MINIMA} (es la única) o {@code IMAGEN_NO_ENCONTRADA} (no es de
 * la galería).
 */
public class GaleriaServicioException extends RuntimeException {

    public static final String LIMITE_IMAGENES = "LIMITE_IMAGENES";
    public static final String IMAGEN_MINIMA = "IMAGEN_MINIMA";

    private final String codigo;

    public GaleriaServicioException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
