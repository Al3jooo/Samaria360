package co.edu.unimagdalena.samaria360.booking.infrastructure;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prueba de integración contra PostgreSQL (Testcontainers) y el catálogo simulado, en el perfil test. Todas las clases
 * usan exactamente esta configuración para compartir un solo contexto. El programador de RF52 está apagado: las
 * pruebas llaman al caso de uso cuando lo necesitan, con el reloj que quieren.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {"samaria360.completar-reservas.habilitado=false", "samaria360.eventos.habilitado=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestructuraTestConfig.class)
public @interface PruebaIntegracion {
}
