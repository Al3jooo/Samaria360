package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.CalificarUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarCalificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroCalificacionesPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ModerarCalificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.AuditoriaRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CalificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ConsultasProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PromedioRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReservaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.ReglaNegocioException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.AccionAuditada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoAnalitica;
import co.edu.unimagdalena.samaria360.analytics.domain.model.PromedioCalificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RF17, RF29 y RF56 (decisión 1 del equipo; DEC-300). Cada escritura (calificar, ocultar, mostrar, eliminar) recalcula
 * el promedio visible de la entidad y encola {@code PromedioActualizado} para ms-catalog (RF61), y la moderación queda
 * en el log de auditoría con su motivo (RF29, RF13). La transacción de MongoDB la abre la infraestructura.
 */
public class CalificacionesService implements CalificarUseCase, ConsultarCalificacionesUseCase,
        ModerarCalificacionesUseCase {

    private final CalificacionRepositoryPort calificaciones;
    private final PromedioRepositoryPort promedios;
    private final ReservaProyeccionPort reservas;
    private final CuentaProyeccionPort cuentas;
    private final AuditoriaRepositoryPort auditoria;
    private final OutboxEventosPort outbox;
    private final ConsultasProyeccionPort fichas;
    private final RelojPort reloj;

    public CalificacionesService(CalificacionRepositoryPort calificaciones, PromedioRepositoryPort promedios,
                                 ReservaProyeccionPort reservas, CuentaProyeccionPort cuentas,
                                 AuditoriaRepositoryPort auditoria, OutboxEventosPort outbox,
                                 ConsultasProyeccionPort fichas, RelojPort reloj) {
        this.calificaciones = Objects.requireNonNull(calificaciones);
        this.promedios = Objects.requireNonNull(promedios);
        this.reservas = Objects.requireNonNull(reservas);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.auditoria = Objects.requireNonNull(auditoria);
        this.outbox = Objects.requireNonNull(outbox);
        this.fichas = Objects.requireNonNull(fichas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    // ------------------------------------------------------------------ RF17

    @Override
    public Calificacion calificar(ComandoCalificar comando) {
        TipoEntidad tipo = tipo(comando.tipoEntidad());
        Optional<ReservaProyectada> reserva = verificarElegible(comando.turista(), tipo, comando.idEntidad());
        Calificacion nueva = Calificacion.nueva(tipo, comando.idEntidad(), comando.turista(),
                reserva.map(ReservaProyectada::idPrestador).orElse(null),
                reserva.map(ReservaProyectada::nombreServicio)
                        .orElseGet(() -> fichas.ultimoNombre(tipo, comando.idEntidad()).orElse(null)),
                comando.estrellas(), comando.comentario(), reloj.ahora());
        Calificacion guardada = calificaciones.insertar(nueva);
        recalcular(tipo, comando.idEntidad());
        return guardada;
    }

    @Override
    public Elegibilidad elegibilidad(UUID turista, String tipoEntidad, String idEntidad) {
        TipoEntidad tipo = tipo(tipoEntidad);
        if (!Calificacion.esObjectId(idEntidad)) {
            throw new CamposInvalidosException(Map.of("id", "FORMATO"));
        }
        Optional<Calificacion> propia = calificaciones.deTurista(turista, tipo, idEntidad);
        if (propia.isPresent()) {
            return new Elegibilidad(false, ReglaNegocioException.CALIFICACION_DUPLICADA, propia.get());
        }
        try {
            verificarElegible(turista, tipo, idEntidad);
            return new Elegibilidad(true, null, null);
        } catch (ReglaNegocioException regla) {
            return new Elegibilidad(false, regla.getCodigo(), null);
        }
    }

    /**
     * Decisión 1: un servicio, solo con una reserva Completada del turista (la de la proyección de ms-booking); un
     * atractivo, cualquier turista con la cuenta Activa. Una calificación por turista y entidad.
     */
    private Optional<ReservaProyectada> verificarElegible(UUID turista, TipoEntidad tipo, String idEntidad) {
        if (!Calificacion.esObjectId(idEntidad)) {
            throw new CamposInvalidosException(Map.of("idEntidad", "FORMATO"));
        }
        Optional<String> estado = cuentas.estadoDe(turista);
        if (estado.isPresent() && !"Activo".equals(estado.get())) {
            throw new ReglaNegocioException(ReglaNegocioException.CUENTA_INACTIVA);
        }
        if (calificaciones.existe(turista, tipo, idEntidad)) {
            throw new ReglaNegocioException(ReglaNegocioException.CALIFICACION_DUPLICADA);
        }
        if (tipo == TipoEntidad.atractivo) {
            return Optional.empty();
        }
        Optional<ReservaProyectada> reserva = reservas.completadaDe(turista, idEntidad);
        if (reserva.isEmpty()) {
            throw new ReglaNegocioException(ReglaNegocioException.SIN_RESERVA_COMPLETADA);
        }
        return reserva;
    }

    // ------------------------------------------------------------------ consultas (RF17, RF56)

    @Override
    public CalificacionesEntidad deEntidad(String tipoEntidad, String idEntidad, int pagina) {
        TipoEntidad tipo = tipo(tipoEntidad);
        if (!Calificacion.esObjectId(idEntidad)) {
            throw new CamposInvalidosException(Map.of("id", "FORMATO"));
        }
        long[] resumen = calificaciones.sumaYCantidadVisibles(tipo, idEntidad);
        return new CalificacionesEntidad(PromedioCalificacion.de(tipo, idEntidad, resumen[0], resumen[1]),
                calificaciones.visibles(tipo, idEntidad, pagina, TAMANO_PUBLICO));
    }

    @Override
    public CalificacionesPrestador delPrestador(FiltroCalificacionesPrestador filtro, int pagina) {
        long[] resumen = calificaciones.sumaYCantidadDelPrestador(filtro);
        String clave = filtro.idServicio() == null ? "000000000000000000000000" : filtro.idServicio();
        return new CalificacionesPrestador(PromedioCalificacion.de(TipoEntidad.servicio, clave, resumen[0], resumen[1]),
                calificaciones.serviciosDelPrestador(filtro.prestador()),
                calificaciones.delPrestador(filtro, pagina, TAMANO_PRESTADOR));
    }

    // ------------------------------------------------------------------ RF29

    @Override
    public Pagina<Calificacion> listar(String estado, int pagina) {
        Calificacion.Estado filtro = null;
        if (estado != null && !estado.isBlank()) {
            try {
                filtro = Calificacion.Estado.valueOf(estado.trim());
            } catch (IllegalArgumentException e) {
                throw new CamposInvalidosException(Map.of("estado", "NO_PERMITIDO"));
            }
        }
        Pagina<Calificacion> encontradas = calificaciones.paraModerar(filtro, pagina, TAMANO);
        // DEC-323: las calificaciones de atractivos anteriores no guardaron el nombre; se completa con la ficha.
        return new Pagina<>(encontradas.elementos().stream().map(this::conNombre).toList(), encontradas.pagina(),
                encontradas.tamano(), encontradas.totalElementos());
    }

    private Calificacion conNombre(Calificacion c) {
        if (c.nombreEntidad() != null) {
            return c;
        }
        return fichas.ultimoNombre(c.tipoEntidad(), c.idEntidad())
                .map(nombre -> new Calificacion(c.id(), c.tipoEntidad(), c.idEntidad(), c.turistaId(), c.idPrestador(),
                        nombre, c.estrellas(), c.comentario(), c.estado(), c.motivoModeracion(), c.fecha(),
                        c.fechaModeracion()))
                .orElse(c);
    }

    @Override
    public Calificacion ocultar(String id, UUID administrador, String motivo) {
        return cambiar(id, administrador, motivo, Calificacion.Estado.Visible, Calificacion.Estado.Oculta,
                AccionAuditada.CALIFICACION_OCULTADA);
    }

    @Override
    public Calificacion mostrar(String id, UUID administrador, String motivo) {
        return cambiar(id, administrador, motivo, Calificacion.Estado.Oculta, Calificacion.Estado.Visible,
                AccionAuditada.CALIFICACION_MOSTRADA);
    }

    @Override
    public void eliminar(String id, UUID administrador, String motivo) {
        String razon = Calificacion.motivoValido(motivo);
        Calificacion actual = existente(id);
        if (!calificaciones.eliminar(id)) {
            throw new ReglaNegocioException(ReglaNegocioException.CALIFICACION_NO_ENCONTRADA);
        }
        Instant ahora = reloj.ahora();
        auditar(administrador, AccionAuditada.CALIFICACION_ELIMINADA, actual, razon, ahora);
        if (actual.estado() == Calificacion.Estado.Visible) {
            recalcular(actual.tipoEntidad(), actual.idEntidad());
        }
    }

    private Calificacion cambiar(String id, UUID administrador, String motivo, Calificacion.Estado esperado,
                                 Calificacion.Estado nuevo, AccionAuditada accion) {
        String razon = Calificacion.motivoValido(motivo);
        Calificacion actual = existente(id);
        Instant ahora = reloj.ahora();
        if (actual.estado() != esperado || !calificaciones.cambiarEstado(id, esperado, nuevo, razon, ahora)) {
            throw new ReglaNegocioException(ReglaNegocioException.ESTADO_CAMBIADO,
                    Map.of("estadoActual", actual.estado() == esperado ? nuevo.name() : actual.estado().name()));
        }
        auditar(administrador, accion, actual, razon, ahora);
        recalcular(actual.tipoEntidad(), actual.idEntidad());
        return calificaciones.buscar(id).orElseThrow();
    }

    private Calificacion existente(String id) {
        if (!Calificacion.esObjectId(id)) {
            throw new ReglaNegocioException(ReglaNegocioException.CALIFICACION_NO_ENCONTRADA);
        }
        return calificaciones.buscar(id)
                .orElseThrow(() -> new ReglaNegocioException(ReglaNegocioException.CALIFICACION_NO_ENCONTRADA));
    }

    /** RF29 pide la acción y su motivo en el log (excepción a «sin texto libre» de DEC-294, registrada en DEC-300). */
    private void auditar(UUID administrador, AccionAuditada accion, Calificacion calificacion, String motivo,
                         Instant ahora) {
        auditoria.registrar(RegistroAuditoria.deModeracion(UUID.randomUUID(), administrador, accion,
                calificacion.id(), calificacion.tipoEntidad(), calificacion.idEntidad(), motivo, ahora));
    }

    private void recalcular(TipoEntidad tipo, String idEntidad) {
        long[] resumen = calificaciones.sumaYCantidadVisibles(tipo, idEntidad);
        PromedioCalificacion promedio = PromedioCalificacion.de(tipo, idEntidad, resumen[0], resumen[1]);
        long version = promedios.guardar(promedio);
        outbox.registrar(EventoAnalitica.promedioActualizado(promedio, version, reloj.ahora()));
    }

    private static TipoEntidad tipo(String texto) {
        try {
            return TipoEntidad.desdeTexto(texto);
        } catch (IllegalArgumentException e) {
            throw new CamposInvalidosException(Map.of("tipoEntidad", "NO_PERMITIDO"));
        }
    }
}
