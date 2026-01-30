-- Add freight_quantity column to orders
ALTER TABLE orders 
ADD COLUMN freight_quantity INTEGER DEFAULT 1;
