package co.edu.unimagdalena.samaria360.auth.application.port.in;

/** Restablecimiento de la contraseña con el token del enlace (RF34). Ni el token ni la contraseña van a los logs. */
public record ComandoRestablecimiento(String token, String contrasena) {

    @Override
    public String toString() {
        return "ComandoRestablecimiento[****]";
    }
}
