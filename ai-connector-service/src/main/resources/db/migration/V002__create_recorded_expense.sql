CREATE TABLE recorded_expense (
    id             BIGSERIAL   PRIMARY KEY,
    message_id     BIGINT      NOT NULL REFERENCES incoming_message (id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL,
    expense_id     BIGINT      NOT NULL UNIQUE,
    description    TEXT        NOT NULL,
    merchant       TEXT,
    amount         TEXT        NOT NULL,
    currency_code  VARCHAR(3)  NOT NULL,
    category_id    BIGINT      NOT NULL,
    category_name  TEXT        NOT NULL,
    grouping_id    BIGINT,
    grouping_name  TEXT,
    status         TEXT        NOT NULL CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISCARDED')),
    applied_ms     BIGINT      NOT NULL,
    applied_seq    BIGINT      NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_recorded_expense_message ON recorded_expense (message_id);

CREATE TABLE stream_entry_failure (
    entry_id        TEXT        PRIMARY KEY,
    attempts        INT         NOT NULL,
    first_failed_at TIMESTAMPTZ NOT NULL,
    last_error      TEXT        NOT NULL
);
