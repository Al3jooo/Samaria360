package co.edu.unimagdalena.samaria360.analytics.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Reproduce el primer arranque: el listener conecta antes de que exista su usuario (QA-02). */
class RecuperacionConsumidorIntegracionTest {

    @Test
    void consumeAlCrearElUsuarioSinReiniciarLaAplicacion() throws Exception {
        try (var broker = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine")) {
            broker.start();
            var adminConnection = new CachingConnectionFactory(broker.getHost(), broker.getAmqpPort());
            adminConnection.setUsername(broker.getAdminUsername());
            adminConnection.setPassword(broker.getAdminPassword());
            try {
                new RabbitAdmin(adminConnection).declareQueue(new Queue("qa.recuperacion"));
                new ApplicationContextRunner()
                        .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                        .withUserConfiguration(ConsumidoresConfig.class)
                        .withPropertyValues("spring.rabbitmq.host=" + broker.getHost(),
                                "spring.rabbitmq.port=" + broker.getAmqpPort(),
                                "spring.rabbitmq.username=usuario-tardio",
                                "spring.rabbitmq.password=clave-qa",
                                "spring.rabbitmq.listener.simple.missing-queues-fatal=false")
                        .run(context -> {
                            var factory = context.getBean("rabbitListenerContainerFactory",
                                    org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory.class);
                            var recibido = new CountDownLatch(1);
                            var endpoint = new SimpleRabbitListenerEndpoint();
                            endpoint.setId("qa");
                            endpoint.setQueueNames("qa.recuperacion");
                            endpoint.setMessageListener(message -> recibido.countDown());
                            var listener = factory.createListenerContainer(endpoint);
                            listener.setRecoveryInterval(200);
                            try {
                                listener.start();
                                // start() espera el primer intento: el usuario todavía no existe.
                                assertThat(listener.isRunning()).isTrue();
                                assertThat(listener.getActiveConsumerCount()).isZero();
                                assertThat(broker.execInContainer("rabbitmqctl", "add_user", "usuario-tardio", "clave-qa")
                                        .getExitCode()).isZero();
                                assertThat(broker.execInContainer("rabbitmqctl", "set_permissions", "usuario-tardio",
                                        ".*", ".*", ".*").getExitCode()).isZero();
                                new RabbitTemplate(adminConnection).convertAndSend("", "qa.recuperacion", "recuperado");
                                assertThat(recibido.await(30, TimeUnit.SECONDS)).isTrue();
                            } finally {
                                listener.stop();
                            }
                        });
            } finally {
                adminConnection.destroy();
            }
        }
    }
}
