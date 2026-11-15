package co.edu.unimagdalena.samaria360.catalog.application.port.in;

import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.PrestadorNoHabilitadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;

import java.util.List;
import java.util.UUID;

/**
 * CU «Registrar servicio» (RF09, RF10, RF37; parte de RF27 y RF58). El servicio queda Pendiente de aprobación
 * (DEC-124), con el prestador, su RNT y la copia de su estado tomados de la proyección {@code cuentas} (DEC-125,
 * DEC-126). El rol Prestador lo exige la infraestructura.
 */
public interface RegistrarServicioUseCase {

    /**
     * @throws PrestadorNoHabilitadoException si la cuenta no está en la proyección, no está Activa o no tiene RNT
     * @throws ContenidoInvalidoException     con un motivo por cada campo inválido
     */
    ServicioComercial registrar(UUID prestadorId, BorradorServicio borrador);

    /**
     * Registro con las fotos del formulario (WF-08, DEC-318): de 1 a 5 imágenes, guardadas en GridFS antes del servicio;
     * si algo falla, se borran las guardadas.
     *
     * @throws PrestadorNoHabilitadoException si la cuenta no puede registrar servicios
     * @throws ContenidoInvalidoException     con un motivo por cada campo inválido, también los de las imágenes
     */
    ServicioComercial registrar(UUID prestadorId, BorradorServicio borrador, List<ImagenSubida> imagenes);
}
