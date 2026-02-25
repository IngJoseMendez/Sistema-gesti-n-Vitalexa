-- V37: Add columns for configurable general commission threshold.
-- The Owner can now set a custom threshold for the general commission
-- instead of always using the sum of all vendor goals.

ALTER TABLE payrolls
    ADD COLUMN IF NOT EXISTS effective_threshold  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS threshold_is_custom   BOOLEAN       NOT NULL DEFAULT FALSE;
