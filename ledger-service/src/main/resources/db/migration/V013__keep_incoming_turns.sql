CREATE TABLE incoming_turn (
    id                  BIGSERIAL     PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    incoming_message_id TEXT          NOT NULL,
    text                VARCHAR(4096) NOT NULL,
    conversation_id     TEXT          NOT NULL,
    inbound_message_id  TEXT          NOT NULL,
    kept_at             TIMESTAMPTZ   NOT NULL,
    last_attempt_at     TIMESTAMPTZ,
    handed_over_at      TIMESTAMPTZ,
    CONSTRAINT ck_incoming_turn_text CHECK (text ~ '\S')
);

CREATE UNIQUE INDEX ux_incoming_turn_incoming_message ON incoming_turn (incoming_message_id);

CREATE INDEX idx_incoming_turn_queued ON incoming_turn (id) WHERE handed_over_at IS NULL;
