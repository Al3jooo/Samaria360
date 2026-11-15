package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.OutboxEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.port.out.EntregaFallidaException;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoRolCambiado;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaReintento;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Relevo del outbox hacia RabbitMQ (ADR-011) con dobles: todos los eventos, en orden, y reintento ante un fallo. */
class PublicarEventosServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-14T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");

    private final OutboxEnMemoria outbox = new OutboxEnMemoria();
    private final List<PublicacionPendiente> publicadas = new ArrayList<>();
    private boolean caido;
    private final PublicarEventosService relevo = new PublicarEventosService(outbox, publicacion -> {
        if (caido) {
            throw new EntregaFallidaException("RabbitMQ no disponible");
        }
        publicadas.add(publicacion);
    }, () -> T0, new PoliticaReintento());

    private void dosEventosDelMismoUsuario() {
        outbox.registrar(new EventoCuentaActualizada(USUARIO, Rol.Gestor, EstadoAdministrativo.Activo, 3, null, T0),
                List.of(Destino.values()));
        outbox.registrarPublicacion(new EventoRolCambiado(USUARIO, Rol.Turista, Rol.Gestor, ADMIN, T0));
    }

    /** A diferencia del relevo HTTP, ninguno reemplaza al anterior: la auditoría necesita todos. */
    @Test
    void publicaTodosLosEventosEnOrdenSinReemplazarLosDelMismoUsuario() {
        dosEventosDelMismoUsuario();

        assertThat(relevo.publicar()).isEqualTo(2);

        assertThat(publicadas).extracting(PublicacionPendiente::tipo)
                .containsExactly("CuentaActualizada", "RolCambiado");
        assertThat(outbox.publicaciones()).allSatisfy(p -> assertThat(p.publicadaEn).isEqualTo(T0));
        // El relevo HTTP no se toca: sus entregas siguen pendientes.
        assertThat(outbox.entregas()).allSatisfy(e -> assertThat(e.entregadoEn).isNull());
    }

    @Test
    void conRabbitMqCaidoProgramaElReintentoYNoMarcaNada() {
        dosEventosDelMismoUsuario();
        caido = true;

        assertThat(relevo.publicar()).isZero();

        OutboxEnMemoria.Publicacion primera = outbox.publicaciones().get(0);
        assertThat(primera.intentos).isEqualTo(1);
        assertThat(primera.proximoIntento).isEqualTo(T0.plusSeconds(2));
        assertThat(primera.ultimoError).contains("RabbitMQ no disponible");
        // La segunda vuelve a la cola sin contar un intento.
        assertThat(outbox.publicaciones().get(1).intentos).isZero();
        assertThat(outbox.publicaciones().get(1).proximoIntento).isEqualTo(T0);
    }

    @Test
    void sinPendientesNoPublicaNada() {
        assertThat(relevo.publicar()).isZero();
        assertThat(publicadas).isEmpty();
    }
}
