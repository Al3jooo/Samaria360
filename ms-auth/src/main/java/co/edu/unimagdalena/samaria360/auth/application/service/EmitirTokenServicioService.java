package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.EmitirTokenServicioUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.TokenServicioEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.ClientesServicioPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenServicioPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CredencialesServicioInvalidasException;
import co.edu.unimagdalena.samaria360.auth.domain.model.ClienteServicio;

import java.util.Objects;

/** Credenciales de cliente → JWT de servicio (DEC-280). Un cliente desconocido y un secreto malo dan el mismo 401. */
public class EmitirTokenServicioService implements EmitirTokenServicioUseCase {

    private final ClientesServicioPort clientes;
    private final TokenServicioPort tokens;
    private final RelojPort reloj;

    public EmitirTokenServicioService(ClientesServicioPort clientes, TokenServicioPort tokens, RelojPort reloj) {
        this.clientes = Objects.requireNonNull(clientes);
        this.tokens = Objects.requireNonNull(tokens);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public TokenServicioEmitido emitir(String cliente, String secreto) {
        ClienteServicio registrado = clientes.buscar(cliente == null ? "" : cliente)
                .filter(c -> c.acepta(secreto))
                .orElseThrow(CredencialesServicioInvalidasException::new);
        TokenEmitido emitido = tokens.emitirParaCliente(registrado.nombre(), reloj.ahora());
        return new TokenServicioEmitido(emitido.token(), emitido.expiraEn());
    }
}
