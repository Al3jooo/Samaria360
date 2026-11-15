package co.edu.unimagdalena.samaria360.catalog.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Una modificación de precio, cupos u horarios de un servicio (RF41; DEC-311): la fecha y la hora, qué cambió y el valor
 * anterior frente al nuevo. Los precios salen de RF32 (ms-catalog); los cupos, las fechas, los horarios y la plantilla,
 * de {@code OfertaModificada} de ms-booking (RF19). {@code idEvento} deduplica los que llegan por RabbitMQ.
 */
public record ModificacionServicio(String idServicio, UUID prestadorId, String nombreServicio, Tipo tipo,
                                   String detalle, String valorAnterior, String valorNuevo, Instant fecha,
                                   String idEvento) {

    public enum Tipo { Precio, Cupos, Apertura, Cierre, Plantilla }

    public static final int DIAS_HISTORIAL = 30;

    public ModificacionServicio {
        Objects.requireNonNull(idServicio, "idServicio");
        Objects.requireNonNull(prestadorId, "prestadorId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valorNuevo, "valorNuevo");
        Objects.requireNonNull(fecha, "fecha");
    }

    /**
     * Las modificaciones de precio de RF32: el {@code precioBase}, o el {@code precioNoche} de cada unidad que cambió en
     * un alojamiento. Sin cambios, ninguna.
     */
    public static List<ModificacionServicio> dePrecio(ServicioComercial antes, ServicioComercial despues, Instant ahora) {
        List<ModificacionServicio> cambios = new ArrayList<>();
        String id = despues.getId().orElseThrow();
        if (despues.getDetalle() instanceof DetalleAlojamiento nuevo
                && antes.getDetalle() instanceof DetalleAlojamiento viejo) {
            Map<String, UnidadAlojamiento> previas = viejo.unidades().stream()
                    .collect(Collectors.toMap(UnidadAlojamiento::idUnidad, Function.identity()));
            for (UnidadAlojamiento unidad : nuevo.unidades()) {
                UnidadAlojamiento previa = previas.get(unidad.idUnidad());
                if (previa != null && previa.precioNoche().compareTo(unidad.precioNoche()) != 0) {
                    cambios.add(new ModificacionServicio(id, despues.getPrestadorId(), despues.getNombreComercial(),
                            Tipo.Precio, unidad.nombre(), texto(previa.precioNoche()), texto(unidad.precioNoche()),
                            ahora, null));
                }
            }
            return cambios;
        }
        BigDecimal viejo = antes.getPrecioBase().orElse(null);
        BigDecimal nuevo = despues.getPrecioBase().orElse(null);
        if (nuevo != null && (viejo == null || viejo.compareTo(nuevo) != 0)) {
            cambios.add(new ModificacionServicio(id, despues.getPrestadorId(), despues.getNombreComercial(), Tipo.Precio,
                    null, viejo == null ? null : texto(viejo), texto(nuevo), ahora, null));
        }
        return cambios;
    }

    private static String texto(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
