-- V31: Tabla de Promociones Especiales (standalone o vinculadas a una promo padre)

CREATE TABLE special_promotions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre                VARCHAR(255),
    descripcion           TEXT,
    type                  VARCHAR(20),
    buy_quantity          INTEGER,
    pack_price            NUMERIC(12, 2),
    main_product_id       UUID REFERENCES products(id),
    active                BOOLEAN NOT NULL DEFAULT true,
    valid_from            TIMESTAMP,
    valid_until           TIMESTAMP,
    parent_promotion_id   UUID REFERENCES promotions(id),
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP
);

CREATE INDEX idx_special_promotions_parent ON special_promotions(parent_promotion_id);
CREATE INDEX idx_special_promotions_active ON special_promotions(active);

-- Tabla de relación many-to-many entre special_promotions y users (vendedores asignados)
CREATE TABLE special_promotion_vendors (
    special_promotion_id  UUID NOT NULL REFERENCES special_promotions(id) ON DELETE CASCADE,
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (special_promotion_id, user_id)
);

CREATE INDEX idx_sp_promo_vendors_promo ON special_promotion_vendors(special_promotion_id);
CREATE INDEX idx_sp_promo_vendors_user  ON special_promotion_vendors(user_id);
