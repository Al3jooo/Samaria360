package co.edu.unimagdalena.samaria360.auth.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio de RF34 (token, idioma) y de los eventos que ms-auth publica en RabbitMQ (ADR-011). Java puro. */
class RecuperacionYEventosTest {

    private static final Instant T0 = Instant.parse("2026-11-14T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");

    @Test
    void elTokenVenceALos30Minutos() {
        assertThat(TokenRecuperacion.venceEn(T0)).isEqualTo(T0.plus(Duration.ofMinutes(30)));
    }

    /** SHA-256 conocido de «abc»: el hash es estable y nunca es el token. */
    @Test
    void elHashEsElSha256EnHexadecimal() {
        assertThat(TokenRecuperacion.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void soloAceptaLaFormaDeUnTokenGenerado() {
        assertThat(TokenRecuperacion.tieneForma("A".repeat(42) + "_")).isTrue();
        assertThat(TokenRecuperacion.tieneForma("A".repeat(42))).isFalse();
        assertThat(TokenRecuperacion.tieneForma("A".repeat(42) + "=")).isFalse();
        assertThat(TokenRecuperacion.tieneForma(null)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "es", "es-CO", "fr", "pt-BR"})
    void elIdiomaPorDefectoEsEspanol(String texto) {
        assertThat(Idioma.desdeTexto(texto)).isEqualTo(Idioma.es);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "EN", "en-US", " en-GB "})
    void reconoceElIngles(String texto) {
        assertThat(Idioma.desdeTexto(texto)).isEqualTo(Idioma.en);
    }

    @Test
    void laValidacionDeUnPrestadorLlevaSoloIdsYLaDecision() {
        EventoPrestadorValidado evento = new EventoPrestadorValidado(USUARIO, false, ADMIN, T0);

        assertThat(evento.tipo()).isEqualTo("PrestadorValidado");
        assertThat(evento.idUsuario()).isEqualTo(USUARIO);
        assertThat(evento.datos()).containsExactly(
                java.util.Map.entry("idPrestador", USUARIO.toString()),
                java.util.Map.entry("decision", "Rechazado"),
                java.util.Map.entry("idAdministrador", ADMIN.toString()));
    }

    @Test
    void elCambioDeRolLlevaElRolAnteriorYElNuevo() {
        EventoRolCambiado evento = EventoRolCambiado.de(new CambioRol(USUARIO, Rol.Turista, Rol.Gestor, 2), ADMIN, T0);

        assertThat(evento.tipo()).isEqualTo("RolCambiado");
        assertThat(evento.datos()).containsEntry("rolAnterior", "Turista").containsEntry("rolNuevo", "Gestor")
                .containsEntry("idAdministrador", ADMIN.toString());
    }

    @Test
    void unRegistroNuevoEsLaVersionCero() {
        EventoCuentaActualizada evento = EventoCuentaActualizada.registro(USUARIO, Rol.Turista,
                EstadoAdministrativo.Activo, null, T0);

        assertThat(evento.version()).isZero();
        assertThatThrownBy(() -> new EventoCuentaActualizada(USUARIO, Rol.Turista, EstadoAdministrativo.Activo, -1,
                null, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soloRabbitMqNoEsUnDestinoHttp() {
        assertThat(Destino.MS_CATALOG.esHttp()).isTrue();
        assertThat(Destino.MS_BOOKING.esHttp()).isTrue();
        assertThat(Destino.RABBITMQ.esHttp()).isFalse();
        assertThat(Destino.desdeTexto("rabbitmq")).isEqualTo(Destino.RABBITMQ);
    }
}
