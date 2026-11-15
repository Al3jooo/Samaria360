-- Plan del 100 %, iteración I (RF19; decisión 3 del equipo, DEC-310): el Prestador modifica fechas, horarios y cupos.
-- 1. inventario_cupo.cerrado: una fecha u horario cerrado no se reserva ni se muestra como disponible. No se borra la
--    fila: reserva_cupo_dia la referencia (también la de reservas canceladas). Solo se cierra sin cupos reservados.
-- 2. El outbox lleva también OfertaModificada (clave = id del servicio, un ObjectId en texto): la clave pasa de uuid a
--    texto (las existentes se conservan como su forma canónica) y el CHECK del tipo suma el nuevo evento.
ALTER TABLE inventario_cupo ADD COLUMN cerrado boolean NOT NULL DEFAULT false;

ALTER TABLE outbox_evento ALTER COLUMN clave TYPE varchar(64) USING clave::text;
ALTER TABLE outbox_evento DROP CONSTRAINT ck_outbox_evento_tipo;
ALTER TABLE outbox_evento ADD CONSTRAINT ck_outbox_evento_tipo
    CHECK (tipo IN ('ReservaConfirmada', 'ReservaCancelada', 'ReservaCompletada', 'OfertaModificada'));

-- RF42: asistentes de un servicio por fecha (la reserva más usada por el Prestador).
CREATE INDEX ix_reserva_prestador_servicio_entrada ON reserva (id_prestador, id_servicio_mongo, fecha_entrada)
    WHERE estado = 'Confirmada';
