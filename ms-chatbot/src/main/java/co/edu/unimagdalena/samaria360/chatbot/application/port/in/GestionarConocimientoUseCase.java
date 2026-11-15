package co.edu.unimagdalena.samaria360.chatbot.application.port.in;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;

import java.util.List;

/** RF05: el Administrador actualiza y restringe la base de conocimientos del asistente. */
public interface GestionarConocimientoUseCase {

    int TAMANO_PAGINA = 10;
    int MAXIMO_ENTRADAS = 500;

    record PaginaConocimiento(List<EntradaConocimiento> elementos, int pagina, int tamano, long totalElementos) {
    }

    PaginaConocimiento listar(int pagina);

    EntradaConocimiento crear(String contenido, String categoria);

    EntradaConocimiento actualizar(String id, String contenido, String categoria);

    void eliminar(String id);
}
