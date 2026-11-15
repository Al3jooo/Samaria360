package co.edu.unimagdalena.samaria360.auth.application.fake;

import co.edu.unimagdalena.samaria360.auth.application.port.out.CuentaRestablecida;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RecuperacionRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de {@code token_recuperacion} y del restablecimiento (RF34); las reglas SQL se prueban contra PostgreSQL. */
public class RecuperacionRepositoryEnMemoria implements RecuperacionRepositoryPort {

    public static final class Token {
        public final UUID idUsuario;
        public final String hash;
        public final Instant expiraEn;
        public boolean usado;

        Token(UUID idUsuario, String hash, Instant expiraEn) {
            this.idUsuario = idUsuario;
            this.hash = hash;
            this.expiraEn = expiraEn;
        }
    }

    private final Map<String, UUID> correos = new HashMap<>();
    private final Map<UUID, CuentaRestablecida> cuentas = new HashMap<>();
    private final Map<UUID, String> contrasenas = new HashMap<>();
    private final List<Token> tokens = new ArrayList<>();

    public RecuperacionRepositoryEnMemoria conCuenta(String correo, UUID id, Rol rol, long version) {
        correos.put(correo, id);
        cuentas.put(id, new CuentaRestablecida(id, rol, EstadoAdministrativo.Activo, version,
                rol == Rol.Prestador ? "RNT-1" : null));
        return this;
    }

    public List<Token> tokens() {
        return tokens;
    }

    public Optional<String> contrasenaDe(UUID id) {
        return Optional.ofNullable(contrasenas.get(id));
    }

    @Override
    public Optional<UUID> buscarIdPorCorreo(String correo) {
        return Optional.ofNullable(correos.get(correo));
    }

    @Override
    public void guardarToken(UUID idUsuario, String tokenHash, Instant expiraEn) {
        tokens.stream().filter(t -> t.idUsuario.equals(idUsuario)).forEach(t -> t.usado = true);
        tokens.add(new Token(idUsuario, tokenHash, expiraEn));
    }

    @Override
    public Optional<UUID> consumirToken(String tokenHash, Instant ahora) {
        Optional<Token> token = tokens.stream()
                .filter(t -> t.hash.equals(tokenHash) && !t.usado && t.expiraEn.isAfter(ahora)).findFirst();
        token.ifPresent(t -> tokens.stream().filter(o -> o.idUsuario.equals(t.idUsuario)).forEach(o -> o.usado = true));
        return token.map(t -> t.idUsuario);
    }

    @Override
    public CuentaRestablecida restablecer(UUID idUsuario, String contrasenaHash) {
        contrasenas.put(idUsuario, contrasenaHash);
        CuentaRestablecida anterior = cuentas.get(idUsuario);
        CuentaRestablecida nueva = new CuentaRestablecida(idUsuario, anterior.rol(), anterior.estado(),
                anterior.versionNueva() + 1, anterior.rnt());
        cuentas.put(idUsuario, nueva);
        return nueva;
    }
}
