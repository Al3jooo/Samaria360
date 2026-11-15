package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * MongoDB (réplica de un nodo, como DEC-281) con las migraciones de {@code infra/mongo/analytics}, RabbitMQ con la
 * topología de {@code infra/rabbitmq/definiciones.json}, Mailpit (DEC-282) y ms-auth simulado. Todas las clases de
 * integración comparten este contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfraestructuraTestConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer contenedorMongo() throws Exception {
        MongoDBContainer mongo = new MongoDBContainer("mongo:7.0")
                .withCopyFileToContainer(MountableFile.forHostPath("../infra/mongo/analytics"), "/analytics");
        mongo.start();
        ExecResult resultado = mongo.execInContainer("mongosh", "--quiet", "/analytics/aplicar.js");
        if (resultado.getExitCode() != 0) {
            throw new IllegalStateException("aplicar.js falló:\n" + resultado.getStdout() + resultado.getStderr());
        }
        return mongo;
    }

    @Bean
    RabbitMQContainer rabbitmq() throws Exception {
        RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine")
                .withCopyFileToContainer(MountableFile.forHostPath("../infra/rabbitmq/definiciones.json"),
                        "/tmp/definiciones.json");
        rabbit.start();
        rabbit.execInContainer("rabbitmqctl", "import_definitions", "/tmp/definiciones.json");
        rabbit.execInContainer("rabbitmqctl", "set_permissions", "-p", "samaria360", "guest", ".*", ".*", ".*");
        return rabbit;
    }

    @Bean
    GenericContainer<?> mailpit() {
        GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.27.11")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forListeningPorts(1025, 8025));
        mailpit.start();
        return mailpit;
    }

    @Bean
    DynamicPropertyRegistrar dependenciasDePrueba(RabbitMQContainer rabbit, GenericContainer<?> mailpit) {
        return registro -> {
            registro.add("spring.rabbitmq.host", rabbit::getHost);
            registro.add("spring.rabbitmq.port", rabbit::getAmqpPort);
            registro.add("spring.rabbitmq.username", () -> "guest");
            registro.add("spring.rabbitmq.password", () -> "guest");
            registro.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "100ms");
            registro.add("spring.mail.host", mailpit::getHost);
            registro.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
            registro.add("prueba.mailpit.api", () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
            registro.add("samaria360.seguridad.jwk-set-uri", () -> AuthSimulado.url() + "/jwks");
            registro.add("samaria360.auth.url", AuthSimulado::url);
            registro.add("samaria360.auth.secreto", () -> AuthSimulado.SECRETO);
            registro.add("samaria360.correos.cada", () -> "PT0.5S");
            registro.add("samaria360.correos.espera-inicial", () -> "PT0.5S");
        };
    }
}
