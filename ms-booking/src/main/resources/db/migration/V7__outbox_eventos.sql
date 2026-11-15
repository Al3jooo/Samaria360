-- Plan del 100 %, iteración G (ADR-011, DEC-291): outbox transaccional de reservas_db. El evento se inserta en la misma
-- transacción que la reserva (RF20), su cancelación (RF18) o su paso a Completada (RF52); el relevo lo publica en
-- RabbitMQ con publisher confirms. Un solo destino: sin tabla de entregas.
CREATE TABLE outbox_evento (
    id_evento       bigint GENERATED ALWAYS AS IDENTITY,
    id_publico      uuid         NOT NULL DEFAULT gen_random_uuid(),
    tipo            varchar(50)  NOT NULL,
    clave           uuid         NOT NULL,
    carga           jsonb        NOT NULL,
    creado_en       timestamptz  NOT NULL,
    publicado_en    timestamptz  NULL,
    intentos        integer      NOT NULL DEFAULT 0,
    proximo_intento timestamptz  NOT NULL,
    ultimo_error    varchar(500) NULL,
    CONSTRAINT pk_outbox_evento PRIMARY KEY (id_evento),
    CONSTRAINT uq_outbox_evento_id_publico UNIQUE (id_publico),
    CONSTRAINT ck_outbox_evento_tipo CHECK (tipo IN ('ReservaConfirmada', 'ReservaCancelada', 'ReservaCompletada')),
    CONSTRAINT ck_outbox_evento_intentos CHECK (intentos >= 0)
);

-- El relevo solo busca los pendientes, en orden de id.
CREATE INDEX ix_outbox_evento_pendiente ON outbox_evento (proximo_intento, id_evento) WHERE publicado_en IS NULL;
