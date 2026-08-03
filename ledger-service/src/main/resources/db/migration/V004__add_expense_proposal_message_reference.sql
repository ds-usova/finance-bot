ALTER TABLE expense_proposal
    ADD COLUMN message_reference UUID;

UPDATE expense_proposal SET message_reference = gen_random_uuid() WHERE message_reference IS NULL;

ALTER TABLE expense_proposal
    ALTER COLUMN message_reference SET NOT NULL;

CREATE INDEX idx_expense_proposal_message_reference ON expense_proposal (user_id, message_reference);
