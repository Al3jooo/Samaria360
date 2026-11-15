package co.edu.unimagdalena.samaria360.catalog.infrastructure.config;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.OfertaServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.HistorialModificacionesPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaServicioPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.OfertaServicioService;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarAtractivoUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarCuentaUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarAtractivoUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarAtractivosGestorUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarAgendaUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarImagenUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.GestionarEventosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.RegistrarAtractivoUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.AlmacenImagenesPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.AtractivoRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.EventoRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.GestionAtractivosPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.ActualizarAtractivoService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarAgendaService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarAtractivoService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarAtractivosGestorService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarImagenService;
import co.edu.unimagdalena.samaria360.catalog.application.service.GestionarEventosService;
import co.edu.unimagdalena.samaria360.catalog.application.service.RegistrarAtractivoService;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.AdministrarServiciosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarMisServiciosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.RegistrarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.BuscarOfertaUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarOpcionesFiltroUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.GestionarAvisosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.GestionarFavoritosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.GestionarPreguntasFrecuentesUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.HistorialBusquedasUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.AvisoRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.CuentaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.FavoritoRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.GestionServiciosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.HistorialBusquedaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaReferenciadaPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OpcionesFiltroRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.PreguntaFrecuenteRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.ServicioRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.VisibilidadPrestadorPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.ActualizarCuentaService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ActualizarServicioService;
import co.edu.unimagdalena.samaria360.catalog.application.service.AdministrarServiciosService;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarMisServiciosService;
import co.edu.unimagdalena.samaria360.catalog.application.service.RegistrarServicioService;
import co.edu.unimagdalena.samaria360.catalog.application.service.BuscarOfertaService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarOpcionesFiltroService;
import co.edu.unimagdalena.samaria360.catalog.application.service.ConsultarServicioService;
import co.edu.unimagdalena.samaria360.catalog.application.service.GestionarAvisosService;
import co.edu.unimagdalena.samaria360.catalog.application.service.GestionarFavoritosService;
import co.edu.unimagdalena.samaria360.catalog.application.service.GestionarPreguntasFrecuentesService;
import co.edu.unimagdalena.samaria360.catalog.application.service.HistorialBusquedasService;
import co.edu.unimagdalena.samaria360.catalog.application.service.VerificarSesionService;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaSesion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra como beans las clases de application, que no llevan anotaciones de Spring. */
@Configuration
public class BeanConfig {

    @Bean
    public BuscarOfertaUseCase buscarOfertaUseCase(OfertaRepositoryPort ofertas, RelojPort reloj) {
        return new BuscarOfertaService(ofertas, reloj);
    }

    @Bean
    public ConsultarOpcionesFiltroUseCase consultarOpcionesFiltroUseCase(OpcionesFiltroRepositoryPort opciones) {
        return new ConsultarOpcionesFiltroService(opciones);
    }

    @Bean
    public ConsultarServicioUseCase consultarServicioUseCase(ServicioRepositoryPort servicios) {
        return new ConsultarServicioService(servicios);
    }

    @Bean
    public PoliticaSesion politicaSesion() {
        return new PoliticaSesion();
    }

    @Bean
    public VerificarSesionUseCase verificarSesionUseCase(CuentaRepositoryPort cuentas, PoliticaSesion politica) {
        return new VerificarSesionService(cuentas, politica);
    }

    @Bean
    public ActualizarCuentaUseCase actualizarCuentaUseCase(CuentaRepositoryPort cuentas,
                                                           VisibilidadPrestadorPort visibilidad) {
        return new ActualizarCuentaService(cuentas, visibilidad);
    }

    @Bean
    public GestionarPreguntasFrecuentesUseCase gestionarPreguntasFrecuentesUseCase(
            PreguntaFrecuenteRepositoryPort preguntas, RelojPort reloj) {
        return new GestionarPreguntasFrecuentesService(preguntas, reloj);
    }

    @Bean
    public GestionarAvisosUseCase gestionarAvisosUseCase(AvisoRepositoryPort avisos, RelojPort reloj) {
        return new GestionarAvisosService(avisos, reloj);
    }

    @Bean
    public GestionarFavoritosUseCase gestionarFavoritosUseCase(FavoritoRepositoryPort favoritos,
                                                               OfertaReferenciadaPort referencias, RelojPort reloj) {
        return new GestionarFavoritosService(favoritos, referencias, reloj);
    }

    @Bean
    public HistorialBusquedasUseCase historialBusquedasUseCase(HistorialBusquedaRepositoryPort historial,
                                                               RelojPort reloj) {
        return new HistorialBusquedasService(historial, reloj);
    }

    @Bean
    public RegistrarServicioUseCase registrarServicioUseCase(GestionServiciosPort servicios,
                                                             CuentaRepositoryPort cuentas, RelojPort reloj,
                                                             AlmacenImagenesPort imagenes) {
        return new RegistrarServicioService(servicios, cuentas, reloj, imagenes);
    }

    @Bean
    public ConsultarMisServiciosUseCase consultarMisServiciosUseCase(GestionServiciosPort servicios) {
        return new ConsultarMisServiciosService(servicios);
    }

    @Bean
    public ActualizarServicioUseCase actualizarServicioUseCase(GestionServiciosPort servicios,
                                                               HistorialModificacionesPort historial, RelojPort reloj,
                                                               org.springframework.data.mongodb.MongoTransactionManager transacciones) {
        // RF41 (DEC-311): el cambio de precio y su entrada del historial, en una transacción.
        return HistorialTransaccional.edicion(new ActualizarServicioService(servicios, historial, reloj),
                new org.springframework.transaction.support.TransactionTemplate(transacciones));
    }

    @Bean
    public RegistrarAtractivoUseCase registrarAtractivoUseCase(GestionAtractivosPort atractivos,
                                                               AlmacenImagenesPort imagenes, RelojPort reloj) {
        return new RegistrarAtractivoService(atractivos, imagenes, reloj);
    }

    @Bean
    public ActualizarAtractivoUseCase actualizarAtractivoUseCase(GestionAtractivosPort atractivos, RelojPort reloj) {
        return new ActualizarAtractivoService(atractivos, reloj);
    }

    @Bean
    public ConsultarAtractivosGestorUseCase consultarAtractivosGestorUseCase(GestionAtractivosPort atractivos) {
        return new ConsultarAtractivosGestorService(atractivos);
    }

    @Bean
    public ConsultarAtractivoUseCase consultarAtractivoUseCase(AtractivoRepositoryPort atractivos) {
        return new ConsultarAtractivoService(atractivos);
    }

    @Bean
    public ConsultarImagenUseCase consultarImagenUseCase(AlmacenImagenesPort imagenes) {
        return new ConsultarImagenService(imagenes);
    }

    @Bean
    public GestionarEventosUseCase gestionarEventosUseCase(EventoRepositoryPort eventos, RelojPort reloj) {
        return new GestionarEventosService(eventos, reloj);
    }

    @Bean
    public ConsultarAgendaUseCase consultarAgendaUseCase(EventoRepositoryPort eventos, RelojPort reloj) {
        return new ConsultarAgendaService(eventos, reloj);
    }

    /** Aprobación de servicios (RF21): la decisión y su evento en una transacción de MongoDB (DEC-293). */
    @Bean
    public AdministrarServiciosUseCase administrarServiciosUseCase(GestionServiciosPort servicios,
                                                                   CuentaRepositoryPort cuentas, RelojPort reloj,
                                                                   OutboxEventosPort outbox,
                                                                   org.springframework.data.mongodb.MongoTransactionManager transacciones) {
        return new AdministrarServiciosTransaccional(new AdministrarServiciosService(servicios, cuentas, reloj, outbox),
                new org.springframework.transaction.support.TransactionTemplate(transacciones));
    }

    /** RF41, RF19 (plantilla) y RF48 (DEC-311, DEC-313). */
    @Bean
    public OfertaServicioUseCase ofertaServicioUseCase(GestionServiciosPort servicios, OfertaServicioPort oferta,
                                                       HistorialModificacionesPort historial,
                                                       AlmacenImagenesPort imagenes, RelojPort reloj,
                                                       org.springframework.data.mongodb.MongoTransactionManager transacciones) {
        return HistorialTransaccional.oferta(new OfertaServicioService(servicios, oferta, historial, imagenes, reloj),
                new org.springframework.transaction.support.TransactionTemplate(transacciones));
    }
}
