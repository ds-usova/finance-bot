CREATE TABLE proposal_report (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    incoming_message_id TEXT        NOT NULL,
    conversation_id     TEXT        NOT NULL,
    sent_message_id     TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_proposal_report_incoming_message ON proposal_report (user_id, incoming_message_id);
