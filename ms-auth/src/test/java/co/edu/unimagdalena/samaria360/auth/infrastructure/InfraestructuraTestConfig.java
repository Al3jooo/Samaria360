package co.edu.unimagdalena.samaria360.auth.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * PostgreSQL real (misma imagen que docker-compose) y un par de llaves RSA generado para las pruebas,
 * de modo que no dependen de infra/keys. El contenedor es un bean: todas las clases anotadas con
 * {@link PruebaIntegracion} comparten el contexto en caché y un único contenedor.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfraestructuraTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    /**
     * RabbitMQ real (misma imagen que docker-compose) con la topología de {@code infra/rabbitmq/definiciones.json}
     * (ADR-011): el vhost, el exchange y las colas son los de producción. Se conecta con el usuario guest de la imagen,
     * con permisos sobre el vhost.
     */
    @Bean
    RabbitMQContainer rabbitmq() {
        RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine")
                .withCopyFileToContainer(MountableFile.forHostPath("../infra/rabbitmq/definiciones.json"),
                        "/tmp/definiciones.json");
        rabbit.start();
        try {
            rabbit.execInContainer("rabbitmqctl", "import_definitions", "/tmp/definiciones.json");
            rabbit.execInContainer("rabbitmqctl", "set_permissions", "-p", "samaria360", "guest", ".*", ".*", ".*");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    /** Servidor SMTP de desarrollo (DEC-282), el mismo de Compose: la API de Mailpit deja leer los correos enviados. */
    @Bean
    GenericContainer<?> mailpit() {
        GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.27.11")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forListeningPorts(1025, 8025));
        mailpit.start();
        return mailpit;
    }

    @Bean
    DynamicPropertyRegistrar mailpitDePrueba(GenericContainer<?> mailpit) {
        return registro -> {
            registro.add("spring.mail.host", mailpit::getHost);
            registro.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
            registro.add("prueba.mailpit.api", () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
        };
    }

    @Bean
    DynamicPropertyRegistrar llavesJwtDePrueba() {
        Path directorio = generarLlaves();
        return registro -> {
            registro.add("samaria360.jwt.llave-privada", () -> directorio.resolve("jwt-privada.pem").toString());
            registro.add("samaria360.jwt.llave-publica", () -> directorio.resolve("jwt-publica.pem").toString());
        };
    }

    private static Path generarLlaves() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            KeyPair par = generador.generateKeyPair();
            Path directorio = Files.createTempDirectory("samaria360-jwt-prueba");
            escribirPem(directorio.resolve("jwt-privada.pem"), "PRIVATE KEY", par.getPrivate().getEncoded());
            escribirPem(directorio.resolve("jwt-publica.pem"), "PUBLIC KEY", par.getPublic().getEncoded());
            return directorio;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escribirPem(Path archivo, String tipo, byte[] der) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        Files.writeString(archivo, "-----BEGIN " + tipo + "-----\n" + base64 + "\n-----END " + tipo + "-----\n");
        archivo.toFile().deleteOnExit();
    }
}
