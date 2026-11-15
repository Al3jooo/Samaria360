package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.AdministrarCuentasUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoValidacion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.PrestadorDetalle;
import co.edu.unimagdalena.samaria360.auth.application.port.in.TuristaResumen;
import co.edu.unimagdalena.samaria360.auth.application.port.out.CuentasAdminRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoEncontradaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.model.CambioEstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.CuentaAdministrada;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoPrestadorValidado;
import co.edu.unimagdalena.samaria360.auth.domain.model.JustificacionRechazo;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso Administrar cuentas, parte de cambios de estado (RF28, RF22, RF62). La transacción la abre la
 * infraestructura: el UPDATE condicional (DEC-39) y el evento {@code CuentaActualizada} se confirman juntos o ninguno
 * (DEC-26). Los datos se validan primero (400) y después se busca la cuenta (404); un id de otro rol es un 404.
 */
public class AdministrarCuentasService implements AdministrarCuentasUseCase {

    static final String APROBAR = "Aprobar";
    static final String RECHAZAR = "Rechazar";

    private final CuentasAdminRepositoryPort cuentas;
    private final OutboxPort outbox;
    private final RelojPort reloj;

    public AdministrarCuentasService(CuentasAdminRepositoryPort cuentas, OutboxPort outbox, RelojPort reloj) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public PrestadorDetalle validar(ComandoValidacion comando) {
        Map<String, MotivoInvalido> campos = new LinkedHashMap<>();
        boolean aprobar = APROBAR.equals(comando.decision());
        boolean rechazar = RECHAZAR.equals(comando.decision());
        if (!aprobar && !rechazar) {
            campos.put("decision", comando.decision() == null ? MotivoInvalido.REQUERIDO : MotivoInvalido.FORMATO);
        }
        if (rechazar) {
            Optional<MotivoInvalido> motivo = JustificacionRechazo.validar(comando.justificacion());
            motivo.ifPresent(m -> campos.put("justificacion", m));
        }
        if (!campos.isEmpty()) {
            throw new DatosRegistroInvalidosException(campos);
        }

        CuentaAdministrada cuenta = buscar(comando.idPrestador(), Rol.Prestador);
        CambioEstadoCuenta cambio = aprobar
                ? cuenta.aprobar()
                : cuenta.rechazar(JustificacionRechazo.de(comando.justificacion()));
        aplicar(cambio, comando.idAdministrador());
        // Auditoría de RF13 (ADR-011): la decisión, sin la justificación (texto libre).
        if (comando.idAdministrador() != null) {
            outbox.registrarPublicacion(new EventoPrestadorValidado(comando.idPrestador(), aprobar,
                    comando.idAdministrador(), reloj.ahora()));
        }
        return detallePrestador(comando.idPrestador());
    }

    @Override
    public PrestadorDetalle inactivar(UUID idPrestador) {
        CambioEstadoCuenta cambio = buscar(idPrestador, Rol.Prestador).inactivar();
        aplicar(cambio, null);
        return detallePrestador(idPrestador);
    }

    @Override
    public TuristaResumen desactivar(UUID idTurista) {
        CambioEstadoCuenta cambio = buscar(idTurista, Rol.Turista).desactivar();
        aplicar(cambio, null);
        return detalleTurista(idTurista);
    }

    @Override
    public TuristaResumen reactivar(UUID idTurista) {
        CambioEstadoCuenta cambio = buscar(idTurista, Rol.Turista).reactivar();
        aplicar(cambio, null);
        return detalleTurista(idTurista);
    }

    private CuentaAdministrada buscar(UUID id, Rol rolEsperado) {
        return cuentas.buscarCuenta(id)
                .filter(cuenta -> cuenta.getRol() == rolEsperado)
                .orElseThrow(() -> new CuentaNoEncontradaException(id));
    }

    /** El cambio y su evento, para los dos destinos, en la misma transacción. */
    private void aplicar(CambioEstadoCuenta cambio, UUID revisadoPor) {
        Instant ahora = reloj.ahora();
        cuentas.aplicar(cambio, revisadoPor, ahora);
        outbox.registrar(EventoCuentaActualizada.de(cambio, ahora), List.of(Destino.values()));
    }

    private PrestadorDetalle detallePrestador(UUID id) {
        return cuentas.detallePrestador(id).orElseThrow(() -> new CuentaNoEncontradaException(id));
    }

    private TuristaResumen detalleTurista(UUID id) {
        return cuentas.detalleTurista(id).orElseThrow(() -> new CuentaNoEncontradaException(id));
    }
}
