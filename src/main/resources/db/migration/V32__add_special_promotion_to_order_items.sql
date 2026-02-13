-- V32: Add special_promotion_id to order_items
ALTER TABLE order_items
ADD COLUMN special_promotion_id UUID REFERENCES special_promotions(id);

CREATE INDEX idx_order_items_special_promotion ON order_items(special_promotion_id);
