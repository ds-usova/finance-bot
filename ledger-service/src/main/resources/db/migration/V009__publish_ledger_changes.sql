ALTER TABLE expense          REPLICA IDENTITY FULL;
ALTER TABLE expense_proposal REPLICA IDENTITY FULL;
ALTER TABLE category         REPLICA IDENTITY FULL;

CREATE TABLE cdc_heartbeat (
    id        BOOLEAN     PRIMARY KEY DEFAULT TRUE CHECK (id),
    beat_at   TIMESTAMPTZ NOT NULL
);

INSERT INTO cdc_heartbeat (beat_at) VALUES (now());

CREATE PUBLICATION finance_ledger_cdc
    FOR TABLE expense, expense_proposal, category, cdc_heartbeat
    WITH (publish = 'insert, update, delete');
