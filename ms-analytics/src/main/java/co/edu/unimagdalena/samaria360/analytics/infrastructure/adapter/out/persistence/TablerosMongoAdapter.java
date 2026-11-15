package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AfluenciaAtractivo;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AfluenciaDia;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AtractivoReporte;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.EntidadConsultada;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.VisitantesZona;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.TablerosRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregaciones de los tableros (DEC-308) sobre {@code reservas}, {@code consultas}, {@code cuentas} y
 * {@code ocupaciones}. Los días se agrupan en America/Bogota.
 */
@Component
public class TablerosMongoAdapter implements TablerosRepositoryPort {

    private static final String ZONA = "America/Bogota";

    private final MongoTemplate mongo;

    public TablerosMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private List<Document> agregar(String coleccion, List<Document> etapas) {
        List<AggregationOperation> operaciones = etapas.stream()
                .<AggregationOperation>map(etapa -> contexto -> etapa).toList();
        return mongo.aggregate(Aggregation.newAggregation(operaciones), coleccion, Document.class).getMappedResults();
    }

    /** [desde 00:00, hasta + 1 día 00:00) en Santa Marta. */
    private static Document entre(LocalDate desde, LocalDate hasta) {
        return new Document("$gte", Date.from(desde.atStartOfDay(ConsultasFicha.ZONA).toInstant()))
                .append("$lt", Date.from(hasta.plusDays(1).atStartOfDay(ConsultasFicha.ZONA).toInstant()));
    }

    private static Document diaDe(String campo) {
        return new Document("$dateToString", new Document("format", "%Y-%m-%d").append("date", "$" + campo)
                .append("timezone", ZONA));
    }

    private static long numero(Document d, String campo) {
        Object valor = d.get(campo);
        return valor == null ? 0 : ((Number) valor).longValue();
    }

    @Override
    public Map<LocalDate, Long> reservasPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Long> resultado = new LinkedHashMap<>();
        for (Document d : agregar("reservas", List.of(
                new Document("$match", new Document("confirmadaEn", entre(desde, hasta))),
                new Document("$group", new Document("_id", diaDe("confirmadaEn")).append("n", new Document("$sum", 1)))))) {
            resultado.put(LocalDate.parse(d.getString("_id")), numero(d, "n"));
        }
        return resultado;
    }

    @Override
    public List<EntidadConsultada> masConsultadas(TipoEntidad tipo, LocalDate desde, LocalDate hasta, int limite) {
        return agregar("consultas", List.of(
                new Document("$match", new Document("tipoEntidad", tipo.name())
                        .append("dia", new Document("$gte", desde.toString()).append("$lte", hasta.toString()))),
                new Document("$sort", new Document("dia", 1)),
                new Document("$group", new Document("_id", "$idEntidad").append("nombre", new Document("$last", "$nombre"))
                        .append("n", new Document("$sum", "$consultas"))),
                new Document("$sort", new Document("n", -1).append("_id", 1)),
                new Document("$limit", limite)))
                .stream().map(d -> new EntidadConsultada(d.getString("_id"), d.getString("nombre"), numero(d, "n")))
                .toList();
    }

    @Override
    public List<VisitantesZona> turistasPorPais() {
        return agregar("cuentas", List.of(
                new Document("$match", new Document("rol", "Turista")),
                new Document("$group", new Document("_id", new Document("$ifNull", List.of("$pais", "")))
                        .append("n", new Document("$sum", 1))),
                new Document("$sort", new Document("n", -1).append("_id", 1))))
                .stream().map(d -> new VisitantesZona(d.getString("_id").isEmpty() ? null : d.getString("_id"),
                        numero(d, "n"))).toList();
    }

    @Override
    public List<EntidadConsultada> consultasPorServicio(UUID prestador, LocalDate desde, LocalDate hasta) {
        return agregar("consultas", List.of(
                new Document("$match", new Document("tipoEntidad", "servicio").append("idPrestador", prestador.toString())
                        .append("dia", new Document("$gte", desde.toString()).append("$lte", hasta.toString()))),
                new Document("$sort", new Document("dia", 1)),
                new Document("$group", new Document("_id", "$idEntidad").append("nombre", new Document("$last", "$nombre"))
                        .append("n", new Document("$sum", "$consultas")))))
                .stream().map(d -> new EntidadConsultada(d.getString("_id"), d.getString("nombre"), numero(d, "n")))
                .toList();
    }

    @Override
    public List<EntidadConsultada> reservasPorServicio(UUID prestador, LocalDate desde, LocalDate hasta) {
        return agregar("reservas", List.of(
                new Document("$match", new Document("idPrestador", prestador.toString())
                        .append("confirmadaEn", entre(desde, hasta))),
                new Document("$group", new Document("_id", "$idServicio")
                        .append("nombre", new Document("$last", "$nombreServicio"))
                        .append("n", new Document("$sum", 1)))))
                .stream().map(d -> new EntidadConsultada(d.getString("_id"), d.getString("nombre"), numero(d, "n")))
                .toList();
    }

    private static Document filtroOcupaciones(LocalDate desde, LocalDate hasta, String atractivo) {
        Document filtro = new Document("fecha", entre(desde, hasta));
        if (atractivo != null) {
            filtro.append("idAtractivo", atractivo);
        }
        return filtro;
    }

    private static BigDecimal promedio(Document d) {
        Object valor = d.get("promedio");
        return valor == null ? null : BigDecimal.valueOf(((Number) valor).doubleValue()).setScale(1, RoundingMode.HALF_UP);
    }

    @Override
    public List<AfluenciaAtractivo> afluenciaPorAtractivo(LocalDate desde, LocalDate hasta, String atractivo) {
        return agregar("ocupaciones", List.of(
                new Document("$match", filtroOcupaciones(desde, hasta, atractivo)),
                new Document("$sort", new Document("fecha", 1)),
                new Document("$group", new Document("_id", "$idAtractivo")
                        .append("nombre", new Document("$last", "$nombreAtractivo"))
                        .append("lecturas", new Document("$sum", 1))
                        .append("promedio", new Document("$avg", "$porcentaje"))
                        .append("maximo", new Document("$max", "$porcentaje"))
                        .append("nivel", new Document("$last", "$nivel"))
                        .append("ultima", new Document("$last", "$fecha"))),
                new Document("$sort", new Document("promedio", -1).append("_id", 1))))
                .stream().map(d -> new AfluenciaAtractivo(d.getString("_id"), d.getString("nombre"),
                        numero(d, "lecturas"), promedio(d), (int) numero(d, "maximo"), d.getString("nivel"),
                        d.getDate("ultima").toInstant())).toList();
    }

    @Override
    public List<AfluenciaDia> afluenciaPorDia(LocalDate desde, LocalDate hasta, String atractivo) {
        return agregar("ocupaciones", List.of(
                new Document("$match", filtroOcupaciones(desde, hasta, atractivo)),
                new Document("$group", new Document("_id", diaDe("fecha"))
                        .append("lecturas", new Document("$sum", 1))
                        .append("promedio", new Document("$avg", "$porcentaje"))
                        .append("maximo", new Document("$max", "$porcentaje"))),
                new Document("$sort", new Document("_id", 1))))
                .stream().map(d -> new AfluenciaDia(LocalDate.parse(d.getString("_id")), promedio(d),
                        (int) numero(d, "maximo"), numero(d, "lecturas"))).toList();
    }

    @Override
    public List<AtractivoReporte> atractivosConLecturas() {
        return agregar("ocupaciones", List.of(
                new Document("$sort", new Document("fecha", 1)),
                new Document("$group", new Document("_id", "$idAtractivo")
                        .append("nombre", new Document("$last", "$nombreAtractivo"))),
                new Document("$sort", new Document("nombre", 1).append("_id", 1))))
                .stream().map(d -> new AtractivoReporte(d.getString("_id"), d.getString("nombre"))).toList();
    }
}
