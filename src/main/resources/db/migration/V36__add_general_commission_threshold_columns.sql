-- V36: Add columns for general commission threshold rule.
-- The general commission now only applies when total company sales for the month
-- >= sum of all vendor goals. These columns store that audit data.

ALTER TABLE payrolls
    ADD COLUMN IF NOT EXISTS total_company_sales       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS general_commission_goal_met BOOLEAN       NOT NULL DEFAULT FALSE;
