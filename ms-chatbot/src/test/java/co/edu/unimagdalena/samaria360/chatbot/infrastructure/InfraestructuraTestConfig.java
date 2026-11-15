package co.edu.unimagdalena.samaria360.chatbot.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;

/**
 * MongoDB (réplica de un nodo, DEC-281) con las migraciones de {@code infra/mongo/chatbot}, RabbitMQ con la topología
 * de {@code infra/rabbitmq/definiciones.json} y ms-catalog simulado (también sirve el JWKS). El redactor es el de
 * reglas: el CI no usa ninguna llave (decisión 5 del equipo).
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfraestructuraTestConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer contenedorMongo() throws Exception {
        MongoDBContainer mongo = new MongoDBContainer("mongo:7.0")
                .withCopyFileToContainer(MountableFile.forHostPath("../infra/mongo/chatbot"), "/chatbot");
        mongo.start();
        ExecResult resultado = mongo.execInContainer("mongosh", "--quiet", "/chatbot/aplicar.js");
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
    DynamicPropertyRegistrar dependenciasDePrueba(RabbitMQContainer rabbit) {
        return registro -> {
            registro.add("spring.rabbitmq.host", rabbit::getHost);
            registro.add("spring.rabbitmq.port", rabbit::getAmqpPort);
            registro.add("spring.rabbitmq.username", () -> "guest");
            registro.add("spring.rabbitmq.password", () -> "guest");
            registro.add("samaria360.seguridad.jwk-set-uri", () -> CatalogoSimulado.url() + "/jwks");
            registro.add("samaria360.catalogo.url", CatalogoSimulado::url);
            registro.add("samaria360.llm.proveedor", () -> "reglas");
        };
    }
}
