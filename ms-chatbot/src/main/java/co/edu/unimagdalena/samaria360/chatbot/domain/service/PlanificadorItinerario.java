package co.edu.unimagdalena.samaria360.chatbot.domain.service;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;

import java.util.ArrayList;
import java.util.List;

/**
 * Arma el itinerario sugerido (RF04) con las sugerencias, en su orden: hasta 3 actividades por día, repartidas en los
 * días pedidos (DEC-298). Si hay menos actividades que días, el itinerario es más corto: el asistente no inventa
 * actividades que el catálogo no tiene.
 */
public class PlanificadorItinerario {

    static final int POR_DIA = 3;

    public List<Itinerario.Item> planificar(List<Sugerencia> sugerencias, int dias) {
        List<Itinerario.Item> items = new ArrayList<>();
        int maximo = Math.min(sugerencias.size(), dias * POR_DIA);
        for (int i = 0; i < maximo; i++) {
            Sugerencia s = sugerencias.get(i);
            int dia = Math.min(dias, (i * dias) / Math.max(1, maximo) + 1);
            items.add(new Itinerario.Item(i + 1, s.item().esAtractivo() ? "atractivo" : "servicio", s.item().id(),
                    s.item().nombre(), dia));
        }
        return items;
    }
}
