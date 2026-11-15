package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.service.PoliticaSesion;

import java.util.Objects;
import java.util.UUID;

/** Claim {@code ver} contra la proyección de cuentas de analytics_db (ADR-008, DEC-110). */
public class VerificarSesionService implements VerificarSesionUseCase {

    private final CuentaProyeccionPort cuentas;
    private final PoliticaSesion politica;

    public VerificarSesionService(CuentaProyeccionPort cuentas, PoliticaSesion politica) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.politica = Objects.requireNonNull(politica);
    }

    @Override
    public boolean sesionVigente(UUID idUsuario, long ver, String rol) {
        return politica.vigente(cuentas.versionDe(idUsuario), ver, rol);
    }
}
