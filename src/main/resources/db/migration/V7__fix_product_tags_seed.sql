-- V7__fix_product_tags_id_default_and_seed.sql

-- Inserta S/R con UUID fijo (no depende del default)
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_tags_name_ci
    ON product_tags (LOWER(name));

INSERT INTO product_tags (id, name, is_system, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'S/R', TRUE, NOW(), NOW())
    ON CONFLICT (LOWER(name)) DO NOTHING;
