package co.edu.unimagdalena.samaria360.catalog.infrastructure.config;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultasFichasUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultasFichasService;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.AfluenciaUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OcupacionPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.AfluenciaService;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarCalificacionUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.CalificacionCatalogoPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.ActualizarCalificacionService;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.PublicadorEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.PublicarEventosService;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaReintento;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Eventos de ms-catalog en RabbitMQ (ADR-011, DEC-293): el administrador de transacciones de MongoDB (el outbox se
 * escribe en la transacción del cambio; réplica rs0, DEC-281) y el relevo programado.
 */
@Configuration
@EnableScheduling
public class EventosConfig {

    @Bean
    public MongoTransactionManager transaccionesMongo(MongoDatabaseFactory fabrica) {
        return new MongoTransactionManager(fabrica);
    }

    @Bean
    public PoliticaReintento politicaReintento() {
        return new PoliticaReintento();
    }

    @Bean
    public PublicarEventosUseCase publicarEventosUseCase(OutboxEventosPort outbox, PublicadorEventosPort publicador,
                                                         RelojPort reloj, PoliticaReintento politica) {
        return new PublicarEventosService(outbox, publicador, reloj, politica);
    }

    /** {@code PromedioActualizado} de ms-analytics (DEC-302). */
    @Bean
    public ActualizarCalificacionUseCase actualizarCalificacionUseCase(CalificacionCatalogoPort calificaciones) {
        return new ActualizarCalificacionService(calificaciones);
    }

    /** RF39, RF08 y RF51 (DEC-304, DEC-305): registrar la ocupación, en una transacción con el outbox. */
    @Bean
    public AfluenciaUseCase afluenciaUseCase(OcupacionPort ocupaciones, OutboxEventosPort outbox, RelojPort reloj,
                                             MongoTransactionManager transacciones) {
        return new AfluenciaTransaccional(new AfluenciaService(ocupaciones, outbox, reloj),
                new org.springframework.transaction.support.TransactionTemplate(transacciones));
    }

    /** DEC-283, DEC-307: los conteos de consultas a las fichas, en memoria, encolados cada minuto. */
    @Bean
    public ConsultasFichasUseCase consultasFichasUseCase(OutboxEventosPort outbox, RelojPort reloj) {
        return new ConsultasFichasService(outbox, reloj);
    }
}
