package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.eventos;

import co.edu.unimagdalena.samaria360.auth.application.port.out.EntregaFallidaException;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PublicadorEventosPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica un evento del outbox en el exchange {@code samaria360.eventos.v1} (ADR-011, contrato en
 * {@code docs/sad/api/eventos.md}): arma el sobre, publica con {@code mandatory} y espera la confirmación del bróker
 * (<i>publisher confirms</i>, 5 s). Un <i>nack</i>, un mensaje devuelto (sin cola que lo reciba) o un plazo vencido es
 * una entrega fallida: el relevo la reintenta.
 */
@Component
public class EventosRabbitAdapter implements PublicadorEventosPort {

    static final String EXCHANGE = "samaria360.eventos.v1";
    static final long ESPERA_CONFIRMACION_MS = 5_000;

    /** Clave de enrutamiento de cada tipo: {@code <origen>.<agregado>.<suceso>}. */
    static final Map<String, String> CLAVES = Map.of(
            "CuentaActualizada", "auth.cuenta.actualizada",
            "PrestadorValidado", "auth.prestador.validado",
            "RolCambiado", "auth.cuenta.rol-cambiado");

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public EventosRabbitAdapter(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
    }

    @Override
    public void publicar(PublicacionPendiente publicacion) {
        String clave = CLAVES.get(publicacion.tipo());
        if (clave == null) {
            throw new EntregaFallidaException("Tipo de evento sin clave de enrutamiento: " + publicacion.tipo());
        }
        Message mensaje = MessageBuilder.withBody(sobre(publicacion))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(publicacion.idPublico().toString())
                .setType(publicacion.tipo())
                .setAppId("ms-auth")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlacion = new CorrelationData(publicacion.idPublico().toString());
        try {
            rabbit.send(EXCHANGE, clave, mensaje, correlacion);
            CorrelationData.Confirm confirmacion = correlacion.getFuture()
                    .get(ESPERA_CONFIRMACION_MS, TimeUnit.MILLISECONDS);
            if (correlacion.getReturned() != null) {
                throw new EntregaFallidaException("RabbitMQ devolvió el mensaje (sin cola para " + clave + ")");
            }
            if (!confirmacion.isAck()) {
                throw new EntregaFallidaException("RabbitMQ no confirmó el mensaje: " + confirmacion.getReason());
            }
        } catch (AmqpException error) {
            throw new EntregaFallidaException("RabbitMQ no disponible: " + error.getMessage(), error);
        } catch (TimeoutException error) {
            throw new EntregaFallidaException("RabbitMQ no confirmó a tiempo", error);
        } catch (ExecutionException error) {
            throw new EntregaFallidaException("Falló la confirmación de RabbitMQ", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new EntregaFallidaException("Publicación interrumpida", error);
        }
    }

    /** Sobre común de {@code eventos.md}; {@code datos} es la carga guardada en el outbox. */
    private byte[] sobre(PublicacionPendiente publicacion) {
        try {
            ObjectNode sobre = json.createObjectNode();
            sobre.put("idEvento", publicacion.idPublico().toString());
            sobre.put("tipo", publicacion.tipo());
            sobre.put("version", 1);
            sobre.put("origen", "ms-auth");
            sobre.put("ocurridoEn", publicacion.ocurridoEn().toString());
            sobre.put("clave", publicacion.clave().toString());
            sobre.set("datos", json.readTree(publicacion.datosJson()));
            return json.writeValueAsBytes(sobre);
        } catch (JsonProcessingException e) {
            throw new EntregaFallidaException("Carga del outbox inválida", e);
        }
    }
}
