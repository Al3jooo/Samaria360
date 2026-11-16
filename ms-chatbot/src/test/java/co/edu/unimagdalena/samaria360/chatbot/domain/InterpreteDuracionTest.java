package co.edu.unimagdalena.samaria360.chatbot.domain;

import co.edu.unimagdalena.samaria360.chatbot.domain.service.InterpreteMensaje;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class InterpreteDuracionTest {
    @ParameterizedTest
    @CsvSource({
            "un día,1", "una dia,1", "uno dia,1", "dos días,2", "TRES DÍAS,3", "cuatro dias,4",
            "cinco dias,5", "seis dias,6", "siete dias,7", "ocho dias,7", "nueve dias,7", "diez dias,7",
            "one day,1", "a two-day itinerary,2", "three days,3", "four-day itinerary,4", "five days,5",
            "six days,6", "seven days,7", "eight days,7", "nine days,7", "ten days,7",
            "2 días,2", "2-day itinerary,2", "0 dias,1", "99 days,7", "fin de semana,2", "weekend,2"
    })
    void reconoceLaDuracionYConservaElLimite(String mensaje, int dias) {
        var interpretacion = new InterpreteMensaje().interpretar(mensaje);
        assertThat(interpretacion.pideItinerario()).isTrue();
        assertThat(interpretacion.dias()).isEqualTo(dias);
    }

    @ParameterizedTest
    @CsvSource({"dos días en Minca", "two-day itinerary in Minca", "2 días en Minca"})
    void noBuscaElNumeroComoSiFueraUnLugar(String mensaje) {
        assertThat(new InterpreteMensaje().interpretar(mensaje).palabras()).containsExactly("minca");
    }
}
