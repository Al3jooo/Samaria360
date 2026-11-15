package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.port.in.ConsultarAsistentesUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.AsistentesPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ContactosTuristasPort;
import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.model.Asistente;
import co.edu.unimagdalena.samaria360.booking.domain.model.ContactoTurista;
import co.edu.unimagdalena.samaria360.booking.domain.model.IdentificadorObjeto;
import co.edu.unimagdalena.samaria360.booking.domain.model.MotivoInvalido;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF42 (respuesta 7 del equipo; DEC-312): las reservas Confirmadas del Prestador para el servicio y la fecha, con el
 * nombre y el contacto de cada turista pedidos a ms-auth en el momento (no se copian a {@code reservas_db}). Solo las
 * reservas del {@code sub}: un servicio ajeno da una lista vacía. La exportación CSV o PDF la arma el frontend.
 */
public class ConsultarAsistentesService implements ConsultarAsistentesUseCase {

    private final AsistentesPort asistentes;
    private final ContactosTuristasPort contactos;

    public ConsultarAsistentesService(AsistentesPort asistentes, ContactosTuristasPort contactos) {
        this.asistentes = Objects.requireNonNull(asistentes);
        this.contactos = Objects.requireNonNull(contactos);
    }

    @Override
    public ListadoAsistentes asistentes(UUID prestador, String idServicio, LocalDate fecha, LocalTime hora) {
        Map<String, MotivoInvalido> campos = new LinkedHashMap<>();
        if (idServicio == null || !IdentificadorObjeto.esValido(idServicio)) {
            campos.put("servicio", idServicio == null ? MotivoInvalido.REQUERIDO : MotivoInvalido.FORMATO);
        }
        if (fecha == null) {
            campos.put("fecha", MotivoInvalido.REQUERIDO);
        }
        if (!campos.isEmpty()) {
            throw new DatosReservaInvalidosException(campos);
        }
        List<Asistente> reservas = asistentes.confirmadas(prestador, idServicio, fecha, hora);
        Map<UUID, ContactoTurista> porTurista = reservas.isEmpty() ? Map.of()
                : contactos.buscar(reservas.stream().map(Asistente::idTurista).distinct().toList());
        List<Asistente> conContacto = reservas.stream()
                .map(a -> a.conContacto(porTurista.get(a.idTurista())))
                .sorted(Comparator.comparing(Asistente::horaInicio, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(a -> a.nombre() == null ? "" : a.nombre()))
                .toList();
        return new ListadoAsistentes(idServicio, fecha, hora, conContacto,
                conContacto.stream().mapToInt(Asistente::personas).sum());
    }
}
