package co.edu.unimagdalena.samaria360.chatbot.domain.model;

/** Roles de las cuentas de ms-auth. Gestor y Administrador son privilegiados (DEC-110). */
public enum RolCuenta {
    Turista,
    Prestador,
    Gestor,
    Administrador;

    /** @param rol el claim {@code rol} del token, tal como llega */
    public static boolean privilegiado(String rol) {
        return Gestor.name().equals(rol) || Administrador.name().equals(rol);
    }
}
