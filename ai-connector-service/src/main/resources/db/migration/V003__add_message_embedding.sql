ALTER TABLE incoming_message
    ADD COLUMN embedding           vector(1536),
    ADD COLUMN embedding_attempts  INT NOT NULL DEFAULT 0,
    ADD COLUMN backfill_claimed_at TIMESTAMPTZ;
