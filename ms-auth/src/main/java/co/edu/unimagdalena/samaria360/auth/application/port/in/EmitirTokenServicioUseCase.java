package co.edu.unimagdalena.samaria360.auth.application.port.in;

/** JWT de servicio para un cliente interno (DEC-280): {@code sub} = el cliente, {@code aud} = ms-auth, 5 minutos. */
public interface EmitirTokenServicioUseCase {

    TokenServicioEmitido emitir(String cliente, String secreto);
}
