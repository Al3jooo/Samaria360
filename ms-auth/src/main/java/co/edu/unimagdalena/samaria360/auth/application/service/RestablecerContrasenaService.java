package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRestablecimiento;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RestablecerContrasenaUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.out.CuentaRestablecida;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PasswordHasherPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RecuperacionRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.TokenRecuperacionInvalidoException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.ValorInvalidoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.ContrasenaNueva;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.TokenRecuperacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF34, segundo paso (DEC-285). La contraseña se valida antes de consumir el token: una contraseña inválida no lo gasta.
 * Con el token vigente, en la transacción de quien llama: lo marca usado, cambia la contraseña, quita el bloqueo de RF15,
 * sube {@code version_cuenta} y encola CuentaActualizada para todos los destinos, lo que cierra las sesiones abiertas
 * (ADR-008).
 */
public class RestablecerContrasenaService implements RestablecerContrasenaUseCase {

    private final RecuperacionRepositoryPort recuperaciones;
    private final PasswordHasherPort hasher;
    private final OutboxPort outbox;
    private final RelojPort reloj;

    public RestablecerContrasenaService(RecuperacionRepositoryPort recuperaciones, PasswordHasherPort hasher,
                                        OutboxPort outbox, RelojPort reloj) {
        this.recuperaciones = Objects.requireNonNull(recuperaciones);
        this.hasher = Objects.requireNonNull(hasher);
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public void restablecer(ComandoRestablecimiento comando) {
        ContrasenaNueva contrasena;
        try {
            contrasena = new ContrasenaNueva(comando.contrasena());
        } catch (ValorInvalidoException e) {
            throw new DatosRegistroInvalidosException(Map.of("contrasena", e.getMotivo()));
        }
        if (!TokenRecuperacion.tieneForma(comando.token())) {
            throw new TokenRecuperacionInvalidoException();
        }
        String hash = hasher.codificar(contrasena.valor());
        Instant ahora = reloj.ahora();
        UUID idUsuario = recuperaciones.consumirToken(TokenRecuperacion.hash(comando.token()), ahora)
                .orElseThrow(TokenRecuperacionInvalidoException::new);
        CuentaRestablecida cuenta = recuperaciones.restablecer(idUsuario, hash);
        outbox.registrar(new EventoCuentaActualizada(cuenta.idUsuario(), cuenta.rol(), cuenta.estado(),
                cuenta.versionNueva(), cuenta.rnt(), ahora), List.of(Destino.values()));
    }
}
