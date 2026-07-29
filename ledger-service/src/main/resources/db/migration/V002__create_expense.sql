CREATE TABLE expense (
    id                 BIGSERIAL    PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category_id        BIGINT       NOT NULL REFERENCES category (id),
    description        VARCHAR(500) NOT NULL,
    merchant           VARCHAR(255),
    amount_minor_units BIGINT       NOT NULL CHECK (amount_minor_units >= 0),
    currency_code      VARCHAR(3)   NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_expense_user_created_at ON expense (user_id, created_at DESC);
