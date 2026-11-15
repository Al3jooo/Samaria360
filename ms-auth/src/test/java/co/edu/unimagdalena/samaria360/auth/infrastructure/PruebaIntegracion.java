package co.edu.unimagdalena.samaria360.auth.infrastructure;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prueba de integración contra PostgreSQL (Testcontainers) con el perfil test, que carga la semilla.
 * Todas las clases usan exactamente esta configuración para compartir un solo contexto y un solo contenedor.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// El relevo se prueba llamando al caso de uso: el programador no corre en las pruebas.
@SpringBootTest(properties = {"samaria360.outbox.habilitado=false", "samaria360.eventos.habilitado=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestructuraTestConfig.class)
public @interface PruebaIntegracion {
}
