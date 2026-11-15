package co.edu.unimagdalena.samaria360.chatbot.application.service;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarConocimientoUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ConocimientoRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.LimiteSuperadoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.RecursoNoEncontradoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** RF05: CRUD de la base de conocimientos para el Administrador, con un tope de 500 entradas (DEC-297). */
public class GestionarConocimientoService implements GestionarConocimientoUseCase {

    private static final String NO_ENCONTRADO = "CONOCIMIENTO_NO_ENCONTRADO";

    private final ConocimientoRepositoryPort entradas;
    private final RelojPort reloj;

    public GestionarConocimientoService(ConocimientoRepositoryPort entradas, RelojPort reloj) {
        this.entradas = Objects.requireNonNull(entradas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public PaginaConocimiento listar(int pagina) {
        int numero = Math.max(0, pagina);
        return new PaginaConocimiento(entradas.listar(numero, TAMANO_PAGINA), numero, TAMANO_PAGINA, entradas.contar());
    }

    @Override
    public EntradaConocimiento crear(String contenido, String categoria) {
        EntradaConocimiento nueva = EntradaConocimiento.validar(null, contenido, categoria,
                reloj.ahora().truncatedTo(ChronoUnit.MILLIS));
        if (entradas.contar() >= MAXIMO_ENTRADAS) {
            throw new LimiteSuperadoException("LIMITE_CONOCIMIENTO");
        }
        return entradas.guardar(nueva);
    }

    @Override
    public EntradaConocimiento actualizar(String id, String contenido, String categoria) {
        EntradaConocimiento editada = EntradaConocimiento.validar(id, contenido, categoria,
                reloj.ahora().truncatedTo(ChronoUnit.MILLIS));
        entradas.buscar(id).orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
        return entradas.guardar(editada);
    }

    @Override
    public void eliminar(String id) {
        if (!entradas.eliminar(id)) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADO);
        }
    }
}
