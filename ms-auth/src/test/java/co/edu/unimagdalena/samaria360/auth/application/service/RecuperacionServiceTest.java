package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.NotificadorCuentaFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.OutboxEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.PasswordHasherFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.RecuperacionRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRecuperacion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRestablecimiento;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.TokenRecuperacionInvalidoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.TokenRecuperacion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF34 con dobles de los puertos (DEC-285): pedir el enlace y restablecer la contraseña. */
class RecuperacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000034");
    private static final String TOKEN = "a".repeat(43);

    private final RecuperacionRepositoryEnMemoria recuperaciones = new RecuperacionRepositoryEnMemoria()
            .conCuenta("ana@x.com", ID, Rol.Turista, 4);
    private final NotificadorCuentaFalso notificador = new NotificadorCuentaFalso();
    private final OutboxEnMemoria outbox = new OutboxEnMemoria();
    private final PasswordHasherFalso hasher = new PasswordHasherFalso();
    private final AtomicReference<Instant> ahora = new AtomicReference<>(AHORA);
    private final RelojPort reloj = ahora::get;
    private final SolicitarRecuperacionService solicitar =
            new SolicitarRecuperacionService(recuperaciones, () -> TOKEN, notificador, reloj);
    private final RestablecerContrasenaService restablecer =
            new RestablecerContrasenaService(recuperaciones, hasher, outbox, reloj);

    @Test
    void conUnaCuentaGuardaSoloElHashQueVenceALos30MinutosYEnviaElTokenEnClaro() {
        solicitar.solicitar(new ComandoRecuperacion(" ANA@x.com ", "en-US"));

        assertThat(recuperaciones.tokens()).singleElement().satisfies(t -> {
            assertThat(t.idUsuario).isEqualTo(ID);
            assertThat(t.hash).isEqualTo(TokenRecuperacion.hash(TOKEN)).isNotEqualTo(TOKEN);
            assertThat(t.expiraEn).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        });
        assertThat(notificador.recuperaciones()).containsExactly(
                new NotificadorCuentaFalso.Recuperacion(ID, "ana@x.com", TOKEN, Idioma.en));
    }

    /** ADR-006: sin cuenta no pasa nada y quien llama responde lo mismo. */
    @Test
    void sinCuentaNoGuardaNiEnviaNada() {
        solicitar.solicitar(new ComandoRecuperacion("nadie@x.com", "es"));

        assertThat(recuperaciones.tokens()).isEmpty();
        assertThat(notificador.recuperaciones()).isEmpty();
    }

    @Test
    void unCorreoMalFormadoEsDatosInvalidos() {
        assertThatThrownBy(() -> solicitar.solicitar(new ComandoRecuperacion("sin-arroba", null)))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("correo", MotivoInvalido.FORMATO));
    }

    @Test
    void unPedidoNuevoAnulaElEnlaceAnterior() {
        solicitar.solicitar(new ComandoRecuperacion("ana@x.com", null));
        solicitar.solicitar(new ComandoRecuperacion("ana@x.com", null));

        assertThat(recuperaciones.tokens()).extracting(t -> t.usado).containsExactly(true, false);
    }

    @Test
    void restablecerCambiaLaContrasenaSubeLaVersionYEncolaElEventoParaTodosLosDestinos() {
        solicitar.solicitar(new ComandoRecuperacion("ana@x.com", null));

        restablecer.restablecer(new ComandoRestablecimiento(TOKEN, "ClaveNueva1"));

        assertThat(recuperaciones.contrasenaDe(ID)).contains(PasswordHasherFalso.hashDe("ClaveNueva1"));
        EventoCuentaActualizada evento = outbox.eventos().get(0);
        assertThat(evento.idUsuario()).isEqualTo(ID);
        assertThat(evento.version()).isEqualTo(5);
        assertThat(outbox.entregas()).extracting(e -> e.destino)
                .containsExactlyInAnyOrder(Destino.MS_CATALOG, Destino.MS_BOOKING);
        assertThat(outbox.publicaciones()).hasSize(1);
    }

    @Test
    void elEnlaceSirveUnaSolaVez() {
        solicitar.solicitar(new ComandoRecuperacion("ana@x.com", null));
        restablecer.restablecer(new ComandoRestablecimiento(TOKEN, "ClaveNueva1"));

        assertThatThrownBy(() -> restablecer.restablecer(new ComandoRestablecimiento(TOKEN, "OtraClave2")))
                .isInstanceOf(TokenRecuperacionInvalidoException.class);
    }

    @Test
    void unEnlaceVencidoNoSirve() {
        solicitar.solicitar(new ComandoRecuperacion("ana@x.com", null));
        ahora.set(AHORA.plus(Duration.ofMinutes(30)));

        assertThatThrownBy(() -> restablecer.restablecer(new ComandoRestablecimiento(TOKEN, "ClaveNueva1")))
                .isInstanceOf(TokenRecuperacionInvalidoException.class);
        assertThat(outbox.eventos()).isEmpty();
    }

    /** DEC-285: una contraseña inválida no gasta el enlace. */
    @Test
    void unaContrasenaCortaNoConsumeElToken() {
        solicitar.solicitar(new ComandoRecuperacion("ana@x.com", null));

        assertThatThrownBy(() -> restablecer.restablecer(new ComandoRestablecimiento(TOKEN, "corta")))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("contrasena", MotivoInvalido.LONGITUD_MINIMA));
        assertThat(recuperaciones.tokens().get(0).usado).isFalse();
    }

    @Test
    void unTokenSinFormaSeRechazaSinConsultar() {
        assertThatThrownBy(() -> restablecer.restablecer(new ComandoRestablecimiento("../x", "ClaveNueva1")))
                .isInstanceOf(TokenRecuperacionInvalidoException.class);
        assertThat(hasher.codificadas()).isEmpty();
    }
}
