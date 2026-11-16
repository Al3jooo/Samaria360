package co.edu.unimagdalena.samaria360.analytics.infrastructure.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El usuario puede crearse después del arranque; el consumidor reintenta sin impedir las lecturas HTTP. */
@Configuration(proxyBeanMethods = false)
public class ConsumidoresConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setContainerCustomizer(container -> container.setPossibleAuthenticationFailureFatal(false));
        return factory;
    }
}
