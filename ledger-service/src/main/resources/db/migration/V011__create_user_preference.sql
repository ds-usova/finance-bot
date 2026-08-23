CREATE TABLE user_preference (
    user_id               BIGINT     PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    default_currency_code VARCHAR(3) NOT NULL
);
