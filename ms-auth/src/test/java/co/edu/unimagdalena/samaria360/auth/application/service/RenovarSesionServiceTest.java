package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.auth.application.fake.TokenProviderFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.UsuarioRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.port.in.SesionIniciada;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaInactivaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.SesionVencidaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.UsuarioNoEncontradoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DEC-317: renovar la sesión conserva su inicio y no pasa de 8 horas. */
class RenovarSesionServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2030-03-01T15:00:00Z");

    private final UsuarioRepositoryEnMemoria usuarios = new UsuarioRepositoryEnMemoria();
    private final TokenProviderFalso tokens = new TokenProviderFalso();
    private final RenovarSesionService servicio = new RenovarSesionService(usuarios, tokens, new RelojFijo(AHORA));

    @Test
    void emiteUnTokenNuevoQueConservaElInicioDeLaSesion() {
        usuarios.agregar(usuario(EstadoCuenta.Activo));
        Instant inicio = AHORA.minus(Duration.ofHours(2));

        SesionIniciada sesion = servicio.renovar(ID, inicio);

        assertThat(sesion.idUsuario()).isEqualTo(ID);
        assertThat(sesion.rol()).isEqualTo(Rol.Turista);
        assertThat(sesion.expiraEn()).isEqualTo(AHORA.plus(TokenProviderFalso.VIGENCIA));
        assertThat(tokens.ultimoInicioSesion()).isEqualTo(inicio);
    }

    @Test
    void justoEnLaDuracionMaximaTodaviaSeRenuevaYDespuesNo() {
        usuarios.agregar(usuario(EstadoCuenta.Activo));

        assertThat(servicio.renovar(ID, AHORA.minus(RenovarSesionService.DURACION_MAXIMA))).isNotNull();
        assertThatThrownBy(() -> servicio.renovar(ID, AHORA.minus(RenovarSesionService.DURACION_MAXIMA).minusSeconds(1)))
                .isInstanceOf(SesionVencidaException.class);
        assertThat(tokens.llamadas()).isEqualTo(1);
    }

    @Test
    void unaCuentaQueDejoDeEstarActivaNoSeRenueva() {
        usuarios.agregar(usuario(EstadoCuenta.Desactivado));

        assertThatThrownBy(() -> servicio.renovar(ID, AHORA)).isInstanceOf(CuentaInactivaException.class);
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void unUsuarioQueYaNoExisteNoSeRenueva() {
        assertThatThrownBy(() -> servicio.renovar(ID, AHORA)).isInstanceOf(UsuarioNoEncontradoException.class);
    }

    private static Usuario usuario(EstadoCuenta estado) {
        return new Usuario(ID, "turista@samaria360.test", "$2a$10$hash", Rol.Turista, estado, 0, null);
    }
}
