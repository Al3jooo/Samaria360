package co.edu.unimagdalena.samaria360.catalog.application.port.in;

import java.util.UUID;

/**
 * Consultas a las fichas públicas agregadas por minuto (DEC-283, DEC-307): el detalle de un servicio o de un atractivo
 * cuenta cada respuesta 200 en memoria y un programador publica {@code ConsultasAgregadas} cada minuto (RF31, RF14).
 */
public interface ConsultasFichasUseCase {

    /** {@code tipoEntidad}: {@code servicio} o {@code atractivo}; {@code idPrestador}, solo en servicios. */
    void registrar(String tipoEntidad, String idEntidad, String nombre, UUID idPrestador);

    /** Encola los conteos de la ventana que termina ahora; devuelve cuántos eventos encoló. */
    int publicar();
}
