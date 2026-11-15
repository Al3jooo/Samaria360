package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Una lectura de ocupación de un atractivo (evento {@code OcupacionRegistrada} de ms-catalog; DEC-304): el historial
 * con el que la iteración K arma los reportes de afluencia del Gestor (RF64). Sin datos personales.
 */
public record OcupacionRegistrada(UUID idEvento, String idAtractivo, String nombreAtractivo, String categoria,
                                  int ocupacion, int limite, int porcentaje, String nivel, Instant fecha) {

    private static final Set<String> NIVELES = Set.of("Bajo", "Medio", "Alto");

    public OcupacionRegistrada {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(idAtractivo, "idAtractivo");
        Objects.requireNonNull(nombreAtractivo, "nombreAtractivo");
        Objects.requireNonNull(fecha, "fecha");
    }

    public static OcupacionRegistrada de(EventoEntrante evento) {
        String nivel = evento.texto("nivel");
        if (!NIVELES.contains(nivel)) {
            throw new EventoInvalidoException("OcupacionRegistrada: nivel desconocido " + nivel);
        }
        long ocupacion = evento.entero("ocupacion");
        long limite = evento.entero("limiteCapacidadCarga");
        if (ocupacion < 0 || limite < 1) {
            throw new EventoInvalidoException("OcupacionRegistrada: ocupación o límite fuera de rango");
        }
        return new OcupacionRegistrada(evento.idEvento(), evento.texto("idAtractivo"), evento.texto("nombreAtractivo"),
                evento.textoOpcional("categoria").orElse(null), (int) ocupacion, (int) limite,
                (int) evento.entero("porcentaje"), nivel, evento.ocurridoEn());
    }
}
