package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.notificacion;

import co.edu.unimagdalena.samaria360.auth.application.port.out.NotificadorCuentaPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.auth.domain.model.TokenRecuperacion;
import co.edu.unimagdalena.samaria360.auth.infrastructure.config.CorreoPropiedades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Correos de ms-auth por SMTP directo (figura 20 del Entregable 2; DEC-278, DEC-282): Mailpit en desarrollo y CI, Gmail
 * en la demo. El envío va a un ejecutor propio: la respuesta no espera al servidor SMTP y su duración no depende de si la
 * cuenta existe (ADR-006). Un fallo queda en el log con el id del usuario, nunca con la dirección ni con el token.
 * <p>
 * El token va en el fragmento del enlace ({@code #token=}): el navegador no lo envía al servidor, así que no queda en
 * los logs de nginx ni en el {@code Referer}.
 */
@Component
public class NotificadorCuentaCorreoAdapter implements NotificadorCuentaPort {

    private static final Logger LOG = LoggerFactory.getLogger(NotificadorCuentaCorreoAdapter.class);

    private final JavaMailSender correo;
    private final TaskExecutor ejecutor;
    private final CorreoPropiedades propiedades;

    public NotificadorCuentaCorreoAdapter(JavaMailSender correo, @Qualifier("ejecutorCorreo") TaskExecutor ejecutor,
                                          CorreoPropiedades propiedades) {
        this.correo = correo;
        this.ejecutor = ejecutor;
        this.propiedades = propiedades;
    }

    @Override
    public void avisarRegistroConCorreoExistente(UUID idUsuario, String destinatario) {
        String enlace = propiedades.frontendUrl() + "/recuperar";
        enviar(idUsuario, destinatario, "Samaria360: ya tienes una cuenta / you already have an account", """
                Hola:

                Alguien intentó registrarse en Samaria360 con este correo, que ya tiene una cuenta. Si fuiste tú, inicia \
                sesión; si olvidaste tu contraseña, puedes recuperarla en %s

                Si no fuiste tú, ignora este mensaje: tu cuenta no cambió.

                ---

                Someone tried to sign up for Samaria360 with this email, which already has an account. If it was you, \
                sign in; if you forgot your password, you can recover it at %s

                If it was not you, ignore this message: your account did not change.
                """.formatted(enlace, enlace));
    }

    @Override
    public void enviarRecuperacion(UUID idUsuario, String destinatario, String token, Idioma idioma) {
        String enlace = propiedades.frontendUrl() + "/restablecer#token=" + token;
        long minutos = TokenRecuperacion.VIGENCIA.toMinutes();
        if (idioma == Idioma.en) {
            enviar(idUsuario, destinatario, "Samaria360: reset your password", """
                    Hello:

                    We received a request to reset the password of your Samaria360 account. Open this link to choose a \
                    new one (it expires in %d minutes and can be used only once):

                    %s

                    If you did not request it, ignore this message: your password does not change.
                    """.formatted(minutos, enlace));
        } else {
            enviar(idUsuario, destinatario, "Samaria360: restablece tu contraseña", """
                    Hola:

                    Recibimos una solicitud para restablecer la contraseña de tu cuenta de Samaria360. Abre este enlace \
                    para elegir una nueva (vence en %d minutos y sirve una sola vez):

                    %s

                    Si no la pediste, ignora este mensaje: tu contraseña no cambia.
                    """.formatted(minutos, enlace));
        }
    }

    private void enviar(UUID idUsuario, String destinatario, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(propiedades.remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        try {
            ejecutor.execute(() -> {
                try {
                    correo.send(mensaje);
                    LOG.info("Correo enviado: «{}», idUsuario={}", asunto, idUsuario);
                } catch (MailException error) {
                    LOG.warn("No se pudo enviar el correo «{}» (idUsuario={}): {}", asunto, idUsuario,
                            error.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException rechazo) {
            LOG.warn("Cola de correos llena; no se envió «{}» (idUsuario={})", asunto, idUsuario);
        }
    }
}
