package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.CorreoPendiente;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Cola de correos de RF30 (colección técnica {@code correos_pendientes}, DEC-292). */
public interface CorreoPendienteRepositoryPort {

    void encolar(CorreoPendiente correo);

    /** Reclama hasta {@code limite} correos vencidos y les concede un arriendo hasta {@code ahora + arriendo}. */
    List<CorreoPendiente> reclamar(Instant ahora, Duration arriendo, int limite);

    void marcarEnviado(CorreoPendiente correo, Instant ahora);

    void registrarFallo(CorreoPendiente correo, Instant proximoIntento, String error);
}
