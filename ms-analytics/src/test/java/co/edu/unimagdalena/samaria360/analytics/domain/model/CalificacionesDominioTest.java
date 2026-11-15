package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF17, RF29 y RF57 en el dominio (decisión 1 del equipo, DEC-300, DEC-301). */
class CalificacionesDominioTest {

    private static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");
    private static final String SERVICIO = "6a0000000000000000000001";

    @Test
    void unaCalificacionNuevaEsVisibleConElComentarioRecortado() {
        Calificacion c = Calificacion.nueva(TipoEntidad.servicio, SERVICIO, UUID.randomUUID(), null, null, 5,
                "  Excelente  ", T0);

        assertThat(c.estado()).isEqualTo(Calificacion.Estado.Visible);
        assertThat(c.comentario()).isEqualTo("Excelente");
        assertThat(Calificacion.nueva(TipoEntidad.atractivo, SERVICIO, UUID.randomUUID(), null, null, 1, null, T0)
                .comentario()).isEmpty();
    }

    @Test
    void estrellasDe1A5ComentarioHasta500EIdObjectId() {
        assertThatThrownBy(() -> Calificacion.nueva(TipoEntidad.servicio, "x", UUID.randomUUID(), null, null, 6,
                "é".repeat(501), T0))
                .isInstanceOfSatisfying(CamposInvalidosException.class, e -> assertThat(e.getCampos())
                        .containsEntry("idEntidad", "FORMATO").containsEntry("estrellas", "VALOR_FUERA_DE_RANGO")
                        .containsEntry("comentario", "LONGITUD_MAXIMA"));
        assertThatThrownBy(() -> Calificacion.nueva(TipoEntidad.servicio, SERVICIO, UUID.randomUUID(), null, null,
                null, "", T0)).isInstanceOf(CamposInvalidosException.class);
        assertThat(Calificacion.nueva(TipoEntidad.servicio, SERVICIO, UUID.randomUUID(), null, null, 3,
                "é".repeat(500), T0).comentario()).hasSize(500);
    }

    @Test
    void elMotivoDeModeracionVaDe1A500() {
        assertThat(Calificacion.motivoValido("  Lenguaje ofensivo ")).isEqualTo("Lenguaje ofensivo");
        assertThatThrownBy(() -> Calificacion.motivoValido(" ")).isInstanceOf(CamposInvalidosException.class);
        assertThatThrownBy(() -> Calificacion.motivoValido("x".repeat(501)))
                .isInstanceOf(CamposInvalidosException.class);
    }

    @Test
    void elPromedioTieneDosDecimalesYEsNuloSinCalificaciones() {
        assertThat(PromedioCalificacion.de(TipoEntidad.servicio, SERVICIO, 13, 3).promedio())
                .isEqualByComparingTo(new BigDecimal("4.33"));
        assertThat(PromedioCalificacion.de(TipoEntidad.servicio, SERVICIO, 0, 0).promedio()).isNull();
        assertThatThrownBy(() -> new PromedioCalificacion(TipoEntidad.servicio, SERVICIO, null, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elEventoLlevaElPromedioYLaVersion() {
        EventoAnalitica evento = EventoAnalitica.promedioActualizado(
                PromedioCalificacion.de(TipoEntidad.atractivo, SERVICIO, 9, 2), 3, T0);

        assertThat(evento.tipo()).isEqualTo("PromedioActualizado");
        assertThat(evento.clave()).isEqualTo(SERVICIO);
        assertThat(evento.datos()).containsEntry("tipoEntidad", "atractivo").containsEntry("promedio", 4.5)
                .containsEntry("cantidad", 2L).containsEntry("version", 3L);
        assertThat(EventoAnalitica.promedioActualizado(PromedioCalificacion.de(TipoEntidad.atractivo, SERVICIO, 0, 0),
                4, T0).datos()).containsEntry("promedio", null);
    }

    @Test
    void unReporteUsaLaListaDeMotivosYUnaDescripcionOpcional() {
        Reporte r = Reporte.nuevo("Servicio", SERVICIO, UUID.randomUUID(), "PRECIO_INCORRECTO", null, T0);

        assertThat(r.tipoEntidad()).isEqualTo(TipoEntidad.servicio);
        assertThat(r.estado()).isEqualTo(Reporte.Estado.Pendiente);
        assertThat(r.descripcion()).isEmpty();
        assertThatThrownBy(() -> Reporte.nuevo("lugar", "x", UUID.randomUUID(), "SPAM", "x".repeat(501), T0))
                .isInstanceOfSatisfying(CamposInvalidosException.class, e -> assertThat(e.getCampos())
                        .containsEntry("tipoEntidad", "NO_PERMITIDO").containsEntry("idEntidad", "FORMATO")
                        .containsEntry("motivo", "NO_PERMITIDO").containsEntry("descripcion", "LONGITUD_MAXIMA"));
    }
}
