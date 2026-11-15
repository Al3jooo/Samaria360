package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor de los correos (RF34, DEC-282): dos hilos y una cola de 100. La respuesta HTTP no espera al servidor SMTP;
 * con la cola llena el correo se descarta y queda en el log (el límite de tasa del gateway lo hace improbable).
 */
@Configuration
public class CorreoConfig {

    @Bean(name = "ejecutorCorreo", destroyMethod = "shutdown")
    public TaskExecutor ejecutorCorreo() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(2);
        ejecutor.setQueueCapacity(100);
        ejecutor.setThreadNamePrefix("correo-");
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(10);
        ejecutor.initialize();
        return ejecutor;
    }
}
