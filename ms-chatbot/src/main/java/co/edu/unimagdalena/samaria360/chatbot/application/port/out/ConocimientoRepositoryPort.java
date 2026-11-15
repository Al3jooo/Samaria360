package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import java.util.List;
import java.util.Optional;

/** {@code base_conocimiento_turistico} (Entregable 2, §2.4; RF05). */
public interface ConocimientoRepositoryPort {

    /** Hasta {@code limite} entradas que contienen alguna de las palabras o son de la categoría. */
    List<EntradaConocimiento> relevantes(List<String> palabras, int limite);

    List<EntradaConocimiento> listar(int pagina, int tamano);

    long contar();

    Optional<EntradaConocimiento> buscar(String id);

    EntradaConocimiento guardar(EntradaConocimiento entrada);

    boolean eliminar(String id);
}
