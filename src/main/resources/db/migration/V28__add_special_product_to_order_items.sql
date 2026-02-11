-- V28: Agregar referencia a special_product en order_items
ALTER TABLE order_items ADD COLUMN special_product_id UUID NULL;

ALTER TABLE order_items ADD CONSTRAINT fk_order_item_special_product
    FOREIGN KEY (special_product_id) REFERENCES special_products(id);
