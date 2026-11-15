package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.application.port.in.RestablecerContrasenaUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.SolicitarRecuperacionUseCase;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto.RespuestaRegistro;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto.SolicitudRecuperacion;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto.SolicitudRestablecimiento;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF34 (contrato en {@code docs/sad/api/ms-auth.md}). Pedir el enlace responde siempre 202 {@code RECIBIDO}, exista o
 * no la cuenta (ADR-006); restablecer responde 204 o 400 {@code TOKEN_INVALIDO}. Los dos son públicos y tienen límite
 * de tasa en el gateway.
 */
@RestController
@RequestMapping("/api/v1/auth/recuperacion")
public class RecuperacionController {

    private final SolicitarRecuperacionUseCase solicitar;
    private final RestablecerContrasenaUseCase restablecer;

    public RecuperacionController(SolicitarRecuperacionUseCase solicitar, RestablecerContrasenaUseCase restablecer) {
        this.solicitar = solicitar;
        this.restablecer = restablecer;
    }

    @PostMapping
    public ResponseEntity<RespuestaRegistro> solicitar(@RequestBody SolicitudRecuperacion solicitud) {
        solicitar.solicitar(solicitud.aComando());
        return ResponseEntity.accepted().body(RespuestaRegistro.RECIBIDO);
    }

    @PostMapping("/restablecimiento")
    public ResponseEntity<Void> restablecer(@RequestBody SolicitudRestablecimiento solicitud) {
        restablecer.restablecer(solicitud.aComando());
        return ResponseEntity.noContent().build();
    }
}
