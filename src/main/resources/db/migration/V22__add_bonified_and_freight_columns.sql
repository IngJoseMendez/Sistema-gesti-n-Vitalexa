-- Add columns for bonified products (items)
ALTER TABLE order_items 
ADD COLUMN is_bonified BOOLEAN DEFAULT FALSE,
ADD COLUMN is_freight_item BOOLEAN DEFAULT FALSE;

-- Add columns for bonified/custom freight (orders)
ALTER TABLE orders 
ADD COLUMN is_freight_bonified BOOLEAN DEFAULT FALSE,
ADD COLUMN freight_custom_text VARCHAR(255);
