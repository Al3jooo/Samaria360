package co.edu.unimagdalena.samaria360.auth.application.port.in;

/** Pedido del enlace de recuperación (RF34): el correo de la cuenta y el idioma de la interfaz (RF03). */
public record ComandoRecuperacion(String correo, String idioma) {
}
