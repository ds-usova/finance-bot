ALTER TABLE expense
    ADD COLUMN message_reference UUID;

CREATE INDEX idx_expense_message_reference ON expense (user_id, message_reference);
