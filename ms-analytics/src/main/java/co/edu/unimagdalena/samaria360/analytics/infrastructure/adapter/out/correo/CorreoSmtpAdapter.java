package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.correo;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ServicioExternoNoDisponibleException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ContactoPrestador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Correo de RF30 por SMTP directo (figura 20; DEC-278, DEC-282): la actividad, la fecha y la cantidad de personas, en
 * español y en inglés (el Prestador no tiene idioma guardado).
 */
@Component
public class CorreoSmtpAdapter implements CorreoPort {

    private final JavaMailSender correo;
    private final String remitente;

    public CorreoSmtpAdapter(JavaMailSender correo,
                             @Value("${samaria360.correo.remitente:no-responder@samaria360.local}") String remitente) {
        this.correo = correo;
        this.remitente = remitente;
    }

    @Override
    public void enviarNuevaReserva(ContactoPrestador contacto, Map<String, String> datos) {
        String servicio = datos.getOrDefault("nombreServicio", "");
        String fecha = datos.getOrDefault("fecha", "");
        String personas = datos.getOrDefault("personas", "");
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(contacto.correo());
        mensaje.setSubject("Samaria360: nueva reserva / new booking — " + servicio);
        mensaje.setText("""
                Hola %s:

                Recibiste una reserva confirmada en Samaria360.
                - Actividad: %s
                - Fecha: %s
                - Personas: %s

                Consulta el detalle en «Historial de reservas» de tu cuenta.

                ---

                You received a confirmed booking on Samaria360.
                - Activity: %s
                - Date: %s
                - People: %s
                """.formatted(contacto.nombre(), servicio, fecha, personas, servicio, fecha, personas));
        try {
            correo.send(mensaje);
        } catch (MailException error) {
            throw new ServicioExternoNoDisponibleException("El servidor SMTP falló: " + error.getClass().getSimpleName(),
                    error);
        }
    }
}
