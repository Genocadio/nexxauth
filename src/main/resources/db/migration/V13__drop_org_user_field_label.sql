-- User fields are now defined by their attribute name (field_key) plus a
-- value type; the human-facing label has been removed from the product.

ALTER TABLE organisation_user_fields DROP COLUMN label;