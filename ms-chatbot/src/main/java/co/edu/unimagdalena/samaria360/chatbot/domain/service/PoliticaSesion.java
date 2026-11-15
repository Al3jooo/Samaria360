package co.edu.unimagdalena.samaria360.chatbot.domain.service;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.RolCuenta;

import java.util.OptionalLong;

/**
 * Regla del claim {@code ver} (ADR-008, DEC-110), la misma de ms-catalog y ms-booking: con fila en la proyección, el
 * token vale si {@code ver} no es menor que la versión conocida; sin fila, vale un Turista o un Prestador, pero no un
 * Gestor ni un Administrador.
 */
public class PoliticaSesion {

    /** @param rol el claim {@code rol} del token */
    public boolean vigente(OptionalLong versionConocida, long ver, String rol) {
        if (versionConocida.isEmpty()) {
            return !RolCuenta.privilegiado(rol);
        }
        return ver >= versionConocida.getAsLong();
    }
}
