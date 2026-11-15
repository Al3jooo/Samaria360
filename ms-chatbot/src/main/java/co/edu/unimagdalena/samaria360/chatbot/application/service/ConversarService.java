package co.edu.unimagdalena.samaria360.chatbot.application.service;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoNoDisponibleException;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ConocimientoRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort.ContextoRespuesta;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.UsoDiarioPort;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.LimiteSuperadoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Interpretacion;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.InterpreteMensaje;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.PlanificadorItinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.PoliticaJustificacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * RF04, RF36 y RF59 (decisión 5 del equipo; DEC-297, DEC-298). Pasos:
 * <ol>
 *   <li>valida el mensaje (1 a 500) y consume uno de los 20 mensajes del día del turista (Santa Marta);</li>
 *   <li>interpreta el mensaje sin LLM y consulta la búsqueda pública del catálogo (la «herramienta») y la base de
 *       conocimientos (solo eso puede usar el asistente, RF05);</li>
 *   <li>calcula las sugerencias con su criterio y justificación (RF36) y, si lo pide, el itinerario;</li>
 *   <li>el modelo de lenguaje solo redacta, en el idioma de la interfaz (RF59), con ese contexto.</li>
 * </ol>
 * Si el catálogo no responde, responde con la base de conocimientos y lo dice.
 */
public class ConversarService implements ConversarUseCase {

    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int RESULTADOS = 10;

    private final UsoDiarioPort uso;
    private final CatalogoPort catalogo;
    private final ConocimientoRepositoryPort conocimiento;
    private final ModeloLenguajePort modelo;
    private final RelojPort reloj;
    private final InterpreteMensaje interprete = new InterpreteMensaje();
    private final PoliticaJustificacion justificacion = new PoliticaJustificacion();
    private final PlanificadorItinerario planificador = new PlanificadorItinerario();

    public ConversarService(UsoDiarioPort uso, CatalogoPort catalogo, ConocimientoRepositoryPort conocimiento,
                            ModeloLenguajePort modelo, RelojPort reloj) {
        this.uso = Objects.requireNonNull(uso);
        this.catalogo = Objects.requireNonNull(catalogo);
        this.conocimiento = Objects.requireNonNull(conocimiento);
        this.modelo = Objects.requireNonNull(modelo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public RespuestaAsistente conversar(ComandoMensaje comando) {
        String mensaje = comando.mensaje() == null ? "" : comando.mensaje().strip();
        if (mensaje.isEmpty()) {
            throw new DatosInvalidosException(Map.of("mensaje", "REQUERIDO"));
        }
        if (mensaje.codePointCount(0, mensaje.length()) > MENSAJE_MAXIMO) {
            throw new DatosInvalidosException(Map.of("mensaje", "LONGITUD_MAXIMA"));
        }
        LocalDate hoy = LocalDate.ofInstant(reloj.ahora(), ZONA);
        OptionalLong usados = uso.consumir(comando.turistaId(), hoy, MENSAJES_POR_DIA);
        if (usados.isEmpty()) {
            throw new LimiteSuperadoException("LIMITE_MENSAJES");
        }
        Idioma idioma = Idioma.desdeTexto(comando.idioma());
        Interpretacion interpretacion = interprete.interpretar(mensaje);

        boolean catalogoDisponible = true;
        List<ItemCatalogo> items = List.of();
        if (interpretacion.buscaAlgo()) {
            try {
                items = consultarCatalogo(interpretacion);
            } catch (CatalogoNoDisponibleException caido) {
                catalogoDisponible = false;
            }
        }
        Set<String> intereses = comando.intereses() == null ? Set.of() : new HashSet<>(comando.intereses());
        List<Sugerencia> sugerencias = justificacion.sugerir(items, intereses, idioma);
        List<Itinerario.Item> itinerario = interpretacion.pideItinerario()
                ? planificador.planificar(sugerencias, interpretacion.dias()) : List.of();
        List<String> palabrasConocimiento = new ArrayList<>(interpretacion.palabras());
        if (interpretacion.categoriaAtractivo() != null) {
            palabrasConocimiento.add(interpretacion.categoriaAtractivo());
        }
        List<EntradaConocimiento> entradas = conocimiento.relevantes(palabrasConocimiento, 3);

        String texto = modelo.redactar(new ContextoRespuesta(idioma, mensaje, sugerencias, entradas, itinerario,
                catalogoDisponible));
        List<String> fuentes = entradas.stream().map(EntradaConocimiento::categoria).distinct().toList();
        return new RespuestaAsistente(texto, sugerencias, itinerario, fuentes, catalogoDisponible,
                (int) Math.max(0, MENSAJES_POR_DIA - usados.getAsLong()));
    }

    /**
     * Con la categoría de servicio la busca; con la de atractivo, trae todo y filtra los atractivos de esa categoría.
     * Busca con cada palabra significativa (hasta 5, en orden) y une los resultados sin repetir: con «Tayrona en
     * febrero y un tour de café» la búsqueda también trae el tour de café, no solo lo de la primera palabra. Si
     * ninguna da resultados, busca sin texto. Como máximo {@link #RESULTADOS}.
     */
    private List<ItemCatalogo> consultarCatalogo(Interpretacion interpretacion) {
        Map<String, ItemCatalogo> unicos = new LinkedHashMap<>();
        for (String palabra : interpretacion.palabras()) {
            filtrar(catalogo.buscar(palabra, interpretacion.categoriaServicio(), RESULTADOS), interpretacion)
                    .forEach(item -> unicos.putIfAbsent(item.tipo() + item.id(), item));
        }
        if (unicos.isEmpty()) {
            filtrar(catalogo.buscar(null, interpretacion.categoriaServicio(), RESULTADOS), interpretacion)
                    .forEach(item -> unicos.putIfAbsent(item.tipo() + item.id(), item));
        }
        return unicos.values().stream().limit(RESULTADOS).toList();
    }

    private static List<ItemCatalogo> filtrar(List<ItemCatalogo> items, Interpretacion interpretacion) {
        if (interpretacion.categoriaAtractivo() == null || interpretacion.categoriaServicio() != null) {
            return items;
        }
        return items.stream().filter(i -> i.esAtractivo()
                && interpretacion.categoriaAtractivo().equals(i.categoria())).toList();
    }
}
