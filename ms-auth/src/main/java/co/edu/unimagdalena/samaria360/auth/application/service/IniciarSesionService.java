package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoInicioSesion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.IniciarSesionUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.SesionIniciada;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PasswordHasherPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CredencialesInvalidasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaBloqueadaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaRechazadaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.InicioSesionException;
import co.edu.unimagdalena.samaria360.auth.domain.model.Correo;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaAcceso;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaBloqueo;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso Iniciar sesión (RF15). La transacción la abre la infraestructura. Orden:
 * <ol>
 *   <li>cuenta bloqueada por intentos (423): lo único antes de la contraseña;</li>
 *   <li>contraseña: si no coincide, 401 y suma un intento, sea cual sea el estado de la cuenta;</li>
 *   <li>con la contraseña correcta, el estado: cuenta no Activa (RF22) o prestador Pendiente o Rechazado (decisión D3),
 *       403 sin emitir token; el conteo de intentos vuelve a 0 y no se registra un acceso;</li>
 *   <li>token y acceso registrado.</li>
 * </ol>
 */
public class IniciarSesionService implements IniciarSesionUseCase {

    /**
     * Hash BCrypt real de costo 10 (igual que el de las cuentas) de una contraseña aleatoria descartada.
     * Se compara cuando el correo no existe para que el tiempo de respuesta no revele qué correos están
     * registrados; un valor que no fuera BCrypt válido se rechazaría sin calcular nada.
     */
    static final String HASH_FICTICIO = "$2a$10$T6zsF.mE8.NoxkZi4SQbPuIQ9h3uxhsiB2.08ghsaFTuZ05kifUaS";

    private final UsuarioRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final TokenProviderPort tokens;
    private final RelojPort reloj;
    private final PoliticaBloqueo politica;
    private final PoliticaAcceso politicaAcceso;

    public IniciarSesionService(UsuarioRepositoryPort usuarios, PasswordHasherPort hasher,
                                TokenProviderPort tokens, RelojPort reloj, PoliticaBloqueo politica,
                                PoliticaAcceso politicaAcceso) {
        this.usuarios = Objects.requireNonNull(usuarios);
        this.hasher = Objects.requireNonNull(hasher);
        this.tokens = Objects.requireNonNull(tokens);
        this.reloj = Objects.requireNonNull(reloj);
        this.politica = Objects.requireNonNull(politica);
        this.politicaAcceso = Objects.requireNonNull(politicaAcceso);
    }

    @Override
    public SesionIniciada iniciarSesion(ComandoInicioSesion comando) {
        Instant ahora = reloj.ahora();
        String correo = Correo.normalizar(comando.correo());

        Optional<Usuario> encontrado = usuarios.buscarPorCorreo(correo);
        if (encontrado.isEmpty()) {
            hasher.coincide(comando.contrasena(), HASH_FICTICIO);
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = encontrado.get();

        // Bloqueada: se rechaza sin verificar la contraseña (riesgo residual aceptado en ADR-006).
        politica.verificarBloqueo(usuario, ahora);

        if (!hasher.coincide(comando.contrasena(), usuario.getContrasenaHash())) {
            politica.registrarIntentoFallido(usuario, ahora);
            usuarios.guardar(usuario);
            if (usuario.estaBloqueadoEn(ahora)) {
                throw new CuentaBloqueadaException(usuario.getBloqueadoHasta().orElseThrow());
            }
            throw new CredencialesInvalidasException();
        }

        try {
            politicaAcceso.verificar(usuario, usuarios.buscarEstadoValidacion(usuario.getId()));
        } catch (InicioSesionException accesoNegado) {
            // La contraseña era correcta: no es un intento fallido, pero tampoco un acceso.
            politica.reiniciarIntentos(usuario);
            usuarios.guardar(usuario);
            if (accesoNegado instanceof CuentaRechazadaException) {
                // DEC-273: con la contraseña correcta, el prestador ve la justificación de su rechazo (RF28).
                throw new CuentaRechazadaException(usuarios.buscarMotivoRechazo(usuario.getId()).orElse(null));
            }
            throw accesoNegado;
        }

        TokenEmitido token = tokens.emitir(usuario, ahora);
        politica.registrarLoginExitoso(usuario);
        usuarios.guardar(usuario);
        return new SesionIniciada(token.token(), token.expiraEn(), usuario.getId(), usuario.getRol());
    }
}
