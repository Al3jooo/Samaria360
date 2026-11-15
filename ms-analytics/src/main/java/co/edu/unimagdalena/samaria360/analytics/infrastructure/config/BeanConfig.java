package co.edu.unimagdalena.samaria360.analytics.infrastructure.config;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.ConsultasProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.TablerosRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.service.TablerosService;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.OcupacionProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ReportarUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CalificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PromedioRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PublicadorEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReporteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.service.CalificacionesService;
import co.edu.unimagdalena.samaria360.analytics.application.service.PublicarEventosService;
import co.edu.unimagdalena.samaria360.analytics.application.service.ReportesService;
import co.edu.unimagdalena.samaria360.analytics.domain.service.PoliticaReintento;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarAuditoriaUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.EnviarCorreosPendientesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.GestionarNotificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ProcesarEventoUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.AuditoriaRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ContactoPrestadorPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPendienteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.EventosProcesadosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.NotificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReservaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.service.ConsultarAuditoriaService;
import co.edu.unimagdalena.samaria360.analytics.application.service.EnviarCorreosPendientesService;
import co.edu.unimagdalena.samaria360.analytics.application.service.GestionarNotificacionesService;
import co.edu.unimagdalena.samaria360.analytics.application.service.ProcesarEventoService;
import co.edu.unimagdalena.samaria360.analytics.application.service.VerificarSesionService;
import co.edu.unimagdalena.samaria360.analytics.domain.service.PoliticaSesion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/** Registra como beans las clases de domain y application, que no llevan anotaciones de Spring. */
@Configuration
@EnableScheduling
public class BeanConfig {

    @Bean
    public MongoTransactionManager transaccionesMongo(MongoDatabaseFactory fabrica) {
        return new MongoTransactionManager(fabrica);
    }

    /** El que recibe el listener: el evento y su deduplicación en una transacción de MongoDB (ADR-011). */
    @Bean
    public ProcesarEventoUseCase procesarEventoUseCase(EventosProcesadosPort procesados, CuentaProyeccionPort cuentas,
                                                       AuditoriaRepositoryPort auditoria,
                                                       NotificacionRepositoryPort notificaciones,
                                                       ReservaProyeccionPort reservas,
                                                       CorreoPendienteRepositoryPort correos,
                                                       OcupacionProyeccionPort ocupaciones,
                                                       ConsultasProyeccionPort consultas, RelojPort reloj,
                                                       MongoTransactionManager transacciones) {
        return new ProcesarEventoTransaccional(new ProcesarEventoService(procesados, cuentas, auditoria, notificaciones,
                reservas, correos, ocupaciones, consultas, reloj), new TransactionTemplate(transacciones));
    }

    /** RF17, RF29 y RF56 (DEC-300): un solo servicio; las escrituras, en una transacción de MongoDB. */
    @Bean
    public CalificacionesService calificacionesService(CalificacionRepositoryPort calificaciones,
                                                       PromedioRepositoryPort promedios, ReservaProyeccionPort reservas,
                                                       CuentaProyeccionPort cuentas, AuditoriaRepositoryPort auditoria,
                                                       OutboxEventosPort outbox, RelojPort reloj) {
        return new CalificacionesService(calificaciones, promedios, reservas, cuentas, auditoria, outbox, reloj);
    }

    /**
     * Lo que reciben los controladores para calificar y moderar ({@code @Primary}): el mismo servicio, con cada
     * escritura en una transacción. Las consultas usan el servicio directamente.
     */
    @Bean
    @Primary
    public CalificacionesTransaccional calificacionesTransaccional(CalificacionesService servicio,
                                                                   MongoTransactionManager transacciones) {
        return new CalificacionesTransaccional(servicio, servicio, new TransactionTemplate(transacciones));
    }

    /** RF57 (DEC-301). */
    @Bean
    public ReportarUseCase reportarUseCase(ReporteRepositoryPort reportes, CuentaProyeccionPort cuentas,
                                           RelojPort reloj) {
        return new ReportesService(reportes, cuentas, reloj);
    }

    /** Relevo del outbox de analytics_db hacia RabbitMQ (PromedioActualizado; ADR-011, DEC-300). */
    @Bean
    public PublicarEventosUseCase publicarEventosUseCase(OutboxEventosPort outbox, PublicadorEventosPort publicador,
                                                         RelojPort reloj) {
        return new PublicarEventosService(outbox, publicador, reloj, new PoliticaReintento());
    }

    @Bean
    public PoliticaSesion politicaSesion() {
        return new PoliticaSesion();
    }

    @Bean
    public VerificarSesionUseCase verificarSesionUseCase(CuentaProyeccionPort cuentas, PoliticaSesion politica) {
        return new VerificarSesionService(cuentas, politica);
    }

    @Bean
    public GestionarNotificacionesUseCase gestionarNotificacionesUseCase(NotificacionRepositoryPort notificaciones) {
        return new GestionarNotificacionesService(notificaciones);
    }

    @Bean
    public ConsultarAuditoriaUseCase consultarAuditoriaUseCase(AuditoriaRepositoryPort auditoria) {
        return new ConsultarAuditoriaService(auditoria);
    }

    @Bean
    public EnviarCorreosPendientesUseCase enviarCorreosPendientesUseCase(CorreoPendienteRepositoryPort pendientes,
                                                                         ContactoPrestadorPort contactos,
                                                                         CorreoPort correo, RelojPort reloj) {
        return new EnviarCorreosPendientesService(pendientes, contactos, correo, reloj);
    }

    /** RF14, RF31 y RF64 (DEC-308). */
    @Bean
    public TablerosUseCase tablerosUseCase(TablerosRepositoryPort tableros, RelojPort reloj) {
        return new TablerosService(tableros, reloj);
    }
}
