package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.fake.Dobles;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ServicioExternoNoDisponibleException;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.NotificacionNoEncontradaException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ContactoPrestador;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CuentaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.service.PoliticaSesion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Campana (RF63), correo de RF30 y validador de {@code ver} con dobles. */
class ServiciosConsultaTest {

    @Test
    void cadaUsuarioVeSoloSusNotificacionesConLasNoLeidas() {
        Dobles.Notificaciones notificaciones = new Dobles.Notificaciones();
        ProcesarEventoService procesar = new ProcesarEventoService(new Dobles.Procesados(), new Dobles.Cuentas(),
                new Dobles.Auditoria(), notificaciones, new Dobles.Reservas(), new Dobles.Correos(),
                new Dobles.Ocupaciones(), new Dobles.Consultas(),
                () -> ProcesarEventoServiceTest.T0);
        procesar.procesar(ProcesarEventoServiceTest.evento("ReservaConfirmada", ProcesarEventoServiceTest.confirmada()));
        GestionarNotificacionesService servicio = new GestionarNotificacionesService(notificaciones);

        assertThat(servicio.listar(ProcesarEventoServiceTest.PRESTADOR, 0).elementos()).hasSize(1);
        assertThat(servicio.listar(ProcesarEventoServiceTest.PRESTADOR, -3).pagina()).isZero();
        assertThat(servicio.listar(ProcesarEventoServiceTest.TURISTA, 0).noLeidas()).isEqualTo(1);
        assertThatThrownBy(() -> servicio.marcarLeida(ProcesarEventoServiceTest.TURISTA, "ajena"))
                .isInstanceOf(NotificacionNoEncontradaException.class);
    }

    @Test
    void elCorreoDeRf30SeEnviaConElContactoDeMsAuthYSeReintentaSiFalla() {
        Dobles.Correos pendientes = new Dobles.Correos();
        ProcesarEventoService procesar = new ProcesarEventoService(new Dobles.Procesados(), new Dobles.Cuentas(),
                new Dobles.Auditoria(), new Dobles.Notificaciones(), new Dobles.Reservas(), pendientes,
                new Dobles.Ocupaciones(), new Dobles.Consultas(),
                () -> ProcesarEventoServiceTest.T0);
        procesar.procesar(ProcesarEventoServiceTest.evento("ReservaConfirmada", ProcesarEventoServiceTest.confirmada()));
        List<String> enviados = new ArrayList<>();
        boolean[] caido = {true};
        EnviarCorreosPendientesService servicio = new EnviarCorreosPendientesService(pendientes, id -> {
            if (caido[0]) {
                throw new ServicioExternoNoDisponibleException("ms-auth caído", null);
            }
            return Optional.of(new ContactoPrestador("Luis", "luis@x.com"));
        }, (contacto, datos) -> enviados.add(contacto.correo() + " " + datos.get("nombreServicio")),
                () -> ProcesarEventoServiceTest.T0);

        assertThat(servicio.enviar()).isZero();
        assertThat(pendientes.fallos).hasSize(1);
        assertThat(pendientes.fallos.values()).containsExactly(ProcesarEventoServiceTest.T0.plusSeconds(2));

        pendientes.fallos.clear();
        caido[0] = false;
        assertThat(servicio.enviar()).isEqualTo(1);
        assertThat(enviados).containsExactly("luis@x.com Tour a Playa Cristal");
        assertThat(pendientes.enviados).hasSize(1);
    }

    @Test
    void unPrestadorQueMsAuthNoConoceSeDaPorAtendido() {
        Dobles.Correos pendientes = new Dobles.Correos();
        pendientes.encolar(new co.edu.unimagdalena.samaria360.analytics.domain.model.CorreoPendiente(null,
                UUID.randomUUID(), UUID.randomUUID(), Map.of(), 0, ProcesarEventoServiceTest.T0));
        EnviarCorreosPendientesService servicio = new EnviarCorreosPendientesService(pendientes, id -> Optional.empty(),
                (c, d) -> { throw new AssertionError("no debe enviar"); }, () -> ProcesarEventoServiceTest.T0);

        assertThat(servicio.enviar()).isZero();
        assertThat(pendientes.enviados).hasSize(1);
    }

    @Test
    void laEsperaDelReintentoCreceHastaUnaHora() {
        assertThat(EnviarCorreosPendientesService.espera(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(EnviarCorreosPendientesService.espera(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(EnviarCorreosPendientesService.espera(30)).isEqualTo(Duration.ofHours(1));
    }

    /** DEC-110: sin fila, un Gestor o un Administrador no pasa; con fila, ver debe alcanzar la versión. */
    @Test
    void elValidadorDeVerSigueLaRegladeDec110() {
        Dobles.Cuentas cuentas = new Dobles.Cuentas();
        VerificarSesionService sesiones = new VerificarSesionService(cuentas, new PoliticaSesion());
        UUID admin = UUID.randomUUID();

        assertThat(sesiones.sesionVigente(UUID.randomUUID(), 0, "Turista")).isTrue();
        assertThat(sesiones.sesionVigente(admin, 5, "Administrador")).isFalse();
        cuentas.actualizarSiMayor(new CuentaProyectada(admin, "Administrador", "Activo", 3, null));
        assertThat(sesiones.sesionVigente(admin, 2, "Administrador")).isFalse();
        assertThat(sesiones.sesionVigente(admin, 3, "Administrador")).isTrue();
    }
}
