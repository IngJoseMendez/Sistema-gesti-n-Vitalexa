-- V29: Agregar campos para identificar instancias únicas de promociones y preservar precios fijos

-- Agregar campos a order_items para gestionar instancias únicas de promociones
ALTER TABLE order_items ADD COLUMN promotion_instance_id UUID NULL;
ALTER TABLE order_items ADD COLUMN promotion_pack_price NUMERIC(12, 2) NULL;
ALTER TABLE order_items ADD COLUMN promotion_group_index INTEGER NULL;

-- Comentarios explicativos
COMMENT ON COLUMN order_items.promotion_instance_id IS 'UUID único para cada instancia de promoción en la orden. Permite diferenciar múltiples instancias de la misma promoción.';
COMMENT ON COLUMN order_items.promotion_pack_price IS 'Precio fijo de la promoción guardado en el item. Evita que al editar se recalcule como suma de productos.';
COMMENT ON COLUMN order_items.promotion_group_index IS 'Índice ordinal para promociones duplicadas. Ej: Promo A #1, Promo A #2, etc.';

-- Índice para búsquedas rápidas por promotion_instance_id
CREATE INDEX IF NOT EXISTS idx_order_items_promotion_instance ON order_items(promotion_instance_id);

