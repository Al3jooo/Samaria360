package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.application.port.in.AdministrarCuentasUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.CambiarRolUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.GestionarPerfilPrestadorUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.GestionarPerfilUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.IniciarSesionUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RegistrarPrestadorUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RegistrarTuristaUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.out.CambioRolRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.CuentasAdminRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.DestinoCuentasPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.NotificadorCuentaPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PasswordHasherPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PerfilPrestadorRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PerfilRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RegistroRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.service.AdministrarCuentasService;
import co.edu.unimagdalena.samaria360.auth.application.service.CambiarRolService;
import co.edu.unimagdalena.samaria360.auth.application.service.ConsultarCuentasService;
import co.edu.unimagdalena.samaria360.auth.application.service.ConsultarPersonalService;
import co.edu.unimagdalena.samaria360.auth.application.service.ConsultarPaisesService;
import co.edu.unimagdalena.samaria360.auth.application.service.ConsultarUsuarioActualService;
import co.edu.unimagdalena.samaria360.auth.application.service.GestionarPerfilPrestadorService;
import co.edu.unimagdalena.samaria360.auth.application.service.GestionarPerfilService;
import co.edu.unimagdalena.samaria360.auth.application.service.IniciarSesionService;
import co.edu.unimagdalena.samaria360.auth.application.service.RegistrarPrestadorService;
import co.edu.unimagdalena.samaria360.auth.application.service.RegistrarTuristaService;
import co.edu.unimagdalena.samaria360.auth.application.service.PurgarOutboxService;
import co.edu.unimagdalena.samaria360.auth.application.service.RelevarCuentasService;
import co.edu.unimagdalena.samaria360.auth.application.service.VerificarSesionService;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaAcceso;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaAdministradores;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaBloqueo;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaReintento;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaSesion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/** Registra como beans las clases de domain y application, que no llevan anotaciones de Spring. */
@Configuration
public class BeanConfig {

    @Bean
    public PoliticaBloqueo politicaBloqueo() {
        return new PoliticaBloqueo();
    }

    @Bean
    public PoliticaAcceso politicaAcceso() {
        return new PoliticaAcceso();
    }

    @Bean
    public IniciarSesionService iniciarSesionService(UsuarioRepositoryPort usuarios, PasswordHasherPort hasher,
                                                     TokenProviderPort tokens, RelojPort reloj,
                                                     PoliticaBloqueo politica, PoliticaAcceso politicaAcceso) {
        return new IniciarSesionService(usuarios, hasher, tokens, reloj, politica, politicaAcceso);
    }

    /** El que reciben los adaptadores de entrada: el servicio puro envuelto en la transacción. */
    @Bean
    @Primary
    public IniciarSesionUseCase iniciarSesionUseCase(IniciarSesionService servicio) {
        return new IniciarSesionTransaccional(servicio);
    }

    @Bean
    public ConsultarUsuarioActualService consultarUsuarioActualService(UsuarioRepositoryPort usuarios) {
        return new ConsultarUsuarioActualService(usuarios);
    }

    @Bean
    public RegistrarTuristaService registrarTuristaService(RegistroRepositoryPort registros, PasswordHasherPort hasher,
                                                           NotificadorCuentaPort notificador, RelojPort reloj) {
        return new RegistrarTuristaService(registros, hasher, notificador, reloj);
    }

    @Bean
    public RegistrarPrestadorService registrarPrestadorService(RegistroRepositoryPort registros,
                                                               PasswordHasherPort hasher,
                                                               NotificadorCuentaPort notificador, RelojPort reloj) {
        return new RegistrarPrestadorService(registros, hasher, notificador, reloj);
    }

    /** Los que reciben los adaptadores de entrada: cada servicio envuelto en su transacción (con reintento por carrera). */
    @Bean
    @Primary
    public RegistrarTuristaUseCase registrarTuristaUseCase(RegistrarTuristaService servicio,
                                                           PlatformTransactionManager transacciones) {
        return new RegistrarTuristaTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    @Bean
    @Primary
    public RegistrarPrestadorUseCase registrarPrestadorUseCase(RegistrarPrestadorService servicio,
                                                               PlatformTransactionManager transacciones) {
        return new RegistrarPrestadorTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    @Bean
    public ConsultarPaisesService consultarPaisesService() {
        return new ConsultarPaisesService();
    }

    @Bean
    public PoliticaSesion politicaSesion() {
        return new PoliticaSesion();
    }

    @Bean
    public PoliticaReintento politicaReintento() {
        return new PoliticaReintento();
    }

    @Bean
    public VerificarSesionService verificarSesionService(UsuarioRepositoryPort usuarios, PoliticaSesion politica) {
        return new VerificarSesionService(usuarios, politica);
    }

    @Bean
    public ConsultarCuentasService consultarCuentasService(CuentasAdminRepositoryPort cuentas) {
        return new ConsultarCuentasService(cuentas);
    }

    @Bean
    public AdministrarCuentasService administrarCuentasService(CuentasAdminRepositoryPort cuentas, OutboxPort outbox,
                                                               RelojPort reloj) {
        return new AdministrarCuentasService(cuentas, outbox, reloj);
    }

    /** El que reciben los adaptadores de entrada: el cambio de estado y su evento en una sola transacción (DEC-26). */
    @Bean
    @Primary
    public AdministrarCuentasUseCase administrarCuentasUseCase(AdministrarCuentasService servicio,
                                                               PlatformTransactionManager transacciones) {
        return new AdministrarCuentasTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    @Bean
    public PoliticaAdministradores politicaAdministradores() {
        return new PoliticaAdministradores();
    }

    @Bean
    public ConsultarPersonalService consultarPersonalService(CambioRolRepositoryPort cuentas) {
        return new ConsultarPersonalService(cuentas);
    }

    @Bean
    public CambiarRolService cambiarRolService(CambioRolRepositoryPort cuentas, OutboxPort outbox, RelojPort reloj,
                                               PoliticaAdministradores politica) {
        return new CambiarRolService(cuentas, outbox, reloj, politica);
    }

    /** El que reciben los adaptadores de entrada: todo el cambio de rol y su evento en una sola transacción (DEC-82). */
    @Bean
    @Primary
    public CambiarRolUseCase cambiarRolUseCase(CambiarRolService servicio, PlatformTransactionManager transacciones) {
        return new CambiarRolTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    @Bean
    public GestionarPerfilService gestionarPerfilService(PerfilRepositoryPort perfiles) {
        return new GestionarPerfilService(perfiles);
    }

    /** Perfil comercial del Prestador (RF43): un solo UPDATE, sin decorador transaccional (DEC-188). */
    @Bean
    public GestionarPerfilPrestadorUseCase gestionarPerfilPrestadorUseCase(PerfilPrestadorRepositoryPort perfiles) {
        return new GestionarPerfilPrestadorService(perfiles);
    }

    /** El que reciben los adaptadores de entrada: los datos y las categorías en una sola transacción (DEC-85). */
    @Bean
    @Primary
    public GestionarPerfilUseCase gestionarPerfilUseCase(GestionarPerfilService servicio,
                                                         PlatformTransactionManager transacciones) {
        return new GestionarPerfilTransaccional(servicio, new TransactionTemplate(transacciones));
    }

    /** Retención del outbox (DEC-272): {@code samaria360.outbox.purga.retencion}, 30 días por defecto. */
    @Bean
    public PurgarOutboxService purgarOutboxService(OutboxPort outbox, RelojPort reloj,
                                                   @Value("${samaria360.outbox.purga.retencion:P30D}") Duration retencion) {
        return new PurgarOutboxService(outbox, reloj, retencion);
    }

    @Bean
    public RelevarCuentasService relevarCuentasService(OutboxPort outbox, DestinoCuentasPort destinos, RelojPort reloj,
                                                       PoliticaReintento politica) {
        return new RelevarCuentasService(outbox, destinos, reloj, politica);
    }
}
