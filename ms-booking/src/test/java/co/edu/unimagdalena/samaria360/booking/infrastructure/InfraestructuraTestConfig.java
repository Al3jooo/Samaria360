package co.edu.unimagdalena.samaria360.booking.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL real (postgres:16-alpine, la imagen de docker-compose: NULLS NOT DISTINCT y los bloqueos de fila de
 * verdad), ms-catalog y el JWKS de ms-auth simulados, y un reloj ajustable. Todo son beans: las clases con
 * {@link PruebaIntegracion} comparten el contexto, el contenedor y el catálogo simulado.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfraestructuraTestConfig {

    static final CatalogoSimulado CATALOGO = CatalogoSimulado.iniciar();

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
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

    @Bean
    CatalogoSimulado catalogoSimulado() {
        return CATALOGO;
    }

    @Bean
    DynamicPropertyRegistrar destinosSimulados() {
        return registro -> {
            registro.add("samaria360.catalogo.url", CATALOGO::url);
            registro.add("samaria360.auth.url", AuthSimulado::url);
            registro.add("samaria360.auth.secreto", () -> AuthSimulado.SECRETO);
            registro.add("samaria360.seguridad.jwk-set-uri",
                    () -> CATALOGO.url() + "/api/v1/auth/.well-known/jwks.json");
        };
    }

    /** Reemplaza al reloj del sistema (es un RelojPort). */
    @Bean
    @Primary
    RelojAjustable relojAjustable() {
        return new RelojAjustable();
    }
}
