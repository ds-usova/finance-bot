ALTER TABLE expense_proposal RENAME COLUMN message_reference TO incoming_message_id;
ALTER TABLE expense          RENAME COLUMN message_reference TO incoming_message_id;
ALTER TABLE spending_query   RENAME COLUMN message_reference TO incoming_message_id;

ALTER TABLE expense_proposal ALTER COLUMN incoming_message_id TYPE TEXT USING incoming_message_id::text;
ALTER TABLE expense          ALTER COLUMN incoming_message_id TYPE TEXT USING incoming_message_id::text;
ALTER TABLE spending_query   ALTER COLUMN incoming_message_id TYPE TEXT USING incoming_message_id::text;

ALTER INDEX idx_expense_proposal_message_reference RENAME TO idx_expense_proposal_incoming_message;
ALTER INDEX idx_expense_message_reference          RENAME TO idx_expense_incoming_message;
ALTER INDEX idx_spending_query_message_reference   RENAME TO idx_spending_query_incoming_message;
