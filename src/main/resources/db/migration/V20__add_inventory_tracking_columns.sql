-- Add missing inventory tracking columns to order_items
ALTER TABLE order_items 
ADD COLUMN cantidad_descontada INTEGER DEFAULT 0,
ADD COLUMN cantidad_pendiente INTEGER DEFAULT 0;

-- Comments
COMMENT ON COLUMN order_items.cantidad_descontada IS 'Cantidad que ya ha sido descontada del inventario';
COMMENT ON COLUMN order_items.cantidad_pendiente IS 'Cantidad pendiente por descontar (para control de items sin stock)';
