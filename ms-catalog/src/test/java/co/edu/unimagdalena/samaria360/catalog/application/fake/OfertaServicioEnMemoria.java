package co.edu.unimagdalena.samaria360.catalog.application.fake;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaServicioPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Escrituras de plantilla y galería (RF19, RF48) que se registran sin tocar el servicio guardado;
 * {@link #rechazarEscrituras()} simula la condición de la consulta que no se cumplió (una carrera).
 */
public class OfertaServicioEnMemoria implements OfertaServicioPort {

    public record Plantilla(String idServicio, List<String> dias, List<String> salidas) {
    }

    private final List<Plantilla> plantillas = new ArrayList<>();
    private final List<String> agregadas = new ArrayList<>();
    private final List<String> quitadas = new ArrayList<>();
    private boolean rechazar;

    public void rechazarEscrituras() {
        rechazar = true;
    }

    public List<Plantilla> plantillas() {
        return plantillas;
    }

    public List<String> agregadas() {
        return agregadas;
    }

    public List<String> quitadas() {
        return quitadas;
    }

    @Override
    public boolean fijarOferta(String idServicio, UUID prestador, List<String> diasOperacion, List<String> salidas) {
        plantillas.add(new Plantilla(idServicio, diasOperacion, salidas));
        return true;
    }

    @Override
    public boolean agregarImagen(String idServicio, UUID prestador, String ruta) {
        if (rechazar) {
            return false;
        }
        agregadas.add(ruta);
        return true;
    }

    @Override
    public boolean quitarImagen(String idServicio, UUID prestador, String ruta) {
        if (rechazar) {
            return false;
        }
        quitadas.add(ruta);
        return true;
    }
}
