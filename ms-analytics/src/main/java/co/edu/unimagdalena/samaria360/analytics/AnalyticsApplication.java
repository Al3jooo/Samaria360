package co.edu.unimagdalena.samaria360.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** ms-analytics, «Análisis y notificaciones» del Entregable 2 (tabla 1, figura 20). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
