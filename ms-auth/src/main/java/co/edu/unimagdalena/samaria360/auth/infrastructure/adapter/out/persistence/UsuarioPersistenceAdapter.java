package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence.entity.UsuarioEntity;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence.mapper.UsuarioPersistenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Component
public class UsuarioPersistenceAdapter implements UsuarioRepositoryPort {

    private final UsuarioJpaRepository repositorio;
    private final UsuarioPersistenceMapper mapper;

    public UsuarioPersistenceAdapter(UsuarioJpaRepository repositorio, UsuarioPersistenceMapper mapper) {
        this.repositorio = repositorio;
        this.mapper = mapper;
    }

    /**
     * Consulta bloqueante: el lock dura hasta el fin de la transacción del caso de uso, así que exige una
     * transacción abierta (la abre {@code IniciarSesionTransaccional}).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return repositorio.buscarPorCorreoParaActualizar(correo).map(mapper::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return repositorio.findById(id).map(mapper::aDominio);
    }

    @Override
    public Optional<String> buscarNombreVisible(UUID id) {
        return repositorio.buscarNombreVisible(id);
    }

    @Override
    public Optional<EstadoValidacion> buscarEstadoValidacion(UUID id) {
        return repositorio.buscarEstadoValidacion(id).map(EstadoValidacion::desdeTexto);
    }

    @Override
    public Optional<String> buscarMotivoRechazo(UUID id) {
        return repositorio.buscarMotivoRechazo(id).filter(motivo -> !motivo.isBlank());
    }

    @Override
    public OptionalLong buscarVersionCuenta(UUID id) {
        return repositorio.buscarVersionCuenta(id).map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    /** Dentro de la transacción, findById devuelve la entidad ya cargada sin otro SELECT. */
    @Override
    public void guardar(Usuario usuario) {
        UsuarioEntity entidad = repositorio.findById(usuario.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario inexistente: " + usuario.getId()));
        mapper.actualizar(entidad, usuario);
        repositorio.save(entidad);
    }
}
