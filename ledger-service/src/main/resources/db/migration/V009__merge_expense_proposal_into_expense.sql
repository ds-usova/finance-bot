ALTER TABLE expense
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'RECORDED'
        CHECK (status IN ('PENDING', 'RECORDED')),
    ADD CONSTRAINT ck_expense_pending_has_message
        CHECK (status <> 'PENDING' OR incoming_message_id IS NOT NULL);

ALTER TABLE expense ALTER COLUMN status DROP DEFAULT;

INSERT INTO expense (user_id, category_id, description, merchant, amount_minor_units, currency_code,
                     incoming_message_id, created_at, updated_at, status)
SELECT user_id, category_id, description, merchant, amount_minor_units, currency_code,
       incoming_message_id, created_at, updated_at, 'PENDING'
FROM expense_proposal;

DROP TABLE expense_proposal;
