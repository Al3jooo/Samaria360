package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EventoCatalogo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** DEC-283 y DEC-307: conteos en memoria y un evento por ventana, con lotes de hasta 1000 entidades. */
class ConsultasFichasServiceTest {

    private static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");

    static final class Outbox implements OutboxEventosPort {
        final List<EventoCatalogo> eventos = new ArrayList<>();

        @Override
        public void registrar(EventoCatalogo evento) {
            eventos.add(evento);
        }

        @Override
        public List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
            return List.of();
        }

        @Override
        public void marcarPublicado(PublicacionPendiente publicacion, Instant ahora) {
        }

        @Override
        public void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error) {
        }

        @Override
        public void liberar(List<PublicacionPendiente> publicaciones, Instant ahora) {
        }

        @Override
        public int purgarPublicados(Instant limite) {
            return 0;
        }
    }

    private final Outbox outbox = new Outbox();
    private final RelojFijo reloj = new RelojFijo(T0);
    private final ConsultasFichasService servicio = new ConsultasFichasService(outbox, reloj);

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> conteos(EventoCatalogo evento) {
        return (List<Map<String, Object>>) evento.datos().get("conteos");
    }

    @Test
    void cuentaPorEntidadYPublicaUnEventoPorVentana() {
        UUID prestador = UUID.randomUUID();
        servicio.registrar("servicio", "6a0000000000000000000001", "Tour Tayrona", prestador);
        servicio.registrar("servicio", "6a0000000000000000000001", "Tour Tayrona", prestador);
        servicio.registrar("atractivo", "6a0000000000000000000002", "Playa Cristal", prestador);
        reloj.ajustar(T0.plusSeconds(60));

        assertThat(servicio.publicar()).isEqualTo(1);

        EventoCatalogo evento = outbox.eventos.get(0);
        assertThat(evento.tipo()).isEqualTo("ConsultasAgregadas");
        assertThat(evento.datos()).containsEntry("ventanaInicio", T0.toString())
                .containsEntry("ventanaFin", T0.plusSeconds(60).toString());
        assertThat(conteos(evento)).containsExactlyInAnyOrder(
                Map.of("tipoEntidad", "servicio", "idEntidad", "6a0000000000000000000001", "nombre", "Tour Tayrona",
                        "idPrestador", prestador.toString(), "consultas", 2L),
                Map.of("tipoEntidad", "atractivo", "idEntidad", "6a0000000000000000000002", "nombre", "Playa Cristal",
                        "consultas", 1L));
        // Sin consultas en la ventana siguiente, no hay evento; la ventana avanza.
        reloj.ajustar(T0.plusSeconds(120));
        assertThat(servicio.publicar()).isZero();
        servicio.registrar("atractivo", "6a0000000000000000000002", null, null);
        reloj.ajustar(T0.plusSeconds(180));
        servicio.publicar();
        assertThat(outbox.eventos.get(1).datos()).containsEntry("ventanaInicio", T0.plusSeconds(120).toString());
    }

    @Test
    void masDeMilEntidadesVanEnVariosEventos() {
        for (int i = 0; i < 1001; i++) {
            servicio.registrar("atractivo", String.format("%024x", i), null, null);
        }

        assertThat(servicio.publicar()).isEqualTo(2);
        assertThat(conteos(outbox.eventos.get(0))).hasSize(1000);
        assertThat(conteos(outbox.eventos.get(1))).hasSize(1);
    }
}
