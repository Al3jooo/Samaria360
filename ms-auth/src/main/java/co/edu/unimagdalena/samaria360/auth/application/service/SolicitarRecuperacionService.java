package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRecuperacion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.SolicitarRecuperacionUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.out.GeneradorTokenPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.NotificadorCuentaPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RecuperacionRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.ValorInvalidoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.Correo;
import co.edu.unimagdalena.samaria360.auth.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.auth.domain.model.TokenRecuperacion;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RF34, primer paso. Si el correo tiene cuenta, guarda el hash de un token nuevo (vence a los 30 minutos y anula los
 * anteriores) y envía el enlace; si no, no hace nada. Quien llama responde lo mismo en los dos casos (ADR-006). Aplica a
 * cualquier cuenta: el inicio de sesión ya decide los 403 de una cuenta Pendiente, Rechazada o Inactiva (DEC-285).
 */
public class SolicitarRecuperacionService implements SolicitarRecuperacionUseCase {

    private final RecuperacionRepositoryPort recuperaciones;
    private final GeneradorTokenPort generador;
    private final NotificadorCuentaPort notificador;
    private final RelojPort reloj;

    public SolicitarRecuperacionService(RecuperacionRepositoryPort recuperaciones, GeneradorTokenPort generador,
                                        NotificadorCuentaPort notificador, RelojPort reloj) {
        this.recuperaciones = Objects.requireNonNull(recuperaciones);
        this.generador = Objects.requireNonNull(generador);
        this.notificador = Objects.requireNonNull(notificador);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public void solicitar(ComandoRecuperacion comando) {
        Correo correo;
        try {
            correo = new Correo(comando.correo());
        } catch (ValorInvalidoException e) {
            throw new DatosRegistroInvalidosException(Map.of("correo", e.getMotivo()));
        }
        Optional<UUID> idUsuario = recuperaciones.buscarIdPorCorreo(correo.valor());
        if (idUsuario.isEmpty()) {
            return;
        }
        String token = generador.nuevoToken();
        recuperaciones.guardarToken(idUsuario.get(), TokenRecuperacion.hash(token),
                TokenRecuperacion.venceEn(reloj.ahora()));
        notificador.enviarRecuperacion(idUsuario.get(), correo.valor(), token,
                Idioma.desdeTexto(comando.idioma()));
    }
}
