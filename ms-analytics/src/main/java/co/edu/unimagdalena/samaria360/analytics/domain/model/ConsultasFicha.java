package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Consultas a una ficha en un día de Santa Marta (evento {@code ConsultasAgregadas} de ms-catalog; DEC-283, DEC-307):
 * la base del top 3 de atractivos más consultados (RF14) y de las visualizaciones por servicio del Prestador (RF31).
 */
public record ConsultasFicha(TipoEntidad tipoEntidad, String idEntidad, String nombre, UUID idPrestador,
                             LocalDate dia, long consultas) {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public ConsultasFicha {
        Objects.requireNonNull(tipoEntidad, "tipoEntidad");
        Objects.requireNonNull(idEntidad, "idEntidad");
        Objects.requireNonNull(dia, "dia");
        if (consultas < 1) {
            throw new IllegalArgumentException("consultas desde 1");
        }
    }

    /** Un conteo por entidad del evento; el día es el del inicio de la ventana en Santa Marta. */
    @SuppressWarnings("unchecked")
    public static List<ConsultasFicha> de(EventoEntrante evento) {
        Instant inicio = evento.instante("ventanaInicio");
        LocalDate dia = LocalDate.ofInstant(inicio, ZONA);
        Object valor = evento.datos().get("conteos");
        if (!(valor instanceof List<?> conteos) || conteos.isEmpty()) {
            throw new EventoInvalidoException("ConsultasAgregadas sin conteos");
        }
        List<ConsultasFicha> resultado = new ArrayList<>();
        for (Object elemento : conteos) {
            if (!(elemento instanceof Map<?, ?> conteo)) {
                throw new EventoInvalidoException("ConsultasAgregadas: conteo inválido");
            }
            Map<String, Object> c = (Map<String, Object>) conteo;
            try {
                TipoEntidad tipo = TipoEntidad.desdeTexto(String.valueOf(c.get("tipoEntidad")));
                String id = String.valueOf(c.get("idEntidad"));
                if (!Calificacion.esObjectId(id)) {
                    throw new EventoInvalidoException("ConsultasAgregadas: idEntidad inválido");
                }
                Object prestador = c.get("idPrestador");
                Object nombre = c.get("nombre");
                long consultas = ((Number) c.get("consultas")).longValue();
                resultado.add(new ConsultasFicha(tipo, id, nombre == null ? null : nombre.toString(),
                        tipo == TipoEntidad.servicio && prestador != null ? UUID.fromString(prestador.toString()) : null,
                        dia, consultas));
            } catch (RuntimeException invalido) {
                if (invalido instanceof EventoInvalidoException e) {
                    throw e;
                }
                throw new EventoInvalidoException("ConsultasAgregadas: conteo fuera del contrato");
            }
        }
        return resultado;
    }
}
