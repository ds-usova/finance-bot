CREATE TABLE recorded_expense (
    id                 BIGSERIAL   PRIMARY KEY,
    message_id         BIGINT      NOT NULL REFERENCES incoming_message (id) ON DELETE CASCADE,
    user_id            BIGINT      NOT NULL,
    proposal_id        BIGINT      UNIQUE,
    expense_id         BIGINT      UNIQUE,
    description        TEXT        NOT NULL,
    merchant           TEXT,
    amount_minor_units BIGINT      NOT NULL,
    currency_code      VARCHAR(3)  NOT NULL,
    category_id        BIGINT      NOT NULL,
    category_name      TEXT,
    grouping_name      TEXT,
    status             TEXT        NOT NULL CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISCARDED', 'UNKNOWN')),
    moved_in_tx        TEXT,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_recorded_expense_has_a_key CHECK (proposal_id IS NOT NULL OR expense_id IS NOT NULL)
);

CREATE INDEX idx_recorded_expense_message       ON recorded_expense (message_id);
CREATE INDEX idx_recorded_expense_category      ON recorded_expense (category_id);
CREATE INDEX idx_recorded_expense_user_grouping ON recorded_expense (user_id, grouping_name);
CREATE INDEX idx_recorded_expense_moved_in_tx   ON recorded_expense (moved_in_tx);

CREATE TABLE stream_entry_failure (
    entry_id        TEXT        PRIMARY KEY,
    attempts        INT         NOT NULL,
    first_failed_at TIMESTAMPTZ NOT NULL,
    last_error      TEXT        NOT NULL
);
