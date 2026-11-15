package co.edu.unimagdalena.samaria360.catalog.infrastructure;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Prueba de integración contra MongoDB (Testcontainers) con la semilla de demostración, en el perfil test. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "samaria360.eventos.habilitado=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestructuraTestConfig.class)
public @interface PruebaIntegracion {
}
