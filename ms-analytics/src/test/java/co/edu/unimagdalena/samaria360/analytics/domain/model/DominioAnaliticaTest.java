package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;
import co.edu.unimagdalena.samaria360.analytics.domain.service.PoliticaSesion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio de ms-analytics, Java puro: lectura del contrato, auditoría, notificaciones y la regla de {@code ver}. */
class DominioAnaliticaTest {

    private static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");
    private static final UUID TURISTA = UUID.randomUUID();
    private static final UUID PRESTADOR = UUID.randomUUID();

    private static EventoEntrante evento(String tipo, Map<String, Object> datos) {
        return new EventoEntrante(UUID.randomUUID(), tipo, "ms-booking", T0, "x", datos);
    }

    private static Map<String, Object> reserva(String inicio) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idReserva", UUID.randomUUID().toString());
        datos.put("idTurista", TURISTA.toString());
        datos.put("idPrestador", PRESTADOR.toString());
        datos.put("idServicio", "65f0c0ffee0000000000000a");
        datos.put("nombreServicio", "Suite Casa Tayrona");
        datos.put("fechaInicio", inicio);
        datos.put("personas", 1);
        datos.put("idUnidad", null);
        return datos;
    }

    /** La fecha de la actividad es la de Santa Marta: las 02:00 UTC del 11 son todavía el 10. */
    @Test
    void laNotificacionAlPrestadorLlevaActividadFechaLocalYPersonasEnSingular() {
        Notificacion n = Notificacion.alPrestador(evento("ReservaConfirmada", reserva("2030-03-11T02:00:00Z")));

        assertThat(n.usuarioId()).isEqualTo(PRESTADOR);
        assertThat(n.mensaje()).isEqualTo("Nueva reserva de «Suite Casa Tayrona» para el 2030-03-10: 1 persona.");
        assertThat(n.datos()).containsEntry("fecha", "2030-03-10").containsEntry("personas", "1")
                .doesNotContainKey("idTurista");
    }

    @Test
    void laNotificacionAlTuristaNoAdmiteElTipoDelPrestador() {
        EventoEntrante e = evento("ReservaConfirmada", reserva("2030-03-10T13:00:00Z"));

        assertThatThrownBy(() -> Notificacion.alTurista(TipoNotificacion.NUEVA_RESERVA, e, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elContratoSeValidaCampoPorCampo() {
        EventoEntrante e = evento("X", Map.of("id", "no-uuid", "n", "texto", "f", "ayer"));

        assertThatThrownBy(() -> e.uuid("id")).isInstanceOf(EventoInvalidoException.class);
        assertThatThrownBy(() -> e.entero("n")).isInstanceOf(EventoInvalidoException.class);
        assertThatThrownBy(() -> e.instante("f")).isInstanceOf(EventoInvalidoException.class);
        assertThatThrownBy(() -> e.texto("falta")).isInstanceOf(EventoInvalidoException.class);
        assertThat(e.textoOpcional("falta")).isEmpty();
    }

    @Test
    void laAuditoriaRechazaUnaDecisionDesconocida() {
        EventoEntrante e = evento("PrestadorValidado", Map.of("idPrestador", PRESTADOR.toString(),
                "decision", "Quizás", "idAdministrador", UUID.randomUUID().toString()));

        assertThatThrownBy(() -> RegistroAuditoria.deValidacion(e)).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void laAuditoriaGuardaLosDatosDelDetalleParaTraducirlo() {
        UUID usuario = UUID.randomUUID();
        RegistroAuditoria rol = RegistroAuditoria.deCambioRol(evento("RolCambiado", Map.of("idUsuario",
                usuario.toString(), "rolAnterior", "Turista", "rolNuevo", "Gestor",
                "idAdministrador", UUID.randomUUID().toString())));
        RegistroAuditoria moderacion = RegistroAuditoria.deModeracion(UUID.randomUUID(), usuario,
                AccionAuditada.CALIFICACION_OCULTADA, "6a0000000000000000000001", TipoEntidad.atractivo,
                "6a0000000000000000000002", "Lenguaje ofensivo", T0);

        // DEC-322: la interfaz arma la frase con datos; el detalle en español se conserva.
        assertThat(rol.datos()).containsExactly(Map.entry("idUsuario", usuario.toString()),
                Map.entry("rolAnterior", "Turista"), Map.entry("rolNuevo", "Gestor"));
        assertThat(rol.detalle()).isEqualTo("Cuenta " + usuario + ": Turista → Gestor");
        assertThat(moderacion.datos()).containsEntry("tipoEntidad", "atractivo")
                .containsEntry("motivo", "Lenguaje ofensivo");
    }

    @Test
    void laAuditoriaDelServicioGuardaSuNombreSiElEventoLoTrae() {
        String servicio = "6a0000000000000000000003";
        Map<String, Object> base = new LinkedHashMap<>(Map.of("idServicio", servicio, "idPrestador",
                PRESTADOR.toString(), "decision", "Publicado", "idAdministrador", UUID.randomUUID().toString()));
        RegistroAuditoria anterior = RegistroAuditoria.deServicio(evento("ServicioDecidido", base));
        base.put("nombreServicio", "Tour Tayrona");
        RegistroAuditoria conNombre = RegistroAuditoria.deServicio(evento("ServicioDecidido", base));

        // DEC-323: el nombre es opcional en el contrato; un evento anterior sigue siendo válido.
        assertThat(anterior.datos()).doesNotContainKey("nombreServicio");
        assertThat(conNombre.datos()).containsEntry("nombreServicio", "Tour Tayrona");
        assertThat(conNombre.detalle()).contains("«Tour Tayrona»");
    }

    @Test
    void elPaisSoloSeGuardaEnUnTurista() {
        CuentaProyectada gestor = CuentaProyectada.de(evento("CuentaActualizada", Map.of("idUsuario",
                TURISTA.toString(), "rol", "Gestor", "estadoCuenta", "Activo", "version", 1, "pais", "CO")));

        assertThat(gestor.pais()).isNull();
        assertThatThrownBy(() -> CuentaProyectada.de(evento("CuentaActualizada", Map.of("idUsuario",
                TURISTA.toString(), "rol", "Rey", "estadoCuenta", "Activo", "version", 1))))
                .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void laReglaDeVerEsLaDeDec110() {
        PoliticaSesion politica = new PoliticaSesion();

        assertThat(politica.vigente(OptionalLong.empty(), 0, "Prestador")).isTrue();
        assertThat(politica.vigente(OptionalLong.empty(), 9, "Gestor")).isFalse();
        assertThat(politica.vigente(OptionalLong.of(4), 3, "Turista")).isFalse();
        assertThat(politica.vigente(OptionalLong.of(4), 4, "Turista")).isTrue();
    }
}
