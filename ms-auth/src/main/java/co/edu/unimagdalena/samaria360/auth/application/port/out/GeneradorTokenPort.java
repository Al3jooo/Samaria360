package co.edu.unimagdalena.samaria360.auth.application.port.out;

/** Fuente de tokens de recuperación (RF34): 32 bytes de un generador criptográfico, en Base64 URL sin relleno. */
public interface GeneradorTokenPort {

    String nuevoToken();
}
