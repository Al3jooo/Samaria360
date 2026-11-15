package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoInicioSesion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ConsultarUsuarioActualUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.IniciarSesionUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RenovarSesionUseCase;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto.RespuestaInicioSesion;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto.RespuestaUsuarioActual;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto.SolicitudInicioSesion;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Caso de uso Iniciar sesión (RF15). Solo depende de los puertos de entrada. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IniciarSesionUseCase iniciarSesion;
    private final ConsultarUsuarioActualUseCase consultarUsuarioActual;
    private final RenovarSesionUseCase renovarSesion;

    /** Recibe el IniciarSesionUseCase @Primary: el decorador transaccional, no el servicio puro. */
    public AuthController(IniciarSesionUseCase iniciarSesion, ConsultarUsuarioActualUseCase consultarUsuarioActual,
                          RenovarSesionUseCase renovarSesion) {
        this.iniciarSesion = iniciarSesion;
        this.consultarUsuarioActual = consultarUsuarioActual;
        this.renovarSesion = renovarSesion;
    }

    @PostMapping("/login")
    public RespuestaInicioSesion login(@Valid @RequestBody SolicitudInicioSesion solicitud) {
        return RespuestaInicioSesion.de(
                iniciarSesion.iniciarSesion(new ComandoInicioSesion(solicitud.correo(), solicitud.contrasena())));
    }

    @GetMapping("/me")
    public RespuestaUsuarioActual me(@AuthenticationPrincipal Jwt jwt) {
        return RespuestaUsuarioActual.de(consultarUsuarioActual.consultar(UUID.fromString(jwt.getSubject())));
    }

    /**
     * Renovar la sesión (DEC-317): con el token vigente, uno nuevo con la misma forma que el del login. El inicio de la
     * sesión sale del claim {@code ini}; un token emitido antes de DEC-317 no lo tiene y se usa su {@code iat}.
     */
    @PostMapping("/sesion/renovacion")
    public RespuestaInicioSesion renovar(@AuthenticationPrincipal Jwt jwt) {
        return RespuestaInicioSesion.de(renovarSesion.renovar(UUID.fromString(jwt.getSubject()), inicioSesion(jwt)));
    }

    private static Instant inicioSesion(Jwt jwt) {
        Object ini = jwt.getClaims().get("ini");
        if (ini instanceof Number segundos) {
            return Instant.ofEpochSecond(segundos.longValue());
        }
        return jwt.getIssuedAt();
    }
}
