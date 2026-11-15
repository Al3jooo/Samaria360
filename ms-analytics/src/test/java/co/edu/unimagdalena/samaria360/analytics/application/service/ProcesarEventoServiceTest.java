package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.fake.Dobles;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.AccionAuditada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoEntrante;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoNotificacion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Procesamiento de los eventos de RabbitMQ (ADR-011) con dobles: RF13, RF30, RF63 y la proyección de cuentas. */
class ProcesarEventoServiceTest {

    static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");
    static final UUID TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000004");
    static final UUID RESERVA = UUID.fromString("7d3c1f0a-1b2c-4d5e-8f90-a1b2c3d4e5f6");

    private final Dobles.Procesados procesados = new Dobles.Procesados();
    private final Dobles.Cuentas cuentas = new Dobles.Cuentas();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final Dobles.Notificaciones notificaciones = new Dobles.Notificaciones();
    private final Dobles.Reservas reservas = new Dobles.Reservas();
    private final Dobles.Correos correos = new Dobles.Correos();
    private final Dobles.Ocupaciones ocupaciones = new Dobles.Ocupaciones();
    private final Dobles.Consultas consultas = new Dobles.Consultas();
    private final ProcesarEventoService servicio = new ProcesarEventoService(procesados, cuentas, auditoria,
            notificaciones, reservas, correos, ocupaciones, consultas, () -> T0);

    static EventoEntrante evento(String tipo, Map<String, Object> datos) {
        return new EventoEntrante(UUID.randomUUID(), tipo, "ms-booking", T0, RESERVA.toString(), datos);
    }

    static Map<String, Object> confirmada() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idReserva", RESERVA.toString());
        datos.put("idTurista", TURISTA.toString());
        datos.put("idPrestador", PRESTADOR.toString());
        datos.put("idServicio", "65f0c0ffee0000000000000a");
        datos.put("nombreServicio", "Tour a Playa Cristal");
        datos.put("categoria", "Tour");
        datos.put("idUnidad", null);
        datos.put("fechaInicio", "2030-03-10T13:00:00Z");
        datos.put("fechaFin", "2030-03-10T18:00:00Z");
        datos.put("personas", 4);
        datos.put("cantidad", 4);
        return datos;
    }

    @Test
    void unaReservaConfirmadaAvisaAlTuristaYAlPrestadorYEncolaElCorreoDeRf30() {
        assertThat(servicio.procesar(evento("ReservaConfirmada", confirmada()))).isTrue();

        assertThat(notificaciones.guardadas).extracting(n -> n.tipo())
                .containsExactly(TipoNotificacion.RESERVA_CONFIRMADA, TipoNotificacion.NUEVA_RESERVA);
        assertThat(notificaciones.guardadas.get(1).usuarioId()).isEqualTo(PRESTADOR);
        assertThat(notificaciones.guardadas.get(1).mensaje())
                .isEqualTo("Nueva reserva de «Tour a Playa Cristal» para el 2030-03-10: 4 personas.");
        assertThat(correos.encolados).singleElement().satisfies(c -> {
            assertThat(c.idPrestador()).isEqualTo(PRESTADOR);
            assertThat(c.datos()).containsEntry("personas", "4").containsEntry("fecha", "2030-03-10");
        });
        assertThat(reservas.guardadas).singleElement().extracting(ReservaProyectada::estado)
                .isEqualTo(ReservaProyectada.EstadoReserva.Confirmada);
    }

    /** ADR-011, punto 5: el mismo idEvento dos veces no repite el efecto. */
    @Test
    void unEventoRepetidoNoRepiteElEfecto() {
        EventoEntrante evento = evento("ReservaConfirmada", confirmada());

        servicio.procesar(evento);
        assertThat(servicio.procesar(evento)).isFalse();

        assertThat(notificaciones.guardadas).hasSize(2);
        assertThat(correos.encolados).hasSize(1);
    }

    @Test
    void unaCancelacionQuedaEnLaAuditoriaConQuienLaHizo() {
        Map<String, Object> datos = new LinkedHashMap<>(confirmada());
        datos.put("canceladaPor", TURISTA.toString());

        servicio.procesar(evento("ReservaCancelada", datos));

        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.usuarioId()).isEqualTo(TURISTA);
            assertThat(r.accion()).isEqualTo(AccionAuditada.RESERVA_CANCELADA);
            assertThat(r.fecha()).isEqualTo(T0);
        });
        assertThat(notificaciones.guardadas).singleElement().extracting(n -> n.tipo())
                .isEqualTo(TipoNotificacion.RESERVA_CANCELADA);
        assertThat(correos.encolados).isEmpty();
    }

    @Test
    void lasDecisionesDelAdministradorQuedanEnLaAuditoria() {
        servicio.procesar(evento("PrestadorValidado", Map.of("idPrestador", PRESTADOR.toString(),
                "decision", "Rechazado", "idAdministrador", ADMIN.toString())));
        servicio.procesar(evento("RolCambiado", Map.of("idUsuario", TURISTA.toString(), "rolAnterior", "Turista",
                "rolNuevo", "Gestor", "idAdministrador", ADMIN.toString())));
        servicio.procesar(evento("ServicioDecidido", Map.of("idServicio", "65f0c0ffee0000000000000a",
                "idPrestador", PRESTADOR.toString(), "decision", "Publicado", "idAdministrador", ADMIN.toString())));

        assertThat(auditoria.registros).extracting(r -> r.accion()).containsExactly(
                AccionAuditada.PRESTADOR_RECHAZADO, AccionAuditada.ROL_CAMBIADO, AccionAuditada.SERVICIO_APROBADO);
        assertThat(auditoria.registros).allSatisfy(r -> assertThat(r.usuarioId()).isEqualTo(ADMIN));
        assertThat(auditoria.registros.get(1).detalle()).contains("Turista → Gestor");
    }

    @Test
    void laCuentaSoloAvanzaConUnaVersionMayorYGuardaElPaisDelTurista() {
        servicio.procesar(evento("CuentaActualizada", Map.of("idUsuario", TURISTA.toString(), "rol", "Turista",
                "estadoCuenta", "Activo", "version", 3, "pais", "PE")));
        servicio.procesar(evento("CuentaActualizada", Map.of("idUsuario", TURISTA.toString(), "rol", "Turista",
                "estadoCuenta", "Desactivado", "version", 2, "pais", "PE")));

        assertThat(cuentas.cuentas.get(TURISTA).estadoCuenta()).isEqualTo("Activo");
        assertThat(cuentas.cuentas.get(TURISTA).pais()).isEqualTo("PE");
    }

    @Test
    void unaCompletadaAvisaAlTuristaQueYaPuedeCalificar() {
        Map<String, Object> datos = new LinkedHashMap<>(confirmada());
        datos.remove("fechaInicio");
        datos.remove("personas");

        servicio.procesar(evento("ReservaCompletada", datos));

        assertThat(notificaciones.guardadas).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo(TipoNotificacion.RESERVA_COMPLETADA);
            assertThat(n.mensaje()).contains("Ya puedes calificar");
        });
    }

    /** ADR-011, punto 8: un tipo nuevo de otro servicio se ignora sin error. */
    @Test
    void unTipoDesconocidoSeMarcaProcesadoSinEfectos() {
        EventoEntrante desconocido = evento("AlgoNuevo", Map.of());

        assertThat(servicio.procesar(desconocido)).isTrue();
        assertThat(procesados.ids).contains(desconocido.idEvento());
        assertThat(auditoria.registros).isEmpty();
    }

    @Test
    void unEventoSinLosCamposDelContratoEsInvalidoYNoQuedaProcesado() {
        EventoEntrante incompleto = evento("PrestadorValidado", Map.of("decision", "Aprobado"));

        assertThatThrownBy(() -> servicio.procesar(incompleto)).isInstanceOf(EventoInvalidoException.class);
        assertThat(procesados.ids).doesNotContain(incompleto.idEvento());
    }

    /** DEC-304: OcupacionRegistrada queda en el historial de afluencia (RF64); un nivel desconocido es inválido. */
    @Test
    void unaOcupacionRegistradaQuedaEnElHistorial() {
        servicio.procesar(new EventoEntrante(UUID.randomUUID(), "OcupacionRegistrada", "ms-catalog", T0,
                "6a0000000000000000000001", Map.of("idAtractivo", "6a0000000000000000000001",
                "nombreAtractivo", "Playa Cristal", "categoria", "Naturaleza", "ocupacion", 240,
                "limiteCapacidadCarga", 300, "porcentaje", 80, "nivel", "Medio")));

        org.assertj.core.api.Assertions.assertThat(ocupaciones.guardadas).singleElement()
                .satisfies(o -> org.assertj.core.api.Assertions.assertThat(o.porcentaje()).isEqualTo(80));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.procesar(new EventoEntrante(UUID.randomUUID(),
                        "OcupacionRegistrada", "ms-catalog", T0, "x", Map.of("idAtractivo", "x",
                        "nombreAtractivo", "x", "ocupacion", 1, "limiteCapacidadCarga", 1, "porcentaje", 100,
                        "nivel", "Lleno"))))
                .isInstanceOf(co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException.class);
    }
}
