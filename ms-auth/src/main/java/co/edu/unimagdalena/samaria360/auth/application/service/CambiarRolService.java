package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.CambiarRolUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoCambioRol;
import co.edu.unimagdalena.samaria360.auth.application.port.in.PersonalResumen;
import co.edu.unimagdalena.samaria360.auth.application.port.out.CambioRolRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoEncontradaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.model.CambioRol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoRolCambiado;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaAdministradores;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Caso de uso Cambiar rol de cuenta (RF45, DEC-82). Todo el caso de uso corre en la transacción que abre la
 * infraestructura ({@code CambiarRolTransaccional}): la lectura, el bloqueo de los Administradores, el UPDATE condicional
 * y el evento {@code CuentaActualizada} se confirman juntos o ninguno (DEC-26). Orden de los errores (DEC-104): datos
 * (400), cuenta (404), reglas de la cuenta (409, ver {@code CuentaConRol}) y, al final, el último Administrador (409).
 */
public class CambiarRolService implements CambiarRolUseCase {

    private static final Set<Rol> ROLES = EnumSet.of(Rol.Turista, Rol.Gestor, Rol.Administrador);

    private final CambioRolRepositoryPort cuentas;
    private final OutboxPort outbox;
    private final RelojPort reloj;
    private final PoliticaAdministradores politica;

    public CambiarRolService(CambioRolRepositoryPort cuentas, OutboxPort outbox, RelojPort reloj,
                             PoliticaAdministradores politica) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
        this.politica = Objects.requireNonNull(politica);
    }

    @Override
    public PersonalResumen cambiarRol(ComandoCambioRol comando) {
        Map<String, MotivoInvalido> campos = new LinkedHashMap<>();
        Rol rolNuevo = leer(comando.rolNuevo(), "rolNuevo", campos);
        Rol rolActual = leer(comando.rolActual(), "rolActual", campos);
        if (rolNuevo != null && rolNuevo == rolActual) {
            campos.put("rolNuevo", MotivoInvalido.NO_PERMITIDO);
        }
        if (!campos.isEmpty()) {
            throw new DatosRegistroInvalidosException(campos);
        }

        CambioRol cambio = cuentas.buscarCuenta(comando.idCuenta())
                .orElseThrow(() -> new CuentaNoEncontradaException(comando.idCuenta()))
                .cambiarA(rolNuevo, rolActual, comando.idAdministrador());
        if (cambio.quitaAdministrador()) {
            politica.verificarQueQuedeUno(cuentas.bloquearAdministradoresActivos(), cambio);
        }
        cuentas.aplicar(cambio);
        outbox.registrar(EventoCuentaActualizada.de(cambio, reloj.ahora()), List.of(Destino.values()));
        // Auditoría de RF13 (ADR-011).
        outbox.registrarPublicacion(EventoRolCambiado.de(cambio, comando.idAdministrador(), reloj.ahora()));
        return cuentas.resumen(comando.idCuenta())
                .orElseThrow(() -> new CuentaNoEncontradaException(comando.idCuenta()));
    }

    /** Turista, Gestor o Administrador; el Prestador y cualquier otro texto son NO_PERMITIDO. */
    private static Rol leer(String texto, String campo, Map<String, MotivoInvalido> campos) {
        if (texto == null || texto.isBlank()) {
            campos.put(campo, MotivoInvalido.REQUERIDO);
            return null;
        }
        Rol rol = ROLES.stream().filter(r -> r.name().equals(texto)).findFirst().orElse(null);
        if (rol == null) {
            campos.put(campo, MotivoInvalido.NO_PERMITIDO);
        }
        return rol;
    }
}
