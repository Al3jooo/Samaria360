-- Plan del 100 % (ADR-011, DEC-284): el outbox de ADR-008 suma la publicación en RabbitMQ.
-- 1. id_publico: el idEvento (UUID) del sobre, con el que los consumidores deduplican. Los eventos anteriores reciben
--    uno al azar (nunca se publicaron en RabbitMQ).
-- 2. Tipos nuevos, solo para RabbitMQ: PrestadorValidado (RF28) y RolCambiado (RF45), para la auditoría de RF13.
-- 3. Destino nuevo: rabbitmq. El relevo HTTP sigue igual para ms-catalog y ms-booking.
ALTER TABLE outbox_evento
    ADD COLUMN id_publico uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uq_outbox_evento_id_publico UNIQUE (id_publico);

ALTER TABLE outbox_evento DROP CONSTRAINT ck_outbox_evento_tipo;
ALTER TABLE outbox_evento
    ADD CONSTRAINT ck_outbox_evento_tipo CHECK (tipo IN ('CuentaActualizada', 'PrestadorValidado', 'RolCambiado'));

ALTER TABLE outbox_entrega DROP CONSTRAINT ck_outbox_entrega_destino;
ALTER TABLE outbox_entrega
    ADD CONSTRAINT ck_outbox_entrega_destino CHECK (destino IN ('ms-catalog', 'ms-booking', 'rabbitmq'));
