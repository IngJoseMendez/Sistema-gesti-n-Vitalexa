-- V5__add_product_tag_reference.sql
-- Agregar referencia a product_tags en la tabla products

-- ============================
-- Agregar columna product_tag_id
-- ============================
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_tag_id UUID;

-- ============================
-- Crear constraint FK (de forma segura)
-- ============================
DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL AND to_regclass('public.product_tags') IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_product_tag') THEN
            ALTER TABLE products
                ADD CONSTRAINT fk_products_product_tag FOREIGN KEY (product_tag_id) REFERENCES product_tags(id) ON DELETE SET NULL;
        END IF;
    END IF;
END $$;

-- ============================
-- Crear índice para queries por tag
-- ============================
CREATE INDEX IF NOT EXISTS idx_products_product_tag_id ON products(product_tag_id);

