package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Servicio interno que pide un JWT de servicio a ms-auth con su secreto (DEC-280): {@code ms-analytics} puede leer el
 * contacto de los prestadores (RF30) y {@code ms-booking}, el de los turistas (RF42). Mínimo privilegio: cada uno ve
 * solo el rol que necesita.
 */
public record ClienteServicio(String nombre, String secreto, Rol rolDeContactos) {

    public ClienteServicio {
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(rolDeContactos, "rolDeContactos");
    }

    /** Sin secreto configurado el cliente está deshabilitado. Comparación en tiempo constante. */
    public boolean acepta(String presentado) {
        if (secreto == null || secreto.isBlank() || presentado == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(secreto), sha256(presentado));
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public String toString() {
        return "ClienteServicio[" + nombre + ", " + rolDeContactos + "]";
    }
}
