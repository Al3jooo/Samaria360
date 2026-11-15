package co.edu.unimagdalena.samaria360.chatbot.infrastructure;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Prueba de integración contra MongoDB y RabbitMQ reales (Testcontainers), con un solo contexto. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(InfraestructuraTestConfig.class)
public @interface PruebaIntegracion {
}
