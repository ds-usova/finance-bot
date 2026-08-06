CREATE TABLE spending_query (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    message_reference UUID        NOT NULL,
    period_start      DATE        NOT NULL,
    period_end        DATE        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_spending_query_period CHECK (period_end >= period_start)
);

CREATE INDEX idx_spending_query_message_reference ON spending_query (user_id, message_reference);
