package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Colección técnica {@code uso_diario} (DEC-297): mensajes por turista y día de Santa Marta. */
public interface UsoDiarioPort {

    /**
     * Suma un mensaje si el turista no llegó al máximo (operación atómica).
     *
     * @return los mensajes del día con este, o vacío si ya había llegado al máximo
     */
    OptionalLong consumir(UUID turista, LocalDate dia, int maximo);
}
