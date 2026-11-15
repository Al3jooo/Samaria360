package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPrestadorPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaPrestador;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * RF19 sobre {@code inventario_cupo} (V8; DEC-310), con SQL nativo y sentencias condicionales de una fila (D3): ajustar
 * los cupos sin bajar de lo reservado y cerrar solo sin reservas. El CHECK de la tabla impide disponibles negativos.
 */
@Component
public class InventarioPrestadorJdbcAdapter implements InventarioPrestadorPort {

    private static final String CLAVE = """
            id_servicio_mongo = :servicio AND id_unidad IS NOT DISTINCT FROM :unidad
            AND fecha = :fecha AND hora_inicio IS NOT DISTINCT FROM :hora""";

    private final NamedParameterJdbcTemplate jdbc;

    public InventarioPrestadorJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource clave(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora) {
        return new MapSqlParameterSource("servicio", idServicio)
                .addValue("unidad", idUnidad, Types.VARCHAR)
                .addValue("fecha", fecha, Types.DATE)
                .addValue("hora", hora == null ? null : Time.valueOf(hora), Types.TIME);
    }

    private static FilaPrestador fila(ResultSet rs, int n) throws SQLException {
        Time hora = rs.getTime("hora_inicio");
        return new FilaPrestador(CategoriaReservable.valueOf(rs.getString("categoria")), rs.getString("id_unidad"),
                rs.getDate("fecha").toLocalDate(), hora == null ? null : hora.toLocalTime(),
                rs.getInt("cupos_totales"), rs.getInt("cupos_disponibles"), rs.getBoolean("cerrado"));
    }

    @Override
    public List<FilaPrestador> listar(String idServicio, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                        SELECT categoria, id_unidad, fecha, hora_inicio, cupos_totales, cupos_disponibles, cerrado
                        FROM inventario_cupo
                        WHERE id_servicio_mongo = :servicio AND fecha BETWEEN :desde AND :hasta
                        ORDER BY fecha, hora_inicio NULLS FIRST, id_unidad NULLS FIRST""",
                new MapSqlParameterSource("servicio", idServicio).addValue("desde", desde, Types.DATE)
                        .addValue("hasta", hasta, Types.DATE), InventarioPrestadorJdbcAdapter::fila);
    }

    @Override
    public Optional<FilaPrestador> buscar(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora) {
        return jdbc.query("SELECT categoria, id_unidad, fecha, hora_inicio, cupos_totales, cupos_disponibles, cerrado"
                        + " FROM inventario_cupo WHERE " + CLAVE, clave(idServicio, idUnidad, fecha, hora),
                InventarioPrestadorJdbcAdapter::fila).stream().findFirst();
    }

    @Override
    public boolean ajustar(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora, int nuevo) {
        return jdbc.update("""
                UPDATE inventario_cupo
                SET cupos_disponibles = cupos_disponibles + (:nuevo - cupos_totales), cupos_totales = :nuevo,
                    cerrado = false
                """ + " WHERE " + CLAVE + " AND :nuevo >= cupos_totales - cupos_disponibles",
                clave(idServicio, idUnidad, fecha, hora).addValue("nuevo", nuevo)) == 1;
    }

    @Override
    public boolean insertar(String idServicio, CategoriaReservable categoria, String idUnidad, LocalDate fecha,
                            LocalTime hora, int cupos) {
        return jdbc.update("""
                INSERT INTO inventario_cupo
                    (id_servicio_mongo, categoria, id_unidad, fecha, hora_inicio, cupos_totales, cupos_disponibles)
                VALUES (:servicio, :categoria, :unidad, :fecha, :hora, :cupos, :cupos)
                ON CONFLICT DO NOTHING""",
                clave(idServicio, idUnidad, fecha, hora).addValue("categoria", categoria.name())
                        .addValue("cupos", cupos)) == 1;
    }

    @Override
    public boolean cerrar(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora) {
        return jdbc.update("UPDATE inventario_cupo SET cerrado = true WHERE " + CLAVE
                        + " AND NOT cerrado AND cupos_disponibles = cupos_totales",
                clave(idServicio, idUnidad, fecha, hora)) == 1;
    }
}
