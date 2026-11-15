package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;

import java.util.UUID;

/**
 * RF29 (decisión 1 del equipo, DEC-300): el Administrador oculta (reversible), vuelve a mostrar o elimina (definitivo)
 * una calificación con un motivo de 1 a 500 caracteres; cada acción va al log de auditoría con su motivo y recalcula el
 * promedio.
 */
public interface ModerarCalificacionesUseCase {

    int TAMANO = 20;

    Pagina<Calificacion> listar(String estado, int pagina);

    Calificacion ocultar(String id, UUID administrador, String motivo);

    Calificacion mostrar(String id, UUID administrador, String motivo);

    void eliminar(String id, UUID administrador, String motivo);
}
