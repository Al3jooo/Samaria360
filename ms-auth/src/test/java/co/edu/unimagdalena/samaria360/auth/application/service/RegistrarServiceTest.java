package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.NotificadorCuentaFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.OutboxEnMemoria;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.application.fake.PasswordHasherFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.RegistroRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRegistroPrestador;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRegistroTurista;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RegistroRecibido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PrestadorNuevo;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TuristaNuevo;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.PoliticasNoAceptadasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.RntYaRegistradoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.CategoriaInteres;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos de uso Registrarse (RF01, RF02) con dobles de los puertos. Comprueba el orden de ADR-006: formato y políticas,
 * hash BCrypt siempre, RNT (prestador) y, al final, el correo.
 */
class RegistrarServiceTest {

    /** 04:00 UTC del 12 es todavía el 11 en Santa Marta (UTC−5): la fecha de registro es la local. */
    private static final Instant AHORA = Instant.parse("2026-10-12T04:00:00Z");
    private static final UUID ID_EXISTENTE = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    private final RegistroRepositoryEnMemoria registros = new RegistroRepositoryEnMemoria();
    private final PasswordHasherFalso hasher = new PasswordHasherFalso();
    private final NotificadorCuentaFalso notificador = new NotificadorCuentaFalso();
    private final OutboxEnMemoria outbox = new OutboxEnMemoria();
    private final RegistrarTuristaService turistas =
            new RegistrarTuristaService(registros, hasher, notificador, new RelojFijo(AHORA), outbox);
    private final RegistrarPrestadorService prestadores =
            new RegistrarPrestadorService(registros, hasher, notificador, new RelojFijo(AHORA), outbox);

    // ------------------------------------------------------------------ turista

    @Test
    void unTuristaNuevoSeRegistraActivoConElHashYLaFechaLocal() {
        RegistroRecibido resultado = turistas.registrar(turista(" Ana@X.com ", true));

        assertThat(resultado).isEqualTo(RegistroRecibido.INSTANCIA);
        TuristaNuevo guardado = registros.turistas().get(0);
        assertThat(guardado.datos().correo().valor()).isEqualTo("ana@x.com");
        assertThat(guardado.contrasenaHash()).isEqualTo(PasswordHasherFalso.hashDe("Clave1234"));
        assertThat(guardado.fechaRegistro()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(guardado.datos().categoriasInteres()).containsExactly(CategoriaInteres.Cultura);
        assertThat(notificador.avisados()).isEmpty();
    }

    /** ADR-011: la cuenta nueva se publica en RabbitMQ (versión 0) para ms-analytics; los destinos HTTP no la reciben. */
    @Test
    void unRegistroNuevoEncolaCuentaActualizadaSoloParaRabbitMq() {
        turistas.registrar(turista("ana@x.com", true));

        EventoCuentaActualizada evento = outbox.eventos().get(0);
        assertThat(evento.idUsuario()).isEqualTo(registros.turistas().get(0).idUsuario());
        assertThat(evento.rol()).isEqualTo(Rol.Turista);
        assertThat(evento.estadoCuenta()).isEqualTo(EstadoAdministrativo.Activo);
        assertThat(evento.version()).isZero();
        assertThat(outbox.entregas()).isEmpty();
        assertThat(outbox.publicaciones()).singleElement()
                .satisfies(p -> assertThat(p.clave).isEqualTo(evento.idUsuario()));
    }

    @Test
    void unCorreoExistenteNoEncolaNingunEvento() {
        registros.conCorreo("ana@x.com", ID_EXISTENTE);

        turistas.registrar(turista("ana@x.com", true));

        assertThat(outbox.eventos()).isEmpty();
        assertThat(notificador.correosAvisados()).containsExactly("ana@x.com");
    }

    @Test
    void conUnCorreoExistenteRespondeLoMismoAvisaAlDuenoYNoCreaNada() {
        registros.conCorreo("ana@x.com", ID_EXISTENTE);

        RegistroRecibido resultado = turistas.registrar(turista("ANA@x.com", true));

        assertThat(resultado).isEqualTo(RegistroRecibido.INSTANCIA);
        assertThat(registros.turistas()).isEmpty();
        assertThat(notificador.avisados()).containsExactly(ID_EXISTENTE);
    }

    /** ADR-006: el hash se calcula también con el correo existente; si no, el tiempo de respuesta lo delataría. */
    @Test
    void elHashSeCalculaConCorreoNuevoYConCorreoExistente() {
        turistas.registrar(turista("nueva@x.com", true));
        registros.conCorreo("vieja@x.com", ID_EXISTENTE);
        turistas.registrar(turista("vieja@x.com", true));

        assertThat(hasher.codificadas()).containsExactly("Clave1234", "Clave1234");
    }

    @Test
    void conDatosInvalidosNoCalculaElHashNiConsultaElCorreo() {
        assertThatThrownBy(() -> turistas.registrar(new ComandoRegistroTurista("", null, "Pérez", null, "ana@x.com",
                "Clave1234", "CO", List.of("Cultura"), true))).isInstanceOf(DatosRegistroInvalidosException.class);

        assertThat(hasher.codificadas()).isEmpty();
        assertThat(registros.consultas()).isEmpty();
    }

    @Test
    void sinAceptarLasPoliticasNoCalculaElHashNiConsultaElCorreo() {
        assertThatThrownBy(() -> turistas.registrar(turista("ana@x.com", false)))
                .isInstanceOf(PoliticasNoAceptadasException.class);

        assertThat(hasher.codificadas()).isEmpty();
        assertThat(registros.consultas()).isEmpty();
    }

    // ------------------------------------------------------------------ prestador

    @Test
    void unPrestadorNuevoSeRegistraConUnSoloCorreo() {
        prestadores.registrar(prestador("Contacto@Empresa.co", "RNT-1", true));

        PrestadorNuevo guardado = registros.prestadores().get(0);
        assertThat(guardado.datos().correoCorporativo().valor()).isEqualTo("contacto@empresa.co");
        assertThat(guardado.datos().rnt()).isEqualTo("RNT-1");
        assertThat(guardado.contrasenaHash()).isEqualTo(PasswordHasherFalso.hashDe("Clave1234"));
    }

    @Test
    void unRntExistenteDa409SinConsultarElCorreo() {
        registros.conRnt("RNT-1");

        assertThatThrownBy(() -> prestadores.registrar(prestador("nuevo@empresa.co", "RNT-1", true)))
                .isInstanceOf(RntYaRegistradoException.class);

        assertThat(registros.consultas()).containsExactly("rnt");
        assertThat(registros.prestadores()).isEmpty();
    }

    @Test
    void unRntExistenteDa409AunqueElCorreoTambienExista() {
        registros.conRnt("RNT-1").conCorreo("viejo@empresa.co", ID_EXISTENTE);

        assertThatThrownBy(() -> prestadores.registrar(prestador("viejo@empresa.co", "RNT-1", true)))
                .isInstanceOf(RntYaRegistradoException.class);

        assertThat(notificador.avisados()).isEmpty();
    }

    @Test
    void unCorreoExistenteConRntNuevoRespondeLoMismoYAvisaAlDueno() {
        registros.conCorreo("viejo@empresa.co", ID_EXISTENTE);

        RegistroRecibido resultado = prestadores.registrar(prestador("viejo@empresa.co", "RNT-NUEVO", true));

        assertThat(resultado).isEqualTo(RegistroRecibido.INSTANCIA);
        assertThat(registros.prestadores()).isEmpty();
        assertThat(notificador.avisados()).containsExactly(ID_EXISTENTE);
        assertThat(registros.consultas()).containsExactly("rnt", "correo");
        assertThat(hasher.codificadas()).hasSize(1);
    }

    @Test
    void elPrestadorSinAceptarLasPoliticasNoSeRegistra() {
        assertThatThrownBy(() -> prestadores.registrar(prestador("c@empresa.co", "RNT-2", false)))
                .isInstanceOf(PoliticasNoAceptadasException.class);

        assertThat(registros.consultas()).isEmpty();
    }

    @Test
    void laListaDePaisesSaleDelDominio() {
        assertThat(new ConsultarPaisesService().consultar()).contains("CO").isSorted();
    }

    // ------------------------------------------------------------------ auxiliares

    private static ComandoRegistroTurista turista(String correo, Boolean acepto) {
        return new ComandoRegistroTurista("Ana", null, "Pérez", null, correo, "Clave1234", "CO", List.of("Cultura"),
                acepto);
    }

    private static ComandoRegistroPrestador prestador(String correo, String rnt, Boolean acepto) {
        return new ComandoRegistroPrestador("Carlos Gómez", null, rnt, correo, "+57 3000000000", "Clave1234", acepto);
    }
}
