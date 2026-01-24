-- V17: Fix constraint name conflict in production
-- Description: Drop potential legacy constraint names and re-apply correct one

-- 1. Drop the legacy constraint that is causing issues (from exception log)
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_estado_check;

-- 2. Drop the current intended constraint just to be safe/clean
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orden_status;

-- 3. Re-create the correct constraint
ALTER TABLE orders
ADD CONSTRAINT chk_orden_status CHECK (
    estado IN (
        'PENDIENTE', 
        'PENDING_PROMOTION_COMPLETION', 
        'CONFIRMADO', 
        'COMPLETADO', 
        'CANCELADO'
    )
);

COMMENT ON CONSTRAINT chk_orden_status ON orders IS 'Valida que el estado de la orden sea uno de los valores permitidos, incluyendo PENDING_PROMOTION_COMPLETION.';
