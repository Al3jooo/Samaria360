package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.OfertaServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.AlmacenImagenesPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.GestionServiciosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.HistorialModificacionesPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaServicioPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.GaleriaServicioException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FormatoImagen;
import co.edu.unimagdalena.samaria360.catalog.domain.model.IdentificadorObjeto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroAtractivo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaImagen;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF41, RF19 (plantilla) y RF48 en ms-catalog (DEC-311, DEC-313). Un cambio de ms-booking de un servicio que no es del
 * prestador del evento, o que ya no existe, se ignora. La galería: solo servicios Publicados del Prestador, de 1 a 5
 * imágenes JPEG o PNG de hasta 5 MB (las reglas de RF06); la imagen se guarda en GridFS antes de agregarla y, si no se
 * pudo agregar, se borra (compensación, como ADR-009).
 */
public class OfertaServicioService implements OfertaServicioUseCase {

    private final GestionServiciosPort servicios;
    private final OfertaServicioPort oferta;
    private final HistorialModificacionesPort historial;
    private final AlmacenImagenesPort imagenes;
    private final RelojPort reloj;

    public OfertaServicioService(GestionServiciosPort servicios, OfertaServicioPort oferta,
                                 HistorialModificacionesPort historial, AlmacenImagenesPort imagenes, RelojPort reloj) {
        this.servicios = Objects.requireNonNull(servicios);
        this.oferta = Objects.requireNonNull(oferta);
        this.historial = Objects.requireNonNull(historial);
        this.imagenes = Objects.requireNonNull(imagenes);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public void registrarCambio(CambioRecibido c) {
        ModificacionServicio.Tipo tipo = ModificacionServicio.Tipo.valueOf(c.tipo());
        if (tipo == ModificacionServicio.Tipo.Precio || !IdentificadorObjeto.esValido(c.idServicio())) {
            throw new IllegalArgumentException("OfertaModificada fuera del contrato");
        }
        var servicio = servicios.buscar(c.idServicio()).filter(s -> s.getPrestadorId().equals(c.idPrestador()));
        if (servicio.isEmpty()) {
            return;
        }
        if (tipo == ModificacionServicio.Tipo.Plantilla) {
            oferta.fijarOferta(c.idServicio(), c.idPrestador(), c.diasOperacion(),
                    c.salidas() == null ? List.of() : c.salidas());
        }
        String detalle = c.fecha() == null ? null : c.fecha() + (c.horaInicio() == null ? "" : " " + c.horaInicio())
                + (c.idUnidad() == null ? "" : " · " + nombreUnidad(servicio.get(), c.idUnidad()));
        historial.registrar(new ModificacionServicio(c.idServicio(), c.idPrestador(),
                servicio.get().getNombreComercial(), tipo, detalle, c.valorAnterior(), c.valorNuevo(), c.ocurridoEn(),
                c.idEvento()));
    }

    private static String nombreUnidad(ServicioComercial servicio, String idUnidad) {
        if (servicio.getDetalle() instanceof co.edu.unimagdalena.samaria360.catalog.domain.model.DetalleAlojamiento d) {
            return d.unidades().stream().filter(u -> u.idUnidad().equals(idUnidad)).map(u -> u.nombre()).findFirst()
                    .orElse(idUnidad);
        }
        return idUnidad;
    }

    @Override
    public PaginaResultados<ModificacionServicio> historial(UUID prestador, String idServicio, int pagina) {
        if (idServicio != null && !IdentificadorObjeto.esValido(idServicio)) {
            throw new ContenidoInvalidoException(Map.of("servicio", MotivoInvalido.FORMATO));
        }
        if (pagina < 0) {
            throw new ContenidoInvalidoException(Map.of("pagina", MotivoInvalido.FORMATO));
        }
        Instant desde = reloj.ahora().minus(ModificacionServicio.DIAS_HISTORIAL, ChronoUnit.DAYS);
        return historial.listar(prestador, idServicio, desde, pagina, TAMANO_HISTORIAL);
    }

    /** Un servicio del Prestador; otro prestador lo ve como inexistente (DEC-143). Solo Publicado (RF48). */
    private ServicioComercial publicado(UUID prestador, String id) {
        if (!IdentificadorObjeto.esValido(id)) {
            throw new ServicioNoEncontradoException(id);
        }
        ServicioComercial servicio = servicios.buscar(id).filter(s -> s.getPrestadorId().equals(prestador))
                .orElseThrow(() -> new ServicioNoEncontradoException(id));
        if (servicio.getEstadoPublicacion() != EstadoPublicacion.Publicado) {
            throw new EstadoServicioCambiadoException(servicio.getEstadoPublicacion());
        }
        return servicio;
    }

    @Override
    public List<String> agregarImagen(UUID prestador, String idServicio, ImagenSubida imagen) {
        ServicioComercial servicio = publicado(prestador, idServicio);
        PoliticaImagen.motivo(imagen).ifPresent(motivo -> {
            throw new ContenidoInvalidoException(Map.of("imagen", motivo));
        });
        if (servicio.getImagenes().size() >= PoliticaImagen.MAXIMO) {
            throw new GaleriaServicioException(GaleriaServicioException.LIMITE_IMAGENES);
        }
        FormatoImagen formato = FormatoImagen.detectar(imagen.contenido()).orElseThrow();
        String id = imagenes.nuevoId();
        imagenes.guardar(id, formato, imagen.nombre(), imagen.contenido());
        if (!oferta.agregarImagen(idServicio, prestador, RegistroAtractivo.RUTA_IMAGENES + id)) {
            imagenes.borrar(id);
            publicado(prestador, idServicio);
            throw new GaleriaServicioException(GaleriaServicioException.LIMITE_IMAGENES);
        }
        return publicado(prestador, idServicio).getImagenes();
    }

    @Override
    public List<String> quitarImagen(UUID prestador, String idServicio, String ruta) {
        ServicioComercial servicio = publicado(prestador, idServicio);
        if (ruta == null || !servicio.getImagenes().contains(ruta)) {
            throw new ContenidoInvalidoException(Map.of("ruta", MotivoInvalido.NO_PERMITIDO));
        }
        if (servicio.getImagenes().size() <= PoliticaImagen.MINIMO
                || !oferta.quitarImagen(idServicio, prestador, ruta)) {
            throw new GaleriaServicioException(GaleriaServicioException.IMAGEN_MINIMA);
        }
        if (ruta.startsWith(RegistroAtractivo.RUTA_IMAGENES)) {
            imagenes.borrar(ruta.substring(RegistroAtractivo.RUTA_IMAGENES.length()));
        }
        return publicado(prestador, idServicio).getImagenes();
    }
}
