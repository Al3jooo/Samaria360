package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Cambio de rol de una cuenta (RF45), para la auditoría de RF13. */
public record EventoRolCambiado(UUID idUsuario, Rol rolAnterior, Rol rolNuevo, UUID idAdministrador,
                                Instant ocurridoEn) implements EventoPublicable {

    public EventoRolCambiado {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(rolAnterior, "rolAnterior");
        Objects.requireNonNull(rolNuevo, "rolNuevo");
        Objects.requireNonNull(idAdministrador, "idAdministrador");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
    }

    public static EventoRolCambiado de(CambioRol cambio, UUID idAdministrador, Instant ahora) {
        return new EventoRolCambiado(cambio.idUsuario(), cambio.rolAnterior(), cambio.rolNuevo(), idAdministrador, ahora);
    }

    @Override
    public String tipo() {
        return "RolCambiado";
    }

    @Override
    public Map<String, Object> datos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idUsuario", idUsuario.toString());
        datos.put("rolAnterior", rolAnterior.name());
        datos.put("rolNuevo", rolNuevo.name());
        datos.put("idAdministrador", idAdministrador.toString());
        return datos;
    }
}
