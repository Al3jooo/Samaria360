package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AfluenciaAtractivo;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AfluenciaDia;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AtractivoReporte;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.EntidadConsultada;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.PanelPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.TableroGeneral;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.VisitantesZona;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.TablerosRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoEntrante;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF14, RF31 y RF64 con un doble del repositorio (DEC-308). */
class TablerosServiceTest {

    /** 21:00 del 10 de marzo en Santa Marta (ya es 11 en UTC). */
    private static final Instant AHORA = Instant.parse("2030-03-11T02:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2030, 3, 10);
    private static final UUID PRESTADOR = UUID.randomUUID();

    private LocalDate desdeVisto;
    private LocalDate hastaVisto;

    private final TablerosRepositoryPort repositorio = new TablerosRepositoryPort() {
        @Override
        public Map<LocalDate, Long> reservasPorDia(LocalDate desde, LocalDate hasta) {
            desdeVisto = desde;
            hastaVisto = hasta;
            return Map.of(HOY, 3L, HOY.minusDays(2), 1L);
        }

        @Override
        public List<EntidadConsultada> masConsultadas(TipoEntidad tipo, LocalDate desde, LocalDate hasta, int limite) {
            return List.of(new EntidadConsultada("a1", "Playa Cristal", 40));
        }

        @Override
        public List<VisitantesZona> turistasPorPais() {
            return List.of(new VisitantesZona("CO", 5), new VisitantesZona(null, 1));
        }

        @Override
        public List<EntidadConsultada> consultasPorServicio(UUID prestador, LocalDate desde, LocalDate hasta) {
            desdeVisto = desde;
            return List.of(new EntidadConsultada("s1", "Tour", 12), new EntidadConsultada("s2", null, 3));
        }

        @Override
        public List<EntidadConsultada> reservasPorServicio(UUID prestador, LocalDate desde, LocalDate hasta) {
            return List.of(new EntidadConsultada("s2", "Hotel", 2), new EntidadConsultada("s3", "Kayak", 1));
        }

        @Override
        public List<AfluenciaAtractivo> afluenciaPorAtractivo(LocalDate desde, LocalDate hasta, String atractivo) {
            return List.of();
        }

        @Override
        public List<AfluenciaDia> afluenciaPorDia(LocalDate desde, LocalDate hasta, String atractivo) {
            return List.of();
        }

        @Override
        public List<AtractivoReporte> atractivosConLecturas() {
            return List.of(new AtractivoReporte("6a0000000000000000000001", "Playa Cristal"));
        }
    };

    private final TablerosService servicio = new TablerosService(repositorio, () -> AHORA);

    @Test
    void porDefectoSonLosUltimos30DiasDeSantaMartaConLosDiasSinReservasEnCero() {
        TableroGeneral tablero = servicio.general(null, null);

        assertThat(hastaVisto).isEqualTo(HOY);
        assertThat(desdeVisto).isEqualTo(HOY.minusDays(29));
        assertThat(tablero.reservasPorDia()).hasSize(30);
        assertThat(tablero.reservasPorDia().get(29).reservas()).isEqualTo(3);
        assertThat(tablero.reservasPorDia().get(28).reservas()).isZero();
        assertThat(tablero.totalReservas()).isEqualTo(4);
        assertThat(tablero.atractivosMasConsultados()).singleElement()
                .satisfies(a -> assertThat(a.nombre()).isEqualTo("Playa Cristal"));
    }

    @Test
    void elRangoSeValida() {
        assertThatThrownBy(() -> servicio.general("2030-03-10", "2030-03-01"))
                .isInstanceOfSatisfying(CamposInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("hasta", "NO_PERMITIDO"));
        assertThatThrownBy(() -> servicio.general("2029-01-01", "2030-03-01"))
                .isInstanceOfSatisfying(CamposInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("desde", "VALOR_FUERA_DE_RANGO"));
        assertThatThrownBy(() -> servicio.general("ayer", null)).isInstanceOf(CamposInvalidosException.class);
        assertThatThrownBy(() -> servicio.afluencia(null, null, "x")).isInstanceOf(CamposInvalidosException.class);
    }

    @Test
    void elPanelDelPrestadorUneConsultasYReservasEn7O30Dias() {
        PanelPrestador panel = servicio.prestador(PRESTADOR, 30);

        assertThat(desdeVisto).isEqualTo(HOY.minusDays(29));
        assertThat(panel.servicios()).extracting(s -> s.idServicio() + ":" + s.nombre() + ":" + s.consultas() + ":"
                + s.reservas()).containsExactly("s1:Tour:12:0", "s2:Hotel:3:2", "s3:Kayak:0:1");
        assertThat(servicio.prestador(PRESTADOR, null).dias()).isEqualTo(7);
        assertThatThrownBy(() -> servicio.prestador(PRESTADOR, 15)).isInstanceOf(CamposInvalidosException.class);
    }

    @Test
    void consultasAgregadasSeLeePorEntidadYDiaDeSantaMarta() {
        UUID prestador = UUID.randomUUID();
        EventoEntrante evento = new EventoEntrante(UUID.randomUUID(), "ConsultasAgregadas", "ms-catalog", AHORA,
                "x", Map.of("ventanaInicio", "2030-03-11T02:00:00Z", "ventanaFin", "2030-03-11T02:01:00Z",
                "conteos", List.of(Map.of("tipoEntidad", "servicio", "idEntidad", "6a0000000000000000000001",
                                "nombre", "Tour", "idPrestador", prestador.toString(), "consultas", 4),
                        Map.of("tipoEntidad", "atractivo", "idEntidad", "6a0000000000000000000002", "consultas", 1))));

        List<ConsultasFicha> fichas = ConsultasFicha.de(evento);

        assertThat(fichas).hasSize(2);
        assertThat(fichas.get(0).dia()).isEqualTo(HOY);
        assertThat(fichas.get(0).idPrestador()).isEqualTo(prestador);
        assertThat(fichas.get(1).idPrestador()).isNull();
        assertThatThrownBy(() -> ConsultasFicha.de(new EventoEntrante(UUID.randomUUID(), "ConsultasAgregadas",
                "ms-catalog", AHORA, "x", Map.of("ventanaInicio", "2030-03-11T02:00:00Z", "conteos", List.of()))))
                .isInstanceOf(EventoInvalidoException.class);
    }
}
