CREATE TABLE written_message (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    text       VARCHAR(1000) NOT NULL,
    status     VARCHAR(10)   NOT NULL,
    outcome    VARCHAR(20),
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_written_message_text CHECK (text ~ '\S'),
    CONSTRAINT ck_written_message_status CHECK (status IN ('WAITING', 'READING', 'DONE')),
    CONSTRAINT ck_written_message_outcome
        CHECK (outcome IN ('RECORDED', 'PARTIAL', 'NOTHING_IDENTIFIED', 'ANSWERED', 'FAILED')),
    CONSTRAINT ck_written_message_outcome_when_done CHECK ((status = 'DONE') = (outcome IS NOT NULL))
);

CREATE UNIQUE INDEX ux_written_message_one_unsettled_per_user
    ON written_message (user_id) WHERE status IN ('WAITING', 'READING');

CREATE INDEX idx_written_message_waiting ON written_message (id) WHERE status = 'WAITING';
