-- V21: Schema changes for new Operational Features
-- Description: Nullable promotions, Order Freight, Annulling, and System Product.

-- 1. Assortment Promotions: Allow creates without a specific main product (Generic)
-- This is idempotent (if already nullable, does nothing)
ALTER TABLE promotions ALTER COLUMN main_product_id DROP NOT NULL;

-- 2. Freight Toggle: Add flag to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS include_freight BOOLEAN DEFAULT false;

-- 3. Order Annulling: Add cancellation reason and update status constraint
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

-- Update OrdenStatus constraint to include 'ANULADA'
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orden_status;
ALTER TABLE orders
ADD CONSTRAINT chk_orden_status CHECK (
    estado IN (
        'PENDIENTE', 
        'PENDING_PROMOTION_COMPLETION', 
        'CONFIRMADO', 
        'COMPLETADO', 
        'CANCELADO',
        'ANULADA'
    )
);

-- 4. System Product: Add hidden flag to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN DEFAULT false;

-- 5. Seed System Product for "Surtido Promocional"
-- We use a fixed UUID to ensure we can reference it easily if needed, or query by SKU
INSERT INTO products (
    id, 
    nombre, 
    descripcion, 
    precio, 
    stock, 
    image_url, 
    active, 
    is_hidden, 
    created_at, 
    updated_at
) VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', -- Fixed ID for reliability
    'SURTIDO PROMOCIONAL', 
    'Producto placeholder para items de regalo surtidos pendientes de selección.', 
    0, 
    999999, -- Infinite stock effectively
    NULL, 
    true, 
    true, -- Hidden
    CURRENT_TIMESTAMP, 
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- Note: We don't set a specific ID here to avoid conflicts, but the backend will look it up by name or we can add a SKU column if strictly needed. 
-- For now, name 'SURTIDO PROMOCIONAL' + hidden=true is unique enough.
