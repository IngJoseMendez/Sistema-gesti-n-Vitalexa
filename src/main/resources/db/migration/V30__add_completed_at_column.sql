-- V30: Agregar columna completed_at a la tabla orders

ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

COMMENT ON COLUMN orders.completed_at IS 'Fecha y hora exactas cuando la orden fue marcada como COMPLETADO';
