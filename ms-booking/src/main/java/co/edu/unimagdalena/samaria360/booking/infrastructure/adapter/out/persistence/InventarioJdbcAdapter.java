package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.CupoDia;
import co.edu.unimagdalena.samaria360.booking.domain.model.DiaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoDisponibilidad;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaInventarioNueva;
import co.edu.unimagdalena.samaria360.booking.domain.model.RangoFechas;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code inventario_cupo} con SQL nativo, sin entidades (D3): las filas no se cargan antes de actualizarlas. Cada
 * cambio de cupos es un UPDATE condicional sobre una fila; el bloqueo de esa fila dura hasta el fin de la transacción
 * y las filas se toman siempre en orden de fecha y hora, así dos reservas que se cruzan no se esperan entre sí en
 * círculo.
 */
@Component
public class InventarioJdbcAdapter implements InventarioPort {

    private final NamedParameterJdbcTemplate jdbc;

    public InventarioJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FilaInventario> buscarFilas(List<DiaInventario> dias) {
        List<FilaInventario> filas = new ArrayList<>();
        for (DiaInventario dia : dias.stream().sorted(DiaInventario.ORDEN).toList()) {
            jdbc.query("""
                            SELECT id_inventario FROM inventario_cupo
                            WHERE id_servicio_mongo = :servicio AND id_unidad IS NOT DISTINCT FROM :unidad
                              AND fecha = :fecha AND hora_inicio IS NOT DISTINCT FROM :hora AND NOT cerrado""",
                    clave(dia), fila -> {
                        filas.add(new FilaInventario(fila.getLong("id_inventario"), dia));
                    });
        }
        return filas;
    }

    @Override
    public boolean descontar(long idInventario, int cantidad) {
        return jdbc.update("""
                        UPDATE inventario_cupo SET cupos_disponibles = cupos_disponibles - :cantidad
                        WHERE id_inventario = :id AND cupos_disponibles >= :cantidad AND NOT cerrado""",
                new MapSqlParameterSource("id", idInventario).addValue("cantidad", cantidad)) == 1;
    }

    @Override
    public void liberar(long idInventario, int cantidad) {
        jdbc.update("UPDATE inventario_cupo SET cupos_disponibles = cupos_disponibles + :cantidad"
                        + " WHERE id_inventario = :id",
                new MapSqlParameterSource("id", idInventario).addValue("cantidad", cantidad));
    }

    @Override
    public int asegurar(List<FilaInventarioNueva> filas) {
        if (filas.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource[] lote = filas.stream().map(fila -> clave(fila.dia())
                        .addValue("categoria", fila.categoria().name())
                        .addValue("cupos", fila.cupos()))
                .toArray(MapSqlParameterSource[]::new);
        int[] insertadas = jdbc.batchUpdate("""
                INSERT INTO inventario_cupo
                    (id_servicio_mongo, categoria, id_unidad, fecha, hora_inicio, cupos_totales, cupos_disponibles)
                VALUES (:servicio, :categoria, :unidad, :fecha, :hora, :cupos, :cupos)
                ON CONFLICT DO NOTHING""", lote);
        int total = 0;
        for (int filasAfectadas : insertadas) {
            total += Math.max(filasAfectadas, 0);
        }
        return total;
    }

    @Override
    public List<CupoDia> consultar(String idServicio, String idUnidad, RangoFechas rango) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("servicio", idServicio)
                .addValue("unidad", idUnidad, Types.VARCHAR)
                .addValue("desde", rango.desde(), Types.DATE)
                .addValue("hasta", rango.hasta(), Types.DATE);
        return jdbc.query("""
                        SELECT categoria, id_unidad, fecha, hora_inicio, cupos_totales, cupos_disponibles
                        FROM inventario_cupo
                        WHERE id_servicio_mongo = :servicio
                          AND (CAST(:unidad AS varchar) IS NULL OR id_unidad = :unidad)
                          AND fecha BETWEEN :desde AND :hasta AND NOT cerrado
                        ORDER BY id_unidad NULLS FIRST, fecha, hora_inicio NULLS FIRST""",
                parametros, (fila, n) -> {
                    Time hora = fila.getTime("hora_inicio");
                    return new CupoDia(CategoriaReservable.valueOf(fila.getString("categoria")),
                            fila.getString("id_unidad"), fila.getDate("fecha").toLocalDate(),
                            hora == null ? null : hora.toLocalTime(), fila.getInt("cupos_totales"),
                            fila.getInt("cupos_disponibles"));
                });
    }

    @Override
    public Map<String, EstadoDisponibilidad> resumir(List<String> idsServicios, RangoFechas rango) {
        Map<String, EstadoDisponibilidad> encontrados = new HashMap<>();
        jdbc.query("""
                        SELECT id_servicio_mongo, bool_or(cupos_disponibles > 0) AS con_cupos
                        FROM inventario_cupo
                        WHERE id_servicio_mongo IN (:servicios) AND fecha BETWEEN :desde AND :hasta AND NOT cerrado
                        GROUP BY id_servicio_mongo""",
                new MapSqlParameterSource("servicios", idsServicios)
                        .addValue("desde", rango.desde(), Types.DATE)
                        .addValue("hasta", rango.hasta(), Types.DATE),
                fila -> {
                    encontrados.put(fila.getString("id_servicio_mongo"), fila.getBoolean("con_cupos")
                            ? EstadoDisponibilidad.CON_CUPOS : EstadoDisponibilidad.AGOTADO);
                });
        Map<String, EstadoDisponibilidad> resumen = new LinkedHashMap<>();
        idsServicios.forEach(id -> resumen.put(id, encontrados.getOrDefault(id, EstadoDisponibilidad.SIN_INVENTARIO)));
        return resumen;
    }

    /** Los nulos van con su tipo: PostgreSQL no infiere el tipo de un parámetro nulo en IS NOT DISTINCT FROM. */
    private static MapSqlParameterSource clave(DiaInventario dia) {
        return new MapSqlParameterSource("servicio", dia.idServicio())
                .addValue("unidad", dia.idUnidad(), Types.VARCHAR)
                .addValue("fecha", dia.fecha(), Types.DATE)
                .addValue("hora", dia.hora(), Types.TIME);
    }
}
