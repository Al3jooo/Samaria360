package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoInicioSesion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.IniciarSesionUseCase;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CredencialesInvalidasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaBloqueadaException;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF15 con intentos simultáneos (H16 de la revisión del 2026-11-12). {@code buscarPorCorreo} bloquea la fila del usuario
 * ({@code SELECT … FOR UPDATE}) hasta el final de la transacción del caso de uso: los intentos se atienden uno detrás de
 * otro y ninguno pierde la suma de otro. Sin ese bloqueo, varios intentos leerían el mismo conteo y la cuenta aceptaría
 * más de tres contraseñas incorrectas antes de bloquearse.
 */
@PruebaIntegracion
class IniciarSesionConcurrenciaIntegracionTest {

    private static final UUID ID_TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CORREO_TURISTA = "turista@samaria360.test";
    private static final int INTENTOS_SIMULTANEOS = 6;

    @Autowired
    private IniciarSesionUseCase iniciarSesion;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void restablecerEstadoDeLogin() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, estado_cuenta = 'Activo'"
                + " WHERE id_usuario = ?", ID_TURISTA);
    }

    @Test
    void intentosSimultaneosConLaContrasenaIncorrectaBloqueanAlTerceroSinPerderNinguno() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(INTENTOS_SIMULTANEOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Class<?>>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < INTENTOS_SIMULTANEOS; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    try {
                        iniciarSesion.iniciarSesion(new ComandoInicioSesion(CORREO_TURISTA, "incorrecta"));
                        return Void.class;
                    } catch (RuntimeException e) {
                        return e.getClass();
                    }
                }));
            }
            salida.countDown();

            int credencialesInvalidas = 0;
            int bloqueadas = 0;
            for (Future<Class<?>> resultado : resultados) {
                Class<?> tipo = resultado.get(30, TimeUnit.SECONDS);
                if (tipo == CredencialesInvalidasException.class) {
                    credencialesInvalidas++;
                } else if (tipo == CuentaBloqueadaException.class) {
                    bloqueadas++;
                }
            }

            // Los dos primeros suman un intento cada uno (401); el tercero bloquea (423) y los demás ya encuentran la
            // cuenta bloqueada (423, antes de verificar la contraseña).
            assertThat(credencialesInvalidas).isEqualTo(2);
            assertThat(bloqueadas).isEqualTo(INTENTOS_SIMULTANEOS - 2);
            assertThat(jdbc.queryForObject("SELECT bloqueado_hasta IS NOT NULL FROM usuario WHERE id_usuario = ?",
                    Boolean.class, ID_TURISTA)).isTrue();
        } finally {
            hilos.shutdownNow();
        }
    }
}
