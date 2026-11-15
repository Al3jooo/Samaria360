package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.amqp;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarCalificacionUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.OfertaServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CalificacionEntidad;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Cola {@code ms-catalog.eventos.v1} (ADR-011): {@code PromedioActualizado} de ms-analytics (RF17, RF61; DEC-302). Los
 * demás tipos se reconocen y se ignoran (llegarán otros en la iteración I). Un mensaje sin el sobre o con datos que no
 * cumplen el contrato va a la {@code .dlq} sin reintentos.
 */
@Component
@ConditionalOnProperty(name = "samaria360.eventos.consumidor.habilitado", havingValue = "true", matchIfMissing = true)
public class EventosCatalogoListener {

    private final ActualizarCalificacionUseCase calificaciones;
    private final OfertaServicioUseCase oferta;
    private final ObjectMapper json;

    public EventosCatalogoListener(ActualizarCalificacionUseCase calificaciones, OfertaServicioUseCase oferta,
                                   ObjectMapper json) {
        this.calificaciones = calificaciones;
        this.oferta = oferta;
        this.json = json;
    }

    @RabbitListener(queues = "${samaria360.eventos.cola:ms-catalog.eventos.v1}")
    public void recibir(Message mensaje) {
        JsonNode sobre;
        try {
            sobre = json.readTree(mensaje.getBody());
        } catch (IOException error) {
            throw new AmqpRejectAndDontRequeueException("Mensaje sin el sobre de eventos.md", error);
        }
        if ("OfertaModificada".equals(sobre.path("tipo").asText())) {
            ofertaModificada(sobre);
            return;
        }
        if (!"PromedioActualizado".equals(sobre.path("tipo").asText())) {
            return;
        }
        JsonNode datos = sobre.path("datos");
        CalificacionEntidad calificacion;
        try {
            JsonNode promedio = datos.path("promedio");
            calificacion = CalificacionEntidad.deEvento(datos.path("tipoEntidad").asText(null),
                    datos.path("idEntidad").asText(null), promedio.isNumber() ? promedio.asDouble() : null,
                    requerido(datos, "cantidad"), requerido(datos, "version"));
        } catch (RuntimeException invalido) {
            throw new AmqpRejectAndDontRequeueException("PromedioActualizado fuera del contrato", invalido);
        }
        calificaciones.actualizar(calificacion);
    }

    private static long requerido(JsonNode datos, String campo) {
        JsonNode valor = datos.path(campo);
        if (!valor.canConvertToLong()) {
            throw new IllegalArgumentException(campo + " no es un entero");
        }
        return valor.asLong();
    }

    /** RF19 y RF41 (DEC-311): un cambio de oferta de ms-booking, al historial y, con la plantilla, a la oferta. */
    private void ofertaModificada(JsonNode sobre) {
        OfertaServicioUseCase.CambioRecibido cambio;
        try {
            JsonNode d = sobre.path("datos");
            cambio = new OfertaServicioUseCase.CambioRecibido(sobre.path("idEvento").asText(null),
                    d.path("idServicio").asText(null), java.util.UUID.fromString(d.path("idPrestador").asText()),
                    d.path("tipo").asText(null), d.path("fecha").asText(null), d.path("horaInicio").asText(null),
                    d.path("idUnidad").asText(null), d.path("valorAnterior").asText(null),
                    java.util.Objects.requireNonNull(d.path("valorNuevo").asText(null)), textos(d.path("diasOperacion")),
                    textos(d.path("salidas")), java.time.Instant.parse(sobre.path("ocurridoEn").asText()));
            if (cambio.idEvento() == null || cambio.tipo() == null) {
                throw new IllegalArgumentException("sin idEvento o tipo");
            }
            if ("Plantilla".equals(cambio.tipo()) && (cambio.diasOperacion() == null || cambio.diasOperacion().isEmpty())) {
                throw new IllegalArgumentException("plantilla sin días");
            }
        } catch (RuntimeException invalido) {
            throw new AmqpRejectAndDontRequeueException("OfertaModificada fuera del contrato", invalido);
        }
        try {
            oferta.registrarCambio(cambio);
        } catch (IllegalArgumentException invalido) {
            throw new AmqpRejectAndDontRequeueException("OfertaModificada fuera del contrato", invalido);
        }
    }

    private static java.util.List<String> textos(JsonNode lista) {
        if (!lista.isArray()) {
            return null;
        }
        java.util.List<String> valores = new java.util.ArrayList<>();
        lista.forEach(v -> valores.add(v.asText()));
        return valores;
    }
}
