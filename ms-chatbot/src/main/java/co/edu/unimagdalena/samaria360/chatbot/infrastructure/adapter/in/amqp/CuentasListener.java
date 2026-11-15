package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.amqp;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ProyectarCuentaUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Cola {@code ms-chatbot.eventos.v1} (DEC-280): solo {@code CuentaActualizada}, para la proyección del validador de
 * {@code ver}. El upsert por versión es idempotente, así que no necesita deduplicación aparte. Un mensaje sin el
 * contrato va a la {@code .dlq}.
 */
@Component
@ConditionalOnProperty(name = "samaria360.eventos.consumir", havingValue = "true", matchIfMissing = true)
public class CuentasListener {

    private final ProyectarCuentaUseCase proyectar;
    private final ObjectMapper json;

    public CuentasListener(ProyectarCuentaUseCase proyectar, ObjectMapper json) {
        this.proyectar = proyectar;
        this.json = json;
    }

    @RabbitListener(queues = "${samaria360.eventos.cola:ms-chatbot.eventos.v1}")
    public void recibir(Message mensaje) {
        JsonNode sobre;
        try {
            sobre = json.readTree(mensaje.getBody());
        } catch (IOException error) {
            throw new AmqpRejectAndDontRequeueException("Mensaje sin el sobre de eventos.md", error);
        }
        if (!"CuentaActualizada".equals(sobre.path("tipo").asText())) {
            return;
        }
        JsonNode datos = sobre.path("datos");
        try {
            proyectar.proyectar(UUID.fromString(datos.get("idUsuario").asText()), datos.get("rol").asText(),
                    datos.get("estadoCuenta").asText(), datos.get("version").asLong());
        } catch (RuntimeException invalido) {
            throw new AmqpRejectAndDontRequeueException("CuentaActualizada sin los campos del contrato", invalido);
        }
    }
}
