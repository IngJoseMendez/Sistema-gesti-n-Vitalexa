CREATE TABLE IF NOT EXISTS inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID,
    product_name VARCHAR(255),
    type VARCHAR(50) NOT NULL,
    quantity INTEGER,
    previous_stock INTEGER,
    new_stock INTEGER,
    reason VARCHAR(255),
    username VARCHAR(255),
    timestamp TIMESTAMP,
    CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_inventory_movement_product ON inventory_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_type ON inventory_movements(type);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_timestamp ON inventory_movements(timestamp);
