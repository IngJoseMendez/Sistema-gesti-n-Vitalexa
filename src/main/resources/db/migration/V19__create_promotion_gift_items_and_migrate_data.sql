-- Create table for multiple gift items per promotion
CREATE TABLE IF NOT EXISTS promotion_gift_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    
    CONSTRAINT fk_gift_item_promotion FOREIGN KEY (promotion_id) 
        REFERENCES promotions(id) ON DELETE CASCADE,
    CONSTRAINT fk_gift_item_product FOREIGN KEY (product_id) 
        REFERENCES products(id) ON DELETE CASCADE
);

-- Index for performance
CREATE INDEX IF NOT EXISTS idx_gift_items_promotion ON promotion_gift_items(promotion_id);

-- Migrate existing single-product gifts to the new table
-- Only insert if the table is empty to avoid duplication on re-runs
INSERT INTO promotion_gift_items (promotion_id, product_id, quantity)
SELECT id, free_product_id, free_quantity
FROM promotions
WHERE free_product_id IS NOT NULL AND free_quantity IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM promotion_gift_items);

-- Remove old columns
-- Using IF EXISTS to avoid errors if already dropped
ALTER TABLE promotions ALTER COLUMN free_product_id DROP NOT NULL;
ALTER TABLE promotions ALTER COLUMN free_quantity DROP NOT NULL;
