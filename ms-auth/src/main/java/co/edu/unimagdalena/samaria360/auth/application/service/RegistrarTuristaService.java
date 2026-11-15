package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRegistroTurista;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RegistrarTuristaUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RegistroRecibido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.NotificadorCuentaPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PasswordHasherPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RegistroRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TuristaNuevo;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.RegistroTurista;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso Registrarse, turista (RF01). Orden (ADR-006): validación de formato y políticas (solo dependen de lo
 * enviado), hash BCrypt siempre (el tiempo no delata un correo existente) y, al final, el correo. La transacción la abre
 * la infraestructura.
 */
public class RegistrarTuristaService implements RegistrarTuristaUseCase {

    private final RegistroRepositoryPort registros;
    private final PasswordHasherPort hasher;
    private final NotificadorCuentaPort notificador;
    private final RelojPort reloj;
    private final OutboxPort outbox;

    public RegistrarTuristaService(RegistroRepositoryPort registros, PasswordHasherPort hasher,
                                   NotificadorCuentaPort notificador, RelojPort reloj, OutboxPort outbox) {
        this.outbox = Objects.requireNonNull(outbox);
        this.registros = Objects.requireNonNull(registros);
        this.hasher = Objects.requireNonNull(hasher);
        this.notificador = Objects.requireNonNull(notificador);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public RegistroRecibido registrar(ComandoRegistroTurista c) {
        RegistroTurista datos = RegistroTurista.validar(c.primerNombre(), c.segundoNombre(), c.primerApellido(),
                c.segundoApellido(), c.correo(), c.contrasena(), c.paisOrigen(), c.categoriasInteres(),
                c.aceptoPoliticas());
        String hash = hasher.codificar(datos.contrasena().valor());
        if (!RegistroComun.atenderCorreoExistente(registros, notificador, datos.correo().valor())) {
            UUID id = UUID.randomUUID();
            registros.registrarTurista(new TuristaNuevo(id, datos, hash, RegistroComun.hoy(reloj)));
            // Solo para RabbitMQ: ms-analytics cuenta los turistas por país (RF14; ADR-011).
            outbox.registrar(EventoCuentaActualizada.registro(id, Rol.Turista, EstadoAdministrativo.Activo, null,
                    reloj.ahora()), List.of(Destino.RABBITMQ));
        }
        return RegistroRecibido.INSTANCIA;
    }
}
