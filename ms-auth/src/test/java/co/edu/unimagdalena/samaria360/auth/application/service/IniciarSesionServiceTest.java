package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.PasswordHasherFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.auth.application.fake.TokenProviderFalso;
import co.edu.unimagdalena.samaria360.auth.application.fake.UsuarioRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoInicioSesion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.SesionIniciada;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CredencialesInvalidasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaBloqueadaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaInactivaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaPendienteValidacionException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaRechazadaException;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaAcceso;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaBloqueo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class IniciarSesionServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CORREO = "turista@samaria360.test";
    private static final String CONTRASENA = "Samaria360*";

    private final UsuarioRepositoryEnMemoria repositorio = new UsuarioRepositoryEnMemoria();
    private final PasswordHasherFalso hasher = new PasswordHasherFalso();
    private final TokenProviderFalso tokens = new TokenProviderFalso();
    private final IniciarSesionService servicio = new IniciarSesionService(
            repositorio, hasher, tokens, new RelojFijo(T0), new PoliticaBloqueo(), new PoliticaAcceso());

    @Test
    void loginExitosoEmiteElToken() {
        repositorio.agregar(usuario(EstadoCuenta.Activo, 0, null));

        SesionIniciada sesion = servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA));

        assertThat(sesion.token()).isEqualTo("token-" + ID);
        assertThat(sesion.expiraEn()).isEqualTo(T0.plus(TokenProviderFalso.VIGENCIA));
        assertThat(sesion.idUsuario()).isEqualTo(ID);
        assertThat(sesion.rol()).isEqualTo(Rol.Turista);
        assertThat(tokens.llamadas()).isEqualTo(1);
    }

    @Test
    void correoInexistenteDevuelveCredencialesInvalidasYIgualVerificaUnHash() {
        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion("nadie@samaria360.test", CONTRASENA)))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(hasher.hashesConsultados()).containsExactly(IniciarSesionService.HASH_FICTICIO);
        assertThat(repositorio.llamadasGuardar()).isZero();
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void contrasenaIncorrectaSumaUnIntentoYSeGuarda() {
        repositorio.agregar(usuario(EstadoCuenta.Activo, 0, null));

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, "incorrecta")))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(repositorio.llamadasGuardar()).isEqualTo(1);
        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isEqualTo(1);
        assertThat(repositorio.persistido(ID).getBloqueadoHasta()).isEmpty();
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void elTercerFalloLanzaCuentaBloqueada() {
        repositorio.agregar(usuario(EstadoCuenta.Activo, 2, null));
        Instant finBloqueo = T0.plus(Duration.ofMinutes(15));

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, "incorrecta")))
                .isInstanceOf(CuentaBloqueadaException.class)
                .extracting(e -> ((CuentaBloqueadaException) e).getBloqueadoHasta())
                .isEqualTo(finBloqueo);

        Usuario persistido = repositorio.persistido(ID);
        assertThat(persistido.getBloqueadoHasta()).contains(finBloqueo);
        assertThat(persistido.getIntentosFallidos()).isZero();
    }

    @Test
    void unaCuentaBloqueadaNoVerificaLaContrasenaNiSiQuieraLaCorrecta() {
        Instant bloqueadoHasta = T0.plus(Duration.ofMinutes(5));
        repositorio.agregar(usuario(EstadoCuenta.Activo, 0, bloqueadoHasta));

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA)))
                .isInstanceOf(CuentaBloqueadaException.class);

        assertThat(hasher.llamadas()).isZero();
        assertThat(repositorio.llamadasGuardar()).isZero();
        assertThat(repositorio.persistido(ID).getBloqueadoHasta()).contains(bloqueadoHasta);
        assertThat(tokens.llamadas()).isZero();
    }

    // Ajuste aprobado: el estado de la cuenta (RF22) se evalúa después de la contraseña. Sin ella no se sabe si la cuenta
    // está inactiva; con una contraseña incorrecta es un intento fallido más.
    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"Inactivo", "Desactivado"})
    void unaCuentaInactivaConContrasenaIncorrectaEsUnIntentoFallido(EstadoCuenta estado) {
        repositorio.agregar(usuario(estado, 1, null));

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, "incorrecta")))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isEqualTo(2);
        assertThat(tokens.llamadas()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"Inactivo", "Desactivado"})
    void unaCuentaInactivaConContrasenaCorrectaDa403SinTokenYReiniciaLosIntentos(EstadoCuenta estado) {
        repositorio.agregar(usuario(estado, 2, null));

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA)))
                .isInstanceOf(CuentaInactivaException.class);

        assertThat(hasher.llamadas()).isEqualTo(1);
        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isZero();
        assertThat(tokens.llamadas()).isZero();
    }

    // Decisión D3: prestador Pendiente o Rechazado, evaluado después de la contraseña.
    @Test
    void unPrestadorPendienteConContrasenaCorrectaDa403SinTokenYReiniciaLosIntentos() {
        repositorio.agregarPrestador(prestador(2, null), EstadoValidacion.Pendiente);

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA)))
                .isInstanceOf(CuentaPendienteValidacionException.class);

        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isZero();
        assertThat(repositorio.llamadasGuardar()).isEqualTo(1);
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void unPrestadorRechazadoConContrasenaCorrectaDa403SinToken() {
        repositorio.agregarPrestador(prestador(1, null), EstadoValidacion.Rechazado);

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA)))
                .isInstanceOf(CuentaRechazadaException.class);

        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isZero();
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void unPrestadorRechazadoConContrasenaCorrectaRecibeLaJustificacionDelRechazo() {
        repositorio.agregarPrestador(prestador(0, null), EstadoValidacion.Rechazado);
        repositorio.motivoRechazo(ID, "El RNT no está vigente");

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA)))
                .isInstanceOfSatisfying(CuentaRechazadaException.class,
                        e -> assertThat(e.getMotivo()).contains("El RNT no está vigente"));
    }

    @Test
    void conLaContrasenaIncorrectaUnPrestadorRechazadoNoRevelaElMotivo() {
        repositorio.agregarPrestador(prestador(0, null), EstadoValidacion.Rechazado);
        repositorio.motivoRechazo(ID, "El RNT no está vigente");

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, "incorrecta")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoValidacion.class, names = {"Pendiente", "Rechazado"})
    void unPrestadorNoValidadoConContrasenaIncorrectaEsUnIntentoFallido(EstadoValidacion validacion) {
        repositorio.agregarPrestador(prestador(0, null), validacion);

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, "incorrecta")))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isEqualTo(1);
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void unPrestadorValidadoIniciaSesion() {
        repositorio.agregarPrestador(prestador(1, null), EstadoValidacion.Activo);

        SesionIniciada sesion = servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA));

        assertThat(sesion.rol()).isEqualTo(Rol.Prestador);
        assertThat(tokens.llamadas()).isEqualTo(1);
        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isZero();
    }

    @Test
    void unPrestadorPendienteBloqueadoRecibe423AntesDeVerificarLaContrasena() {
        repositorio.agregarPrestador(prestador(0, T0.plus(Duration.ofMinutes(5))), EstadoValidacion.Pendiente);

        assertThatThrownBy(() -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA)))
                .isInstanceOf(CuentaBloqueadaException.class);

        assertThat(hasher.llamadas()).isZero();
        assertThat(tokens.llamadas()).isZero();
    }

    @Test
    void unLoginExitosoReiniciaElContador() {
        repositorio.agregar(usuario(EstadoCuenta.Activo, 2, null));

        servicio.iniciarSesion(new ComandoInicioSesion(CORREO, CONTRASENA));

        assertThat(repositorio.llamadasGuardar()).isEqualTo(1);
        assertThat(repositorio.persistido(ID).getIntentosFallidos()).isZero();
    }

    @Test
    void elCorreoConMayusculasYEspaciosSeNormaliza() {
        repositorio.agregar(usuario(EstadoCuenta.Activo, 0, null));

        SesionIniciada sesion = servicio.iniciarSesion(
                new ComandoInicioSesion("  TURISTA@Samaria360.TEST  ", CONTRASENA));

        assertThat(sesion.idUsuario()).isEqualTo(ID);
    }

    @Test
    void correoInexistenteYContrasenaIncorrectaDevuelvenElMismoMensaje() {
        repositorio.agregar(usuario(EstadoCuenta.Activo, 0, null));

        Throwable sinCuenta = catchThrowable(
                () -> servicio.iniciarSesion(new ComandoInicioSesion("nadie@samaria360.test", CONTRASENA)));
        Throwable claveMala = catchThrowable(
                () -> servicio.iniciarSesion(new ComandoInicioSesion(CORREO, "incorrecta")));

        assertThat(sinCuenta).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(claveMala).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(sinCuenta.getMessage()).isEqualTo(claveMala.getMessage());
    }

    private static Usuario prestador(int intentosFallidos, Instant bloqueadoHasta) {
        return new Usuario(ID, CORREO, PasswordHasherFalso.hashDe(CONTRASENA), Rol.Prestador, EstadoCuenta.Activo,
                intentosFallidos, bloqueadoHasta);
    }

    private static Usuario usuario(EstadoCuenta estado, int intentosFallidos, Instant bloqueadoHasta) {
        return new Usuario(ID, CORREO, PasswordHasherFalso.hashDe(CONTRASENA), Rol.Turista, estado,
                intentosFallidos, bloqueadoHasta);
    }
}
