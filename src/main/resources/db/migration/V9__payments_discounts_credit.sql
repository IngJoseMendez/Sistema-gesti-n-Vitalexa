-- ===========================================
-- V9: Payments, Discounts, and Credit Control
-- ===========================================

-- 1. New fields in clients table
ALTER TABLE clients ADD COLUMN IF NOT EXISTS credit_limit DECIMAL(12,2);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS initial_balance DECIMAL(12,2) DEFAULT 0;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS initial_balance_set BOOLEAN DEFAULT false;

-- 2. New fields in orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_percentage DECIMAL(5,2) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discounted_total DECIMAL(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) DEFAULT 'PENDING';

-- 3. Payments table (for installments/abonos)
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    amount DECIMAL(12,2) NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    within_deadline BOOLEAN DEFAULT false,
    discount_applied DECIMAL(5,2) DEFAULT 0,
    registered_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- 4. Order discounts table (audit trail)
CREATE TABLE IF NOT EXISTS order_discounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    percentage DECIMAL(5,2) NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    applied_by UUID REFERENCES users(id),
    revoked_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    reason TEXT
);

-- 5. Indexes for performance
CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_registered_by ON payments(registered_by);
CREATE INDEX IF NOT EXISTS idx_discounts_order ON order_discounts(order_id);
CREATE INDEX IF NOT EXISTS idx_discounts_status ON order_discounts(status);
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);

-- 6. Initialize discounted_total = total for existing orders
UPDATE orders SET discounted_total = total WHERE discounted_total IS NULL;
