-- V12: Extender order_items para soportar productos sin stock y promociones
-- Fecha: 2026-01-21
-- Descripción: Agregar campos para tracking de stock, ETA y relación con promociones

-- Agregar columnas para productos sin stock
ALTER TABLE order_items 
ADD COLUMN out_of_stock BOOLEAN DEFAULT false NOT NULL,
ADD COLUMN estimated_arrival_date DATE,
ADD COLUMN estimated_arrival_note VARCHAR(500);

-- Agregar columnas para promociones
ALTER TABLE order_items
ADD COLUMN promotion_id UUID,
ADD COLUMN is_promotion_item BOOLEAN DEFAULT false NOT NULL,
ADD COLUMN is_free_item BOOLEAN DEFAULT false NOT NULL;

-- Agregar foreign key para promociones
ALTER TABLE order_items
ADD CONSTRAINT fk_order_item_promotion FOREIGN KEY (promotion_id) 
    REFERENCES promotions(id) ON DELETE SET NULL;

-- Índices para mejorar performance en queries
CREATE INDEX idx_order_items_out_of_stock ON order_items(out_of_stock) WHERE out_of_stock = true;
CREATE INDEX idx_order_items_promotion ON order_items(promotion_id) WHERE promotion_id IS NOT NULL;
CREATE INDEX idx_order_items_free_items ON order_items(is_free_item) WHERE is_free_item = true;

-- Comentarios para documentación
COMMENT ON COLUMN order_items.out_of_stock IS 'Indica si este item fue agregado sin stock disponible';
COMMENT ON COLUMN order_items.estimated_arrival_date IS 'Fecha estimada de llegada del producto (si está sin stock)';
COMMENT ON COLUMN order_items.estimated_arrival_note IS 'Nota adicional sobre la fecha de llegada estimada';
COMMENT ON COLUMN order_items.promotion_id IS 'Referencia a la promoción asociada (si aplica)';
COMMENT ON COLUMN order_items.is_promotion_item IS 'Indica si este item es parte de una promoción';
COMMENT ON COLUMN order_items.is_free_item IS 'Indica si este item es bonificado/gratis (precio = 0)';
