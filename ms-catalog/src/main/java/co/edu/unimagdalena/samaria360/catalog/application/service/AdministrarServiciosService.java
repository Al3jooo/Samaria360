package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.AdministrarServiciosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.CuentaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.GestionServiciosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EventoCatalogo;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Cuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.DecisionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.IdentificadorObjeto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Revision;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.TextoAcotado;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * CU «Aprobar o rechazar servicio» (RF21). La decisión es un UPDATE condicional sobre el estado Pendiente (DEC-128,
 * como DEC-39): si no cambia nada, se busca el servicio para distinguir el 404 (no existe) del 409 (otro Administrador
 * ya decidió). Aprobar a un servicio cuyo prestador ya está inactivo se permite: queda Publicado, pero oculto por
 * {@code prestadorActivo} (RF22, DEC-145).
 */
public class AdministrarServiciosService implements AdministrarServiciosUseCase {

    private final GestionServiciosPort servicios;
    private final CuentaRepositoryPort cuentas;
    private final RelojPort reloj;

    private final OutboxEventosPort outbox;

    public AdministrarServiciosService(GestionServiciosPort servicios, CuentaRepositoryPort cuentas,
                                       RelojPort reloj, OutboxEventosPort outbox) {
        this.outbox = Objects.requireNonNull(outbox);
        this.servicios = Objects.requireNonNull(servicios);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public PaginaResultados<ServicioComercial> listar(String estado, int pagina) {
        if (pagina < 0) {
            throw new ContenidoInvalidoException(Map.of("pagina", MotivoInvalido.FORMATO));
        }
        EstadoPublicacion filtro = estado == null || estado.isBlank() ? EstadoPublicacion.Pendiente
                : ConsultarMisServiciosService.estadoPublicacion(estado);
        return servicios.listarPorEstado(filtro, pagina, TAMANO_PAGINA);
    }

    @Override
    public ServicioEnRevision detalle(String id) {
        if (!IdentificadorObjeto.esValido(id)) {
            throw new ServicioNoEncontradoException(id);
        }
        return enRevision(servicios.buscar(id).orElseThrow(() -> new ServicioNoEncontradoException(id)));
    }

    /** La fecha va en milisegundos, como la guarda MongoDB. */
    @Override
    public ServicioEnRevision decidir(String id, String decision, String justificacion, UUID administrador) {
        Map<String, MotivoInvalido> campos = new LinkedHashMap<>();
        DecisionServicio valor = null;
        if (decision == null || decision.isBlank()) {
            campos.put("decision", MotivoInvalido.REQUERIDO);
        } else {
            try {
                valor = DecisionServicio.valueOf(decision);
            } catch (IllegalArgumentException e) {
                campos.put("decision", MotivoInvalido.NO_PERMITIDO);
            }
        }
        // La justificación solo se exige y se guarda al rechazar; al aprobar se ignora (como RF28, DEC-42).
        if (valor == DecisionServicio.Rechazar) {
            TextoAcotado.validar(justificacion, Revision.JUSTIFICACION_MAXIMA)
                    .ifPresent(motivo -> campos.put("justificacion", motivo));
        }
        if (!campos.isEmpty()) {
            throw new ContenidoInvalidoException(campos);
        }
        if (!IdentificadorObjeto.esValido(id)) {
            throw new ServicioNoEncontradoException(id);
        }
        Revision revision = new Revision(administrador, reloj.ahora().truncatedTo(ChronoUnit.MILLIS),
                valor == DecisionServicio.Rechazar ? TextoAcotado.limpiar(justificacion) : null);
        if (!servicios.decidir(id, valor.estadoDestino(), revision)) {
            ServicioComercial actual = servicios.buscar(id).orElseThrow(() -> new ServicioNoEncontradoException(id));
            throw new EstadoServicioCambiadoException(actual.getEstadoPublicacion());
        }
        ServicioEnRevision decidido = detalle(id);
        // Auditoría de RF13 en ms-analytics (ADR-011, DEC-293): en la misma transacción de MongoDB que la decisión.
        outbox.registrar(EventoCatalogo.servicioDecidido(id, decidido.servicio().getNombreComercial(),
                decidido.servicio().getPrestadorId(),
                valor.estadoDestino(), administrador, revision.fechaRevision()));
        return decidido;
    }

    private ServicioEnRevision enRevision(ServicioComercial servicio) {
        return new ServicioEnRevision(servicio, cuentas.buscar(servicio.getPrestadorId()).map(Cuenta::estado));
    }
}
