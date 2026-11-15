package co.edu.unimagdalena.samaria360.catalog.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Elemento de la lista de resultados de WF-05: un servicio o un atractivo con los datos de su tarjeta y del
 * marcador del mapa (RF23).
 *
 * @param categoria       la categoría del servicio o el texto libre del atractivo
 * @param precio          nulo en los atractivos
 * @param distanciaKm     solo si la búsqueda tiene ubicación (RF38)
 * @param estadoOperativo solo en los atractivos (RF33)
 * @param calificacion    promedio visible de RF17 (de 1 a 5) que llega de ms-analytics (DEC-302); nulo sin calificaciones
 * @param cantidadCalificaciones cuántas calificaciones visibles tiene; nulo si no se conoce
 * @param lecturaOcupacion la última ocupación registrada y el límite (solo atractivos; DEC-304)
 * @param afluencia       el nivel vigente (RF39), calculado con la hora actual por el caso de uso; nulo sin lectura del día
 */
public record ResultadoOferta(TipoOferta tipo, String id, String nombre, String categoria, String descripcionCorta,
                              BigDecimal precio, String imagenPrincipal, Coordenada coordenada, Double distanciaKm,
                              Set<CondicionAccesibilidad> accesibilidad, EstadoOperativo estadoOperativo,
                              Double calificacion, Long cantidadCalificaciones, Afluencia.Lectura lecturaOcupacion,
                              Afluencia afluencia) {

    /** Con la calificación y sin ocupación (los resultados anteriores a la iteración J). */
    public ResultadoOferta(TipoOferta tipo, String id, String nombre, String categoria, String descripcionCorta,
                           BigDecimal precio, String imagenPrincipal, Coordenada coordenada, Double distanciaKm,
                           Set<CondicionAccesibilidad> accesibilidad, EstadoOperativo estadoOperativo,
                           Double calificacion, Long cantidadCalificaciones) {
        this(tipo, id, nombre, categoria, descripcionCorta, precio, imagenPrincipal, coordenada, distanciaKm,
                accesibilidad, estadoOperativo, calificacion, cantidadCalificaciones, null, null);
    }

    /** El mismo resultado con el nivel de afluencia vigente (RF39). */
    public ResultadoOferta conAfluencia(java.time.Instant ahora) {
        Afluencia vigente = Afluencia.calcular(lecturaOcupacion, ahora).orElse(null);
        return new ResultadoOferta(tipo, id, nombre, categoria, descripcionCorta, precio, imagenPrincipal, coordenada,
                distanciaKm, accesibilidad, estadoOperativo, calificacion, cantidadCalificaciones, lecturaOcupacion,
                vigente);
    }

    /** Sin calificación (los resultados armados en memoria y los anteriores a la iteración H). */
    public ResultadoOferta(TipoOferta tipo, String id, String nombre, String categoria, String descripcionCorta,
                           BigDecimal precio, String imagenPrincipal, Coordenada coordenada, Double distanciaKm,
                           Set<CondicionAccesibilidad> accesibilidad, EstadoOperativo estadoOperativo) {
        this(tipo, id, nombre, categoria, descripcionCorta, precio, imagenPrincipal, coordenada, distanciaKm,
                accesibilidad, estadoOperativo, null, null, null, null);
    }

    public static final int LONGITUD_DESCRIPCION_CORTA = 140;

    public ResultadoOferta {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(id, "id");
        Validaciones.texto(nombre, "nombre");
        Objects.requireNonNull(coordenada, "coordenada");
        accesibilidad = accesibilidad == null ? Set.of() : Set.copyOf(accesibilidad);
        descripcionCorta = acortar(descripcionCorta);
        if (tipo == TipoOferta.Atractivo && precio != null) {
            throw new IllegalArgumentException("Los atractivos no tienen precio");
        }
        if (tipo == TipoOferta.Servicio && estadoOperativo != null) {
            throw new IllegalArgumentException("El estado operativo es solo de los atractivos");
        }
    }

    public static ResultadoOferta deServicio(ServicioComercial servicio, Double distanciaKm) {
        return deServicio(servicio, distanciaKm, servicio.getPrecioBase().orElse(null));
    }

    /** @param precio el precio «desde» que se muestra (con filtros de unidad, el de las unidades que cumplen) */
    public static ResultadoOferta deServicio(ServicioComercial servicio, Double distanciaKm, BigDecimal precio) {
        return new ResultadoOferta(TipoOferta.Servicio, servicio.getId().orElseThrow(), servicio.getNombreComercial(),
                servicio.getCategoria().name(), servicio.getDescripcion().orElse(null),
                precio, servicio.getImagenes().stream().findFirst().orElse(null),
                servicio.getCoordenada(), distanciaKm, servicio.getCondicionesAccesibilidad(), null);
    }

    public static ResultadoOferta deAtractivo(AtractivoPublico atractivo, Double distanciaKm) {
        return new ResultadoOferta(TipoOferta.Atractivo, atractivo.getId().orElseThrow(), atractivo.getNombre(),
                atractivo.getCategoria().orElse(null), atractivo.getDescripcion().orElse(null), null,
                atractivo.getImagenes().stream().findFirst().orElse(null), atractivo.getCoordenada(),
                distanciaKm, atractivo.getCondicionesAccesibilidad(), atractivo.getEstadoOperativo().orElse(null));
    }

    /** Hasta 140 caracteres, cortando en un límite de palabra y terminando en «…». */
    static String acortar(String descripcion) {
        if (descripcion == null || descripcion.length() <= LONGITUD_DESCRIPCION_CORTA) {
            return descripcion;
        }
        String corte = descripcion.substring(0, LONGITUD_DESCRIPCION_CORTA - 1);
        int espacio = corte.lastIndexOf(' ');
        if (espacio > LONGITUD_DESCRIPCION_CORTA / 2) {
            corte = corte.substring(0, espacio);
        }
        return corte.stripTrailing() + "…";
    }
}
