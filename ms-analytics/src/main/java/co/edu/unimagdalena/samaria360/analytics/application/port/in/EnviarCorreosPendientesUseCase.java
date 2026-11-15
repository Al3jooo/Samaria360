package co.edu.unimagdalena.samaria360.analytics.application.port.in;

/** Envía los correos de RF30 encolados; devuelve cuántos se enviaron en la pasada. */
public interface EnviarCorreosPendientesUseCase {

    int enviar();
}
