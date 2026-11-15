package co.edu.unimagdalena.samaria360.auth.application.fake;

import co.edu.unimagdalena.samaria360.auth.application.port.out.NotificadorCuentaPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Idioma;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificadorCuentaFalso implements NotificadorCuentaPort {

    /** Correo de recuperación enviado (RF34): el token en claro solo existe aquí y en el correo. */
    public record Recuperacion(UUID idUsuario, String correo, String token, Idioma idioma) {
    }

    private final List<UUID> avisados = new ArrayList<>();
    private final List<String> correosAvisados = new ArrayList<>();
    private final List<Recuperacion> recuperaciones = new ArrayList<>();

    @Override
    public void avisarRegistroConCorreoExistente(UUID idUsuario, String correo) {
        avisados.add(idUsuario);
        correosAvisados.add(correo);
    }

    @Override
    public void enviarRecuperacion(UUID idUsuario, String correo, String token, Idioma idioma) {
        recuperaciones.add(new Recuperacion(idUsuario, correo, token, idioma));
    }

    public List<UUID> avisados() {
        return List.copyOf(avisados);
    }

    public List<String> correosAvisados() {
        return List.copyOf(correosAvisados);
    }

    public List<Recuperacion> recuperaciones() {
        return List.copyOf(recuperaciones);
    }
}
