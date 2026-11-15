package co.edu.unimagdalena.samaria360.chatbot.infrastructure.config;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarConocimientoUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarItinerariosUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ProyectarCuentaUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ConocimientoRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ItinerarioRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.UsoDiarioPort;
import co.edu.unimagdalena.samaria360.chatbot.application.service.ConversarService;
import co.edu.unimagdalena.samaria360.chatbot.application.service.GestionarConocimientoService;
import co.edu.unimagdalena.samaria360.chatbot.application.service.GestionarItinerariosService;
import co.edu.unimagdalena.samaria360.chatbot.application.service.ProyectarCuentaService;
import co.edu.unimagdalena.samaria360.chatbot.application.service.VerificarSesionService;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.PoliticaSesion;
import co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.modelo.RedactorGeminiAdapter;
import co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.modelo.RedactorReglasAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra como beans las clases de domain y application, y elige el redactor (DEC-297). */
@Configuration
public class BeanConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BeanConfig.class);

    /**
     * Reglas por defecto (CI, sin llave); Gemini con {@code samaria360.llm.proveedor = gemini} y {@code GEMINI_API_KEY}.
     * Sin llave, aunque se pida Gemini, quedan las reglas. La llave nunca se escribe en los logs.
     */
    @Bean
    public ModeloLenguajePort modeloLenguaje(@Value("${samaria360.llm.proveedor:reglas}") String proveedor,
                                             @Value("${samaria360.llm.gemini.url:https://generativelanguage.googleapis.com}") String url,
                                             @Value("${samaria360.llm.gemini.llave:}") String llave,
                                             @Value("${samaria360.llm.gemini.modelo:gemini-3.5-flash-lite}") String modelo,
                                             @Value("${samaria360.llm.maximo-tokens:400}") int maximoTokens) {
        RedactorReglasAdapter reglas = new RedactorReglasAdapter();
        if ("gemini".equalsIgnoreCase(proveedor)) {
            if (llave == null || llave.isBlank()) {
                LOG.warn("Se pidió Gemini sin GEMINI_API_KEY: el asistente redacta con las reglas");
                return reglas;
            }
            LOG.info("Asistente con Gemini ({}), tope de {} tokens por respuesta", modelo, maximoTokens);
            return new RedactorGeminiAdapter(url, llave, modelo, maximoTokens, reglas);
        }
        return reglas;
    }

    @Bean
    public ConversarUseCase conversarUseCase(UsoDiarioPort uso, CatalogoPort catalogo,
                                             ConocimientoRepositoryPort conocimiento, ModeloLenguajePort modelo,
                                             RelojPort reloj) {
        return new ConversarService(uso, catalogo, conocimiento, modelo, reloj);
    }

    @Bean
    public GestionarConocimientoUseCase gestionarConocimientoUseCase(ConocimientoRepositoryPort conocimiento,
                                                                     RelojPort reloj) {
        return new GestionarConocimientoService(conocimiento, reloj);
    }

    @Bean
    public GestionarItinerariosUseCase gestionarItinerariosUseCase(ItinerarioRepositoryPort itinerarios,
                                                                   RelojPort reloj) {
        return new GestionarItinerariosService(itinerarios, reloj);
    }

    @Bean
    public ProyectarCuentaUseCase proyectarCuentaUseCase(CuentaProyeccionPort cuentas) {
        return new ProyectarCuentaService(cuentas);
    }

    @Bean
    public PoliticaSesion politicaSesion() {
        return new PoliticaSesion();
    }

    @Bean
    public VerificarSesionUseCase verificarSesionUseCase(CuentaProyeccionPort cuentas, PoliticaSesion politica) {
        return new VerificarSesionService(cuentas, politica);
    }
}
