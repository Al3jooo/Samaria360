package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.application.port.out.ContactosRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Contacto;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Contactos para los clientes internos (DEC-280), filtrados por el rol actual de la cuenta: el turista con su nombre y
 * su correo de acceso; el prestador con el representante legal, el correo corporativo de contacto (decisión D2, DEC-188)
 * y el teléfono.
 */
@Component
public class ContactosJdbcAdapter implements ContactosRepositoryPort {

    private final JdbcTemplate jdbc;

    public ContactosJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Contacto> buscar(Collection<UUID> ids, Rol rol) {
        UUID[] arreglo = ids.toArray(UUID[]::new);
        if (rol == Rol.Prestador) {
            return jdbc.query("""
                    SELECT u.id_usuario, p.nombre_representante_legal, p.correo_corporativo, p.telefono
                    FROM usuario u
                    JOIN rol r ON r.id_rol = u.id_rol
                    JOIN prestador p ON p.id_usuario = u.id_usuario
                    WHERE r.nombre = 'Prestador' AND u.id_usuario = ANY (?)
                    """, (rs, n) -> new Contacto(rs.getObject(1, UUID.class), Rol.Prestador, rs.getString(2),
                    rs.getString(3), rs.getString(4)), (Object) arreglo);
        }
        if (rol == Rol.Turista) {
            return jdbc.query("""
                    SELECT u.id_usuario,
                           concat_ws(' ', t.primer_nombre, t.segundo_nombre, t.primer_apellido, t.segundo_apellido),
                           u.correo
                    FROM usuario u
                    JOIN rol r ON r.id_rol = u.id_rol
                    JOIN turista t ON t.id_usuario = u.id_usuario
                    WHERE r.nombre = 'Turista' AND u.id_usuario = ANY (?)
                    """, (rs, n) -> new Contacto(rs.getObject(1, UUID.class), Rol.Turista, rs.getString(2),
                    rs.getString(3), null), (Object) arreglo);
        }
        return List.of();
    }
}
