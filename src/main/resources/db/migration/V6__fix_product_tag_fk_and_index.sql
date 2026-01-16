-- V6__product_tags_and_fk.sql
-- Crea tabla product_tags + unique index case-insensitive + seed S/R
-- y agrega la referencia en products (columna, FK e índice).
-- Diseñado para ser idempotente.

-- 0) Extensión para generar UUIDs si hace falta
-- Railway/Postgres normalmente la permite, pero por seguridad:
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) Tabla product_tags (según tu entidad)
CREATE TABLE IF NOT EXISTS product_tags (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            name VARCHAR(100) NOT NULL,
                                            is_system BOOLEAN NOT NULL DEFAULT FALSE,
                                            created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                            updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2) Unicidad case-insensitive para name
-- (recomendado para evitar duplicados tipo "sr", "S/R", "s/r")
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_tags_name_ci
    ON product_tags (LOWER(name));

-- (Opcional) Si quieres también búsqueda rápida por name:
-- CREATE INDEX IF NOT EXISTS ix_product_tags_name_ci ON product_tags (LOWER(name));

-- 3) Seed: etiqueta del sistema "S/R" (idempotente)
INSERT INTO product_tags (name, is_system, created_at, updated_at)
VALUES ('S/R', TRUE, NOW(), NOW())
ON CONFLICT (LOWER(name)) DO NOTHING;

-- 4) Columna en products (idempotente)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS product_tag_id UUID;

-- 5) FK (idempotente)
DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_product_tag'
        ) THEN
            ALTER TABLE products
                ADD CONSTRAINT fk_products_product_tag
                    FOREIGN KEY (product_tag_id)
                        REFERENCES product_tags(id)
                        ON DELETE SET NULL;
        END IF;
    END $$;

-- 6) Índice para filtrar productos por tag
CREATE INDEX IF NOT EXISTS idx_products_product_tag_id
    ON products(product_tag_id);
