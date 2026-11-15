package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.EnviarCorreosPendientesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ContactoPrestadorPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPendienteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ServicioExternoNoDisponibleException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ContactoPrestador;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CorreoPendiente;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RF30: envía los correos encolados al Prestador por una reserva nueva (DEC-292). Pide el contacto a ms-auth en el
 * momento (DEC-280) y lo usa sin guardarlo. Si ms-auth o el SMTP fallan, reintenta con espera creciente (2 s a 1 h);
 * si ms-auth ya no conoce al prestador, lo da por atendido (no hay a quién escribir).
 */
public class EnviarCorreosPendientesService implements EnviarCorreosPendientesUseCase {

    static final Duration ARRIENDO = Duration.ofMinutes(2);
    static final int LOTE = 20;
    static final Duration TOPE_ESPERA = Duration.ofHours(1);

    private final CorreoPendienteRepositoryPort pendientes;
    private final ContactoPrestadorPort contactos;
    private final CorreoPort correo;
    private final RelojPort reloj;

    public EnviarCorreosPendientesService(CorreoPendienteRepositoryPort pendientes, ContactoPrestadorPort contactos,
                                          CorreoPort correo, RelojPort reloj) {
        this.pendientes = Objects.requireNonNull(pendientes);
        this.contactos = Objects.requireNonNull(contactos);
        this.correo = Objects.requireNonNull(correo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public int enviar() {
        List<CorreoPendiente> reclamados = pendientes.reclamar(reloj.ahora(), ARRIENDO, LOTE);
        int enviados = 0;
        for (CorreoPendiente pendiente : reclamados) {
            try {
                Optional<ContactoPrestador> contacto = contactos.buscar(pendiente.idPrestador());
                if (contacto.isPresent()) {
                    correo.enviarNuevaReserva(contacto.get(), pendiente.datos());
                    enviados++;
                }
                pendientes.marcarEnviado(pendiente, reloj.ahora());
            } catch (ServicioExternoNoDisponibleException fallo) {
                pendientes.registrarFallo(pendiente, reloj.ahora().plus(espera(pendiente.intentos() + 1)),
                        fallo.getMessage());
            }
        }
        return enviados;
    }

    /** 2 s, 4 s, 8 s… con tope de una hora. */
    static Duration espera(int intento) {
        if (intento >= 12) {
            return TOPE_ESPERA;
        }
        Duration espera = Duration.ofSeconds(2L << (intento - 1));
        return espera.compareTo(TOPE_ESPERA) > 0 ? TOPE_ESPERA : espera;
    }

}
