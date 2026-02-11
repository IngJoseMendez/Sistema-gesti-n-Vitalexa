-- V26: Tabla de Productos Especiales (standalone o vinculados a un producto padre)
CREATE TABLE special_products (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre            VARCHAR(255)   NOT NULL,
    descripcion       TEXT,
    precio            NUMERIC(19,2)  NOT NULL,
    own_stock         INTEGER,
    image_url         VARCHAR(500),
    active            BOOLEAN        NOT NULL DEFAULT true,
    reorder_point     INTEGER,
    product_tag_id    UUID           REFERENCES product_tags(id),
    parent_product_id UUID           REFERENCES products(id),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE INDEX idx_special_products_parent ON special_products(parent_product_id);
CREATE INDEX idx_special_products_active ON special_products(active);
