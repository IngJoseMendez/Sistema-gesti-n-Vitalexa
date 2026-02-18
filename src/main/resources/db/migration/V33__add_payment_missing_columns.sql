-- ===========================================
-- V33: Add missing columns to payments table
-- These columns exist in the Payment entity but were never migrated.
-- In dev (ddl-auto=update) they were auto-created, but prod (ddl-auto=validate) fails.
-- ===========================================

-- 1. Payment method (EFECTIVO, TRANSFERENCIA, etc.)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50) DEFAULT 'EFECTIVO';

-- 2. Actual payment date (when the payment was physically made, as reported by user)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS actual_payment_date DATE;

-- 3. Cancellation fields
ALTER TABLE payments ADD COLUMN IF NOT EXISTS is_cancelled BOOLEAN DEFAULT false;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES users(id);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

-- 4. Indexes for new columns
CREATE INDEX IF NOT EXISTS idx_payments_payment_method ON payments(payment_method);
CREATE INDEX IF NOT EXISTS idx_payments_is_cancelled ON payments(is_cancelled);
