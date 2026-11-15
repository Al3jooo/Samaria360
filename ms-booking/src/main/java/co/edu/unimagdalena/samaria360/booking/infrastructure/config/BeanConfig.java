package co.edu.unimagdalena.samaria360.booking.infrastructure.config;

import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ConsultarAsistentesUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPrestadorPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxModificacionesPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.AsistentesPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ContactosTuristasPort;
import co.edu.unimagdalena.samaria360.booking.application.service.GestionarOfertaPrestadorService;
import co.edu.unimagdalena.samaria360.booking.application.service.ConsultarAsistentesService;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ActualizarCuentaUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.CancelarReservaUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ResumirDisponibilidadUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ReservarServicioUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.CuentaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OfertaMaterializadaPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ReservaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.application.service.ActualizarCuentaService;
import co.edu.unimagdalena.samaria360.booking.application.service.CancelarReservaService;
import co.edu.unimagdalena.samaria360.booking.application.service.CompletarReservasService;
import co.edu.unimagdalena.samaria360.booking.application.service.ConsultarReservasService;
import co.edu.unimagdalena.samaria360.booking.application.service.DisponibilidadService;
import co.edu.unimagdalena.samaria360.booking.application.service.MaterializarInventarioService;
import co.edu.unimagdalena.samaria360.booking.application.service.ReservarServicioService;
import co.edu.unimagdalena.samaria360.booking.application.service.VerificarSesionService;
import co.edu.unimagdalena.samaria360.booking.domain.model.HorarioReservas;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReserva;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaSesion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import co.edu.unimagdalena.samaria360.booking.application.port.in.CompletarReservasUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.PublicadorEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.service.PublicarEventosService;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReintento;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/** Registra como beans las clases de domain y application, que no llevan anotaciones de Spring. */
@Configuration
public class BeanConfig {

    /** Check-in y check-out de los alojamientos: los usan las reglas y la respuesta de la disponibilidad. */
    @Bean
    public HorarioReservas horarioReservas(ReservasPropiedades propiedades) {
        return new HorarioReservas(propiedades.horaCheckIn(), propiedades.horaCheckOut());
    }

    @Bean
    public PoliticaReserva politicaReserva(HorarioReservas horario) {
        return new PoliticaReserva(horario);
    }

    /** DEC-135: materializa la oferta del catálogo bajo demanda; lo usan la disponibilidad, el resumen y reservar. */
    @Bean
    public MaterializarInventarioService materializarInventarioService(CatalogoPort catalogo,
                                                                       InventarioPort inventario,
                                                                       OfertaMaterializadaPort marcas,
                                                                       RelojPort reloj) {
        return new MaterializarInventarioService(catalogo, inventario, marcas, reloj);
    }

    @Bean
    public ReservarServicioService reservarServicioService(ReservaRepositoryPort reservas, InventarioPort inventario,
                                                           CatalogoPort catalogo,
                                                           MaterializarInventarioService materializacion,
                                                           RelojPort reloj, PoliticaReserva politica,
                                                           OutboxEventosPort outbox) {
        return new ReservarServicioService(reservas, inventario, catalogo, materializacion, reloj, politica, outbox);
    }

    /** El que reciben los adaptadores de entrada: las fases del servicio coordinadas con las transacciones. */
    @Bean
    @Primary
    public ReservarServicioUseCase reservarServicioUseCase(ReservarServicioService servicio,
                                                           PlatformTransactionManager transacciones) {
        return new ReservarServicioTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    @Bean
    public CancelarReservaService cancelarReservaService(ReservaRepositoryPort reservas, InventarioPort inventario,
                                                         RelojPort reloj, OutboxEventosPort outbox) {
        return new CancelarReservaService(reservas, inventario, reloj, outbox);
    }

    @Bean
    @Primary
    public CancelarReservaUseCase cancelarReservaUseCase(CancelarReservaService servicio,
                                                         PlatformTransactionManager transacciones) {
        return new CancelarReservaTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    @Bean
    public ConsultarReservasService consultarReservasService(ReservaRepositoryPort reservas) {
        return new ConsultarReservasService(reservas);
    }

    @Bean
    public DisponibilidadService disponibilidadService(InventarioPort inventario,
                                                       MaterializarInventarioService materializacion,
                                                       RelojPort reloj, HorarioReservas horario) {
        return new DisponibilidadService(inventario, materializacion, reloj, horario);
    }

    /** El que recibe el adaptador de entrada: el resumen con un plazo total para materializar (DEC-155). */
    @Bean
    @Primary
    public ResumirDisponibilidadUseCase resumirDisponibilidadUseCase(
            DisponibilidadService servicio,
            @Value("${samaria360.disponibilidad.plazo-materializacion-resumen:2500ms}") Duration plazo) {
        return new ResumenDisponibilidadConPlazo(servicio, plazo);
    }

    /** RF52: un único UPDATE condicional más los eventos de las que cambiaron (ADR-011). */
    @Bean
    public CompletarReservasService completarReservasService(ReservaRepositoryPort reservas, RelojPort reloj,
                                                             OutboxEventosPort outbox) {
        return new CompletarReservasService(reservas, reloj, outbox);
    }

    /** El que recibe el programador: el UPDATE y los eventos en una sola transacción (DEC-291). */
    @Bean
    @Primary
    public CompletarReservasUseCase completarReservasUseCase(CompletarReservasService servicio,
                                                             PlatformTransactionManager transacciones) {
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        return () -> transaccion.execute(estado -> servicio.completar());
    }

    @Bean
    public PoliticaReintento politicaReintento() {
        return new PoliticaReintento();
    }

    /** Relevo del outbox hacia RabbitMQ (ADR-011, DEC-291). */
    @Bean
    public PublicarEventosUseCase publicarEventosUseCase(OutboxEventosPort outbox, PublicadorEventosPort publicador,
                                                         RelojPort reloj, PoliticaReintento politica) {
        return new PublicarEventosService(outbox, publicador, reloj, politica);
    }

    @Bean
    public PoliticaSesion politicaSesion() {
        return new PoliticaSesion();
    }

    @Bean
    public VerificarSesionUseCase verificarSesionUseCase(CuentaRepositoryPort cuentas, PoliticaSesion politica) {
        return new VerificarSesionService(cuentas, politica);
    }

    /** ADR-008: la proyección de cuentas es un solo UPSERT condicional; la transacción la abre el repositorio. */
    @Bean
    public ActualizarCuentaUseCase actualizarCuentaUseCase(CuentaRepositoryPort cuentas, RelojPort reloj) {
        return new ActualizarCuentaService(cuentas, reloj);
    }

    /** RF19 (DEC-310): catálogo y materialización fuera de la transacción; el cambio y el outbox, dentro. */
    @Bean
    public GestionarOfertaPrestadorUseCase gestionarOfertaPrestadorUseCase(CatalogoPort catalogo,
                                                                           MaterializarInventarioService materializar,
                                                                           InventarioPrestadorPort inventario,
                                                                           OutboxModificacionesPort outbox,
                                                                           RelojPort reloj,
                                                                           PlatformTransactionManager transacciones) {
        return new OfertaPrestadorTransaccional(new GestionarOfertaPrestadorService(catalogo, materializar, inventario,
                outbox, reloj), new TransactionTemplate(transacciones));
    }

    /** RF42 (DEC-312). */
    @Bean
    public ConsultarAsistentesUseCase consultarAsistentesUseCase(AsistentesPort asistentes,
                                                                 ContactosTuristasPort contactos) {
        return new ConsultarAsistentesService(asistentes, contactos);
    }
}
