package co.edu.unimagdalena.samaria360.catalog.infrastructure;

import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.mongo.MongoDePrueba;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;

import java.time.Instant;

/**
 * MongoDB real (mongo:7.0, la imagen de docker-compose) preparado como en Compose: la línea base de
 * infra/mongo/init (validadores, índices y semilla v1) y después las migraciones, con la semilla de demostración. Es
 * un bean: todas las clases con {@link PruebaIntegracion} comparten el contexto y un único contenedor.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfraestructuraTestConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer contenedorMongo() throws Exception {
        MongoDBContainer mongo = MongoDePrueba.contenedor();
        mongo.start();
        MongoDePrueba.prepararCatalogoCompleto(mongo);
        return mongo;
    }

    /** RabbitMQ real con la topología de infra/rabbitmq/definiciones.json (ADR-011), con el usuario guest. */
    @Bean
    RabbitMQContainer rabbitmq() {
        RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine")
                .withCopyFileToContainer(MountableFile.forHostPath("../infra/rabbitmq/definiciones.json"),
                        "/tmp/definiciones.json");
        rabbit.start();
        try {
            rabbit.execInContainer("rabbitmqctl", "import_definitions", "/tmp/definiciones.json");
            rabbit.execInContainer("rabbitmqctl", "set_permissions", "-p", "samaria360", "guest", ".*", ".*", ".*");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return rabbit;
    }

    @Bean
    DynamicPropertyRegistrar rabbitmqDePrueba(RabbitMQContainer rabbit) {
        return registro -> {
            registro.add("spring.rabbitmq.host", rabbit::getHost);
            registro.add("spring.rabbitmq.port", rabbit::getAmqpPort);
            registro.add("spring.rabbitmq.username", () -> "guest");
            registro.add("spring.rabbitmq.password", () -> "guest");
        };
    }

    /** Hora fija para {@code fechaActualizacion} y {@code fechaPublicacion}: cada prueba la ajusta. */
    @Bean
    @Primary
    RelojFijo relojDePrueba() {
        return new RelojFijo(Instant.parse("2026-10-22T15:00:00Z"));
    }

    /** El JWKS de ms-auth (simulado): ms-catalog lo descarga al validar el primer token (ADR-005, ADR-008). */
    @Bean
    DynamicPropertyRegistrar jwksDePrueba() {
        return registro -> registro.add("samaria360.seguridad.jwk-set-uri", JwksSimulado::uri);
    }
}
