-- Plan del 100 % (ADR-011, DEC-284): ms-analytics arma sus vistas solo con eventos (decisión 6 del equipo) y necesita el
-- estado de las cuentas que ya existían: su proyección valida el claim «ver» (DEC-110) y cuenta los turistas por país
-- (RF14). Se encola un CuentaActualizada por cuenta, solo para RabbitMQ y sin subir la versión: los destinos HTTP ya
-- tienen su proyección y ningún token se invalida.
--
-- El estado es el derivado (DEC-43), como en V15 y V17: el del prestador, por su validación y su cuenta; el del
-- turista, Activo o Desactivado; el del personal, su estado_cuenta. El país solo va con el rol Turista.
WITH cuentas AS (
    SELECT u.id_usuario, r.nombre AS rol, u.version_cuenta,
           CASE r.nombre
               WHEN 'Prestador' THEN CASE WHEN p.estado_validacion = 'Pendiente' THEN 'Pendiente'
                                          WHEN p.estado_validacion = 'Rechazado' THEN 'Rechazado'
                                          WHEN u.estado_cuenta = 'Activo' THEN 'Activo'
                                          ELSE 'Inactivo' END
               WHEN 'Turista' THEN CASE WHEN u.estado_cuenta = 'Activo' THEN 'Activo' ELSE 'Desactivado' END
               ELSE u.estado_cuenta::text
           END AS estado,
           CASE WHEN r.nombre = 'Turista' THEN t.pais_origen END AS pais
    FROM usuario u
    JOIN rol r ON r.id_rol = u.id_rol
    LEFT JOIN prestador p ON p.id_usuario = u.id_usuario
    LEFT JOIN turista t ON t.id_usuario = u.id_usuario
), eventos AS (
    INSERT INTO outbox_evento (tipo, id_usuario, carga, creado_en)
    SELECT 'CuentaActualizada', c.id_usuario,
           jsonb_strip_nulls(jsonb_build_object('idUsuario', c.id_usuario::text, 'rol', c.rol, 'estadoCuenta', c.estado,
                                                'version', c.version_cuenta, 'pais', c.pais)),
           now()
    FROM cuentas c
    RETURNING id_evento
)
INSERT INTO outbox_entrega (id_evento, destino, proximo_intento)
SELECT e.id_evento, 'rabbitmq', now()
FROM eventos e;
