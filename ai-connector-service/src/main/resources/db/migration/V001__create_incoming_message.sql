CREATE TABLE incoming_message (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    incoming_message_id TEXT         NOT NULL,
    text                TEXT         NOT NULL,
    received_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_incoming_message_user_message UNIQUE (user_id, incoming_message_id)
);

CREATE INDEX idx_incoming_message_user_received ON incoming_message (user_id, received_at DESC);
CREATE INDEX idx_incoming_message_received      ON incoming_message (received_at);
