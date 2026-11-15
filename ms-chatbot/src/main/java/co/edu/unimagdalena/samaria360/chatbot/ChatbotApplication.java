package co.edu.unimagdalena.samaria360.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** ms-chatbot, el asistente virtual del Entregable 2 (tabla 1, figura 20). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
