package co.edu.unimagdalena.samaria360.auth.domain.exception;

import java.util.Optional;

/**
 * El Administrador rechazó la validación del prestador (RF28; decisión D3). Desde DEC-273 lleva la justificación del
 * rechazo ({@code prestador.motivo_rechazo}, V11), que el 403 entrega al prestador: solo llega aquí con la contraseña
 * correcta, así que nadie más puede leerla.
 */
public class CuentaRechazadaException extends InicioSesionException {

    private final String motivo;

    public CuentaRechazadaException() {
        this(null);
    }

    public CuentaRechazadaException(String motivo) {
        super("La solicitud de la cuenta fue rechazada por el Administrador");
        this.motivo = motivo;
    }

    public Optional<String> getMotivo() {
        return Optional.ofNullable(motivo);
    }
}
