package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Evento que ms-auth solo publica en RabbitMQ (ADR-011), sin proyección HTTP: cada uno se entrega aunque haya otros del
 * mismo usuario (la auditoría de RF13 necesita todos). Los datos siguen el contrato de {@code docs/sad/api/eventos.md}
 * y nunca llevan datos personales ni texto libre (ADR-011, punto 7).
 */
public sealed interface EventoPublicable permits EventoPrestadorValidado, EventoRolCambiado {

    String tipo();

    /** Cuenta a la que se refiere el evento: la {@code clave} del sobre. */
    UUID idUsuario();

    Instant ocurridoEn();

    /** Campos de {@code datos}, en el orden del contrato. */
    Map<String, Object> datos();
}
