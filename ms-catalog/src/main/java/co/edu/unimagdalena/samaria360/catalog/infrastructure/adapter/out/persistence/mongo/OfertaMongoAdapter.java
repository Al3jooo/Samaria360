package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Coordenada;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CriteriosBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoOperativo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosAlojamiento;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosAlquilerEquipo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosCategoria;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosGastronomia;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosTour;
import co.edu.unimagdalena.samaria360.catalog.domain.model.OrdenBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ResultadoOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.TextoBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.TipoOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaBusqueda;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Búsqueda combinada de servicios y atractivos en una sola agregación (ADR-004, alternativa A):
 * <pre>
 * servicios: [$geoNear | $match] → $project → $unionWith(atractivos: [$geoNear | $match] → $project)
 *            → [$addFields sinPrecio] → $sort (desempate por _id) → $facet {elementos, total}
 * </pre>
 * Las reglas son las de {@link PoliticaBusqueda} (docs/trazabilidad.md); las pruebas de integración verifican que
 * ambos coincidan.
 */
@Component
public class OfertaMongoAdapter implements OfertaRepositoryPort {

    private static final String SERVICIOS = "servicios";
    private static final String ATRACTIVOS = "atractivos";
    private static final String DETALLE = "detalleCategoria.";
    private static final Pattern METACARACTERES_REGEX = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");
    private static final Collation COLACION_ESPANOL = Collation.of("es").strength(Collation.ComparisonLevel.primary());

    private final MongoTemplate mongo;
    private final PoliticaBusqueda politica = new PoliticaBusqueda();

    public OfertaMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public PaginaResultados<ResultadoOferta> buscar(CriteriosBusqueda criterios) {
        OrdenBusqueda orden = Objects.requireNonNull(criterios.orden(), "el caso de uso resuelve el orden");

        List<AggregationOperation> etapas = new ArrayList<>();
        etapas.add(seleccion(criterios, filtroServicios(criterios)));
        etapas.add(etapa("$project", proyeccionServicio(criterios)));
        if (politica.incluyeAtractivos(criterios)) {
            etapas.add(etapa("$unionWith", new Document("coll", ATRACTIVOS).append("pipeline", List.of(
                    seleccion(criterios, filtroAtractivos(criterios)).toDocument(Aggregation.DEFAULT_CONTEXT),
                    new Document("$project", proyeccionAtractivo())))));
        }
        if (orden == OrdenBusqueda.PrecioAscendente || orden == OrdenBusqueda.PrecioDescendente) {
            // Decisión 4: los resultados sin precio (atractivos) van al final en ambos sentidos.
            Document precioONulo = new Document("$ifNull", Arrays.asList("$precio", null));
            etapas.add(etapa("$addFields", new Document("sinPrecio",
                    new Document("$cond", List.of(new Document("$eq", Arrays.asList(precioONulo, null)), 1, 0)))));
        }
        // $unionWith no garantiza el orden: siempre hay un $sort final con desempate estable por _id.
        etapas.add(etapa("$sort", orden(orden)));
        etapas.add(etapa("$facet", new Document("elementos", List.of(
                new Document("$skip", (long) criterios.pagina() * criterios.tamanoPagina()),
                new Document("$limit", criterios.tamanoPagina())))
                .append("total", List.of(new Document("$count", "n")))));

        AggregationOptions.Builder opciones = AggregationOptions.builder();
        if (orden == OrdenBusqueda.Nombre) {
            opciones.collation(COLACION_ESPANOL);
        }
        Aggregation agregacion = Aggregation.newAggregation(etapas).withOptions(opciones.build());

        PaginaOfertaDocumento pagina = mongo.aggregate(agregacion, SERVICIOS, PaginaOfertaDocumento.class)
                .getUniqueMappedResult();
        List<ResultadoOferta> elementos = pagina == null || pagina.elementos() == null ? List.of()
                : pagina.elementos().stream().map(OfertaMongoAdapter::aDominio).toList();
        return new PaginaResultados<>(elementos, criterios.pagina(), criterios.tamanoPagina(),
                pagina == null ? 0 : pagina.totalElementos());
    }

    /** Con ubicación: $geoNear (usa el índice 2dsphere y filtra en su query). Sin ubicación: $match. */
    private static AggregationOperation seleccion(CriteriosBusqueda criterios, Document filtro) {
        if (criterios.ubicacion() == null) {
            return etapa("$match", filtro);
        }
        Coordenada ubicacion = criterios.ubicacion();
        Document geoNear = new Document("near", new Document("type", "Point")
                .append("coordinates", List.of(ubicacion.longitud(), ubicacion.latitud())))
                .append("key", "coordenadas")
                .append("distanceField", "distanciaMetros")
                .append("spherical", true)
                .append("query", filtro);
        if (criterios.distanciaMaximaKm() != null) {
            geoNear.append("maxDistance", criterios.distanciaMaximaKm() * 1000);
        }
        return etapa("$geoNear", geoNear);
    }

    private static Document filtroServicios(CriteriosBusqueda criterios) {
        // RF16 y RF22: solo servicios publicados de prestadores activos.
        Document filtro = new Document("estadoPublicacion", "Publicado").append("prestadorActivo", true);
        if (criterios.categoria() != null) {
            filtro.append("categoria", criterios.categoria().name());
        }
        if (criterios.precioMaximo() != null) {
            filtro.append("precioBase", new Document("$lte", new Decimal128(criterios.precioMaximo())));
        }
        if (criterios.filtrosCategoria() != null) {
            filtrosDetalle(filtro, criterios.filtrosCategoria(), criterios.precioMaximo());
        }
        return filtrosComunes(filtro, criterios, "nombreComercial");
    }

    /** «Más filtros» (RF10) sobre detalleCategoria, con las mismas reglas que {@link FiltrosCategoria#cumple}. */
    private static void filtrosDetalle(Document filtro, FiltrosCategoria filtros, BigDecimal precioMaximo) {
        if (filtros instanceof FiltrosAlojamiento alojamiento) {
            if (!alojamiento.serviciosIncluidos().isEmpty()) {
                filtro.append(DETALLE + "serviciosIncluidos",
                        new Document("$all", alojamiento.nombresServiciosIncluidos()));
            }
            if (alojamiento.filtraUnidades()) {
                // D1: una misma unidad debe cumplir los filtros de unidad y el precio máximo. Con solo el precio
                // máximo basta precioBase, que es el menor precioNoche: «alguna unidad cuesta hasta el máximo».
                filtro.append(DETALLE + "unidades",
                        new Document("$elemMatch", CondicionesUnidad.de(alojamiento, precioMaximo).consulta()));
            }
        } else if (filtros instanceof FiltrosGastronomia gastronomia) {
            if (gastronomia.soloVegetariano()) {
                filtro.append(DETALLE + "menuVegetariano", true);
            }
            textoExacto(filtro, "tipoCocina", gastronomia.tipoCocina());
        } else if (filtros instanceof FiltrosTour tour) {
            textoExacto(filtro, "nivelDificultad", tour.nivelDificultad());
            if (tour.duracionMaxHoras() != null) {
                filtro.append(DETALLE + "duracionHoras", new Document("$lte", new Decimal128(tour.duracionMaxHoras())));
            }
        } else if (filtros instanceof FiltrosAlquilerEquipo alquiler) {
            textoExacto(filtro, "tipoVehiculoEquipo", alquiler.tipoVehiculoEquipo());
        }
    }

    /**
     * Valor completo sin distinguir mayúsculas (sí tildes): regex anclada ({@code \z}, no {@code $}, que también
     * aceptaría un salto de línea final), escapada y con la opción {@code i}. No se usa la colación «es» porque la
     * agregación solo la lleva al ordenar por nombre, y con ella cambiaría la regla.
     */
    private static void textoExacto(Document filtro, String campo, String valor) {
        if (valor != null) {
            filtro.append(DETALLE + campo, regexExacta(valor));
        }
    }

    private static Document regexExacta(String valor) {
        return new Document("$regex", "^" + escaparRegex(valor) + "\\z").append("$options", "i");
    }

    /** Decisión 5: los atractivos no se filtran por estado operativo. */
    private static Document filtroAtractivos(CriteriosBusqueda criterios) {
        return filtrosComunes(new Document(), criterios, "nombre");
    }

    /** Decisión 6 (accesibilidad en ambos) y texto sobre nombre y descripción, sin mayúsculas ni tildes (DEC-274). */
    private static Document filtrosComunes(Document filtro, CriteriosBusqueda criterios, String campoNombre) {
        if (!criterios.accesibilidad().isEmpty()) {
            filtro.append("condicionesAccesibilidad", new Document("$all",
                    criterios.accesibilidad().stream().map(Enum::name).sorted().toList()));
        }
        if (criterios.texto() != null) {
            Document regex = new Document("$regex", TextoBusqueda.patronSinTildes(criterios.texto()))
                    .append("$options", "i");
            filtro.append("$or", List.of(new Document(campoNombre, regex), new Document("descripcion", regex)));
        }
        return filtro;
    }

    /** El texto del usuario se trata como literal: se escapan los metacaracteres de las expresiones regulares. */
    static String escaparRegex(String texto) {
        return METACARACTERES_REGEX.matcher(texto).replaceAll("\\\\$0");
    }

    /**
     * El precio de la tarjeta es «desde»: con filtros de unidad (D1), el menor precioNoche de las unidades que los
     * cumplen, con las mismas condiciones del $elemMatch; sin ellos, precioBase. El $sort ordena por este mismo
     * campo, así que el orden por precio usa el valor que se muestra.
     */
    private static Document proyeccionServicio(CriteriosBusqueda criterios) {
        Object precio = "$precioBase";
        if (criterios.filtrosCategoria() instanceof FiltrosAlojamiento alojamiento && alojamiento.filtraUnidades()) {
            Document cumplen = new Document("$filter", new Document("input", "$detalleCategoria.unidades")
                    .append("as", "u")
                    .append("cond", CondicionesUnidad.de(alojamiento, criterios.precioMaximo()).expresion("u")));
            precio = new Document("$min", new Document("$map", new Document("input", cumplen)
                    .append("as", "u").append("in", "$$u.precioNoche")));
        }
        return proyeccionComun("Servicio", "$nombreComercial").append("precio", precio);
    }

    private static Document proyeccionAtractivo() {
        return proyeccionComun("Atractivo", "$nombre")
                .append("estadoOperativo", 1);
    }

    /** Solo los campos del resultado: el $sort y el $facet trabajan sobre documentos pequeños (límite de 100 MB). */
    private static Document proyeccionComun(String tipo, String nombre) {
        return new Document("tipo", new Document("$literal", tipo))
                .append("nombre", nombre)
                .append("categoria", 1)
                .append("descripcion", 1)
                .append("imagen", new Document("$first", "$imagenes"))
                .append("coordenadas", "$coordenadas.coordinates")
                .append("distanciaMetros", 1)
                .append("condicionesAccesibilidad", 1);
    }

    private static Document orden(OrdenBusqueda orden) {
        return switch (orden) {
            case Distancia -> new Document("distanciaMetros", 1).append("_id", 1);
            case PrecioAscendente -> new Document("sinPrecio", 1).append("precio", 1).append("_id", 1);
            case PrecioDescendente -> new Document("sinPrecio", 1).append("precio", -1).append("_id", 1);
            case Nombre -> new Document("nombre", 1).append("_id", 1);
        };
    }

    private static AggregationOperation etapa(String nombre, Document contenido) {
        Document etapa = new Document(nombre, contenido);
        return contexto -> etapa;
    }

    /** Mapeo explícito: coordinates[0] es la longitud y coordinates[1] la latitud (GeoJSON). */
    static ResultadoOferta aDominio(ResultadoOfertaDocumento documento) {
        Coordenada coordenada = new Coordenada(documento.coordenadas().get(0), documento.coordenadas().get(1));
        Double distanciaKm = documento.distanciaMetros() == null ? null : documento.distanciaMetros() / 1000.0;
        TipoOferta tipo = TipoOferta.valueOf(documento.tipo());
        return new ResultadoOferta(tipo, documento.id(), documento.nombre(), documento.categoria(),
                documento.descripcion(), tipo == TipoOferta.Servicio ? documento.precio() : null, documento.imagen(),
                coordenada, distanciaKm, ServicioDocumentoMapper.accesibilidad(documento.condicionesAccesibilidad()),
                tipo == TipoOferta.Atractivo && documento.estadoOperativo() != null
                        ? EstadoOperativo.valueOf(documento.estadoOperativo()) : null);
    }
}
