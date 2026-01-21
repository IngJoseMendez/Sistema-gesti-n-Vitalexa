-- V11: Crear tabla de promociones
-- Fecha: 2026-01-21
-- Descripción: Tabla para gestionar promociones con dos tipos: PACK y BUY_GET_FREE

CREATE TABLE promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PACK', 'BUY_GET_FREE')),
    buy_quantity INTEGER NOT NULL CHECK (buy_quantity > 0),
    free_quantity INTEGER CHECK (free_quantity > 0),
    pack_price DECIMAL(12,2),
    main_product_id UUID NOT NULL,
    free_product_id UUID,
    allow_stack_with_discounts BOOLEAN DEFAULT false,
    requires_assortment_selection BOOLEAN DEFAULT true,
    active BOOLEAN DEFAULT true,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign keys
    CONSTRAINT fk_promotion_main_product FOREIGN KEY (main_product_id) 
        REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_promotion_free_product FOREIGN KEY (free_product_id) 
        REFERENCES products(id) ON DELETE SET NULL,
    
    -- Constraints de validación
    CONSTRAINT chk_pack_price CHECK (
        (type = 'PACK' AND pack_price IS NOT NULL) OR 
        (type = 'BUY_GET_FREE' AND pack_price IS NULL)
    ),
    CONSTRAINT chk_valid_dates CHECK (
        valid_from IS NULL OR valid_until IS NULL OR valid_from < valid_until
    )
);

-- Índices para mejorar performance
CREATE INDEX idx_promotions_active ON promotions(active);
CREATE INDEX idx_promotions_type ON promotions(type);
CREATE INDEX idx_promotions_main_product ON promotions(main_product_id);
CREATE INDEX idx_promotions_valid_dates ON promotions(valid_from, valid_until) WHERE active = true;

-- Comentarios para documentación
COMMENT ON TABLE promotions IS 'Tabla para gestionar promociones de productos';
COMMENT ON COLUMN promotions.type IS 'Tipo de promoción: PACK (cantidad fija con precio) o BUY_GET_FREE (compra X y recibe Y)';
COMMENT ON COLUMN promotions.buy_quantity IS 'Cantidad de productos que debe comprar el cliente';
COMMENT ON COLUMN promotions.free_quantity IS 'Cantidad de productos gratis/surtidos que recibe';
COMMENT ON COLUMN promotions.pack_price IS 'Precio total del pack (solo para tipo PACK)';
COMMENT ON COLUMN promotions.allow_stack_with_discounts IS 'Si true, permite combinar esta promoción con descuentos normales';
COMMENT ON COLUMN promotions.requires_assortment_selection IS 'Si true, admin debe seleccionar productos surtidos manualmente';
