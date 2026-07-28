CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_app_user_external_id UNIQUE (external_id)
);

CREATE TABLE category (
    id        BIGSERIAL   PRIMARY KEY,
    user_id   BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    parent_id BIGINT      REFERENCES category (id) ON DELETE CASCADE,
    name      VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX uq_category_user_parent_name
    ON category (user_id, parent_id, name) NULLS NOT DISTINCT;
