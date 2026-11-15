package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.booking.application.port.out.AsistentesPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.Asistente;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * RF42 sobre {@code reserva} (índice de V8; DEC-312): las Confirmadas del Prestador para el servicio. Tour: las del día
 * (y del horario). Alojamiento: las estadías con la noche de la fecha ({@code fecha_entrada <= fecha < fecha_salida}).
 */
@Component
public class AsistentesJdbcAdapter implements AsistentesPort {

    private final NamedParameterJdbcTemplate jdbc;

    public AsistentesJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Asistente> confirmadas(UUID prestador, String idServicio, LocalDate fecha, LocalTime hora) {
        return jdbc.query("""
                        SELECT id_reserva, id_turista, personas, hora_inicio, nombre_unidad, fecha_entrada, fecha_salida
                        FROM reserva
                        WHERE id_prestador = :prestador AND id_servicio_mongo = :servicio AND estado = 'Confirmada'
                          AND ((categoria = 'Tour' AND fecha_entrada = :fecha
                                AND (CAST(:hora AS time) IS NULL OR hora_inicio = :hora))
                            OR (categoria = 'Alojamiento' AND fecha_entrada <= :fecha AND fecha_salida > :fecha))
                        ORDER BY hora_inicio NULLS FIRST, fecha_creacion""",
                new MapSqlParameterSource("prestador", prestador).addValue("servicio", idServicio)
                        .addValue("fecha", fecha, Types.DATE)
                        .addValue("hora", hora == null ? null : Time.valueOf(hora), Types.TIME),
                (rs, n) -> {
                    Time horaInicio = rs.getTime("hora_inicio");
                    Date salida = rs.getDate("fecha_salida");
                    return new Asistente(rs.getObject("id_reserva", UUID.class), rs.getObject("id_turista", UUID.class),
                            null, null, null, rs.getInt("personas"),
                            horaInicio == null ? null : horaInicio.toLocalTime(), rs.getString("nombre_unidad"),
                            rs.getDate("fecha_entrada").toLocalDate(), salida == null ? null : salida.toLocalDate());
                });
    }
}
