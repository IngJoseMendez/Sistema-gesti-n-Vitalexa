-- V13: Agregar constraint para validar estados de orden incluyendo PENDING_PROMOTION_COMPLETION
-- Fecha: 2026-01-21
-- Descripción: Asegurar que solo se usen estados válidos en la columna estado de orders

-- Eliminar constraint anterior si existe
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orden_status;

-- Agregar constraint actualizado con el nuevo estado
ALTER TABLE orders
ADD CONSTRAINT chk_orden_status CHECK (
    estado IN (
        'PENDIENTE',
        'PENDING_PROMOTION_COMPLETION',
        'CONFIRMADO',
        'COMPLETADO',
        'CANCELADO'
    )
);

-- Comentario para documentación
COMMENT ON CONSTRAINT chk_orden_status ON orders IS 'Valida que el estado de la orden sea uno de los valores permitidos. PENDING_PROMOTION_COMPLETION indica que la orden está esperando que admin seleccione productos surtidos de una promoción.';
