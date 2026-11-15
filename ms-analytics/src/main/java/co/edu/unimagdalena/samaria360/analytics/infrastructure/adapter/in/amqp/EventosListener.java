package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.amqp;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ProcesarEventoUseCase;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoEntrante;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumidor de la cola {@code ms-analytics.eventos.v1} (ADR-011, punto 5). Lee el sobre de {@code eventos.md} y lo pasa
 * al caso de uso, que deduplica por {@code idEvento}. Un mensaje que no cumple el contrato se rechaza sin reintentos (va a
 * la {@code .dlq}); cualquier otro fallo se reintenta tres veces (configuración del contenedor) y después va a la
 * {@code .dlq}. El log lleva el tipo y el id, nunca los datos.
 */
@Component
@ConditionalOnProperty(name = "samaria360.eventos.consumir", havingValue = "true", matchIfMissing = true)
public class EventosListener {

    private static final Logger LOG = LoggerFactory.getLogger(EventosListener.class);

    private final ProcesarEventoUseCase procesar;
    private final ObjectMapper json;

    public EventosListener(ProcesarEventoUseCase procesar, ObjectMapper json) {
        this.procesar = procesar;
        this.json = json;
    }

    @RabbitListener(queues = "${samaria360.eventos.cola:ms-analytics.eventos.v1}")
    public void recibir(Message mensaje) {
        EventoEntrante evento = leer(mensaje.getBody());
        boolean nuevo;
        try {
            nuevo = procesar.procesar(evento);
        } catch (EventoInvalidoException invalido) {
            LOG.warn("Evento {} {} inválido: va a la cola de mensajes fallidos", evento.tipo(), evento.idEvento());
            throw new AmqpRejectAndDontRequeueException(invalido.getMessage(), invalido);
        }
        LOG.debug("Evento {} {}: {}", evento.tipo(), evento.idEvento(), nuevo ? "procesado" : "duplicado");
    }

    EventoEntrante leer(byte[] cuerpo) {
        try {
            JsonNode sobre = json.readTree(cuerpo);
            Map<String, Object> datos = json.convertValue(sobre.path("datos"), new TypeReference<>() { });
            return new EventoEntrante(UUID.fromString(sobre.get("idEvento").asText()), sobre.get("tipo").asText(),
                    sobre.path("origen").asText(null), Instant.parse(sobre.get("ocurridoEn").asText()),
                    sobre.path("clave").asText(null), datos);
        } catch (IOException | RuntimeException error) {
            throw new AmqpRejectAndDontRequeueException("Mensaje sin el sobre de eventos.md", error);
        }
    }
}
