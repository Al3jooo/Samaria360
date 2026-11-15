package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.eventos;

import co.edu.unimagdalena.samaria360.booking.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.service.PublicarEventosServiceTest;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxModificacionesPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.ModificacionOferta;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
import co.edu.unimagdalena.samaria360.booking.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox de reservas_db (V7) y relevo a RabbitMQ (ADR-011, DEC-291) contra PostgreSQL y RabbitMQ reales: el evento llega
 * a la cola de ms-analytics, cumple eventos.schema.json y no lleva datos personales.
 */
@PruebaIntegracion
class EventosRabbitIntegracionTest {

    @Autowired
    private OutboxEventosPort outbox;

    @Autowired
    private PublicarEventosUseCase relevo;

    @Autowired
    private OutboxModificacionesPort modificaciones;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private ObjectMapper json;

    @Test
    void losTresEventosLleganALaColaDeAnalyticsYCumplenElContrato() throws Exception {
        Reserva reserva = PublicarEventosServiceTest.reserva();
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            outbox.registrar(EventoReserva.confirmada(reserva, PublicarEventosServiceTest.T0));
            outbox.registrar(EventoReserva.cancelada(reserva, reserva.idTurista(), PublicarEventosServiceTest.T0));
            outbox.registrar(EventoReserva.completada(reserva, PublicarEventosServiceTest.T0));
        });

        while (relevo.publicar() > 0) {
            // drena el outbox
        }

        JsonSchema esquema;
        try (InputStream entrada = Files.newInputStream(Path.of("../docs/sad/api/eventos.schema.json"))) {
            esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
        }
        List<JsonNode> recibidos = new ArrayList<>();
        Message mensaje;
        while ((mensaje = rabbit.receive("ms-analytics.eventos.v1", 2000)) != null) {
            JsonNode evento = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
            if (reserva.idReserva().toString().equals(evento.get("clave").asText())) {
                recibidos.add(evento);
            }
        }
        assertThat(recibidos).extracting(e -> e.get("tipo").asText())
                .containsExactly("ReservaConfirmada", "ReservaCancelada", "ReservaCompletada");
        assertThat(recibidos).allSatisfy(evento -> {
            assertThat(esquema.validate(evento)).isEmpty();
            assertThat(evento.get("origen").asText()).isEqualTo("ms-booking");
            assertThat(evento.toString()).doesNotContain("montoTotal").doesNotContain("hash");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE clave = ? AND publicado_en IS NULL",
                Integer.class, reserva.idReserva().toString())).isZero();
    }

    /** RF19 (DEC-310): OfertaModificada llega a la cola de ms-catalog (booking.oferta.*) y cumple el contrato. */
    @Test
    void laModificacionDeOfertaLlegaALaColaDelCatalogoYCumpleElContrato() throws Exception {
        String servicio = String.format("%024x", System.nanoTime());
        UUID prestador = UUID.randomUUID();
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            modificaciones.registrar(new ModificacionOferta(servicio, prestador, ModificacionOferta.Tipo.Cupos,
                    LocalDate.of(2030, 3, 2), LocalTime.of(8, 0), null, "12", "8", null, null,
                    PublicarEventosServiceTest.T0));
            modificaciones.registrar(new ModificacionOferta(servicio, prestador, ModificacionOferta.Tipo.Plantilla,
                    null, null, null, "Sabado · 08:00", "Lunes · 07:00", List.of("Lunes"), List.of("07:00"),
                    PublicarEventosServiceTest.T0));
        });

        while (relevo.publicar() > 0) {
            // drena el outbox
        }

        JsonSchema esquema;
        try (InputStream entrada = Files.newInputStream(Path.of("../docs/sad/api/eventos.schema.json"))) {
            esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
        }
        List<JsonNode> recibidos = new ArrayList<>();
        Message mensaje;
        while ((mensaje = rabbit.receive("ms-catalog.eventos.v1", 2000)) != null) {
            JsonNode evento = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
            if (servicio.equals(evento.get("clave").asText())) {
                recibidos.add(evento);
            }
        }
        assertThat(recibidos).extracting(e -> e.path("datos").path("tipo").asText())
                .containsExactly("Cupos", "Plantilla");
        assertThat(recibidos).allSatisfy(evento -> {
            assertThat(esquema.validate(evento)).isEmpty();
            assertThat(evento.get("tipo").asText()).isEqualTo("OfertaModificada");
        });
    }

    /** MANDATORY: sin la transacción de la reserva no se escribe el evento. */
    @Test
    void sinTransaccionNoSeRegistraElEvento() {
        Reserva reserva = PublicarEventosServiceTest.reserva();

        assertThatThrownBy(() -> outbox.registrar(EventoReserva.confirmada(reserva, PublicarEventosServiceTest.T0)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
