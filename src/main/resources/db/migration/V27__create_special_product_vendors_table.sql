-- V27: Tabla de relación many-to-many entre special_products y users (vendedores asignados)
CREATE TABLE special_product_vendors (
    special_product_id UUID NOT NULL REFERENCES special_products(id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (special_product_id, user_id)
);

CREATE INDEX idx_sp_vendors_product ON special_product_vendors(special_product_id);
CREATE INDEX idx_sp_vendors_user    ON special_product_vendors(user_id);
