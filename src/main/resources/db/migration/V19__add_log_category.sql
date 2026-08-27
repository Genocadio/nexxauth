-- Add category column for grouping log entries (AUTH, USER_MANAGEMENT, etc.).
-- Default to AUTH for existing rows so the NOT NULL constraint can be added.

ALTER TABLE log_entries ADD COLUMN category VARCHAR(20);
UPDATE log_entries SET category = 'AUTH';
ALTER TABLE log_entries ALTER COLUMN category SET NOT NULL;
ALTER TABLE log_entries ALTER COLUMN category SET DEFAULT 'AUTH';

CREATE INDEX idx_log_entries_category ON log_entries (category);
