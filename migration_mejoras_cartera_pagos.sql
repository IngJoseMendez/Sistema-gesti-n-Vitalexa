-- =========================================================
-- MIGRACIÓN: MEJORAS SISTEMA DE CARTERA Y PAGOS
-- Fecha: 2026-02-17
-- Descripción: Agregar campos para método de pago, fecha real,
--              y auditoría de anulación de pagos
-- =========================================================

-- 1. Agregar nuevas columnas a la tabla payments
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50) DEFAULT 'EFECTIVO',
ADD COLUMN IF NOT EXISTS actual_payment_date DATE,
ADD COLUMN IF NOT EXISTS is_cancelled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS cancelled_by UUID,
ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

-- 2. Crear constraint para cancelled_by (referencia a users)
ALTER TABLE payments
ADD CONSTRAINT IF NOT EXISTS fk_payment_cancelled_by
FOREIGN KEY (cancelled_by) REFERENCES users(id);

-- 3. Migrar datos existentes - Copiar payment_date a actual_payment_date
UPDATE payments
SET actual_payment_date = DATE(payment_date)
WHERE actual_payment_date IS NULL;

-- 4. Crear índices para mejorar rendimiento
CREATE INDEX IF NOT EXISTS idx_payments_actual_date ON payments(actual_payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_is_cancelled ON payments(is_cancelled);
CREATE INDEX IF NOT EXISTS idx_payments_order_not_cancelled ON payments(order_id, is_cancelled);
CREATE INDEX IF NOT EXISTS idx_payments_payment_method ON payments(payment_method);

-- 5. Agregar comentarios a las columnas
COMMENT ON COLUMN payments.payment_method IS 'Método de pago utilizado: EFECTIVO, TRANSFERENCIA, CHEQUE, TARJETA, CREDITO, OTRO';
COMMENT ON COLUMN payments.actual_payment_date IS 'Fecha real en que se realizó el pago (puede ser diferente a payment_date)';
COMMENT ON COLUMN payments.payment_date IS 'Timestamp automático de registro del pago en el sistema';
COMMENT ON COLUMN payments.is_cancelled IS 'Indica si el pago fue anulado (soft delete)';
COMMENT ON COLUMN payments.cancelled_at IS 'Fecha y hora en que se anuló el pago';
COMMENT ON COLUMN payments.cancelled_by IS 'Usuario que anuló el pago';
COMMENT ON COLUMN payments.cancellation_reason IS 'Razón por la cual se anuló el pago';

-- 6. Verificar integridad de datos
SELECT COUNT(*) as total_payments,
       SUM(CASE WHEN is_cancelled THEN 1 ELSE 0 END) as cancelled_payments,
       SUM(CASE WHEN is_cancelled = FALSE OR is_cancelled IS NULL THEN 1 ELSE 0 END) as active_payments,
       SUM(CASE WHEN actual_payment_date IS NULL THEN 1 ELSE 0 END) as missing_actual_date
FROM payments;

-- 7. Mostrar estadísticas por método de pago
SELECT payment_method,
       COUNT(*) as cantidad,
       SUM(amount) as total_monto
FROM payments
WHERE is_cancelled = FALSE OR is_cancelled IS NULL
GROUP BY payment_method
ORDER BY cantidad DESC;

-- =========================================================
-- ROLLBACK (en caso de necesitar revertir cambios)
-- =========================================================
-- NOTA: Ejecutar solo si es necesario revertir la migración
--
-- DROP INDEX IF EXISTS idx_payments_actual_date;
-- DROP INDEX IF EXISTS idx_payments_is_cancelled;
-- DROP INDEX IF EXISTS idx_payments_order_not_cancelled;
-- DROP INDEX IF EXISTS idx_payments_payment_method;
--
-- ALTER TABLE payments DROP CONSTRAINT IF EXISTS fk_payment_cancelled_by;
--
-- ALTER TABLE payments
-- DROP COLUMN IF EXISTS payment_method,
-- DROP COLUMN IF EXISTS actual_payment_date,
-- DROP COLUMN IF EXISTS is_cancelled,
-- DROP COLUMN IF EXISTS cancelled_at,
-- DROP COLUMN IF EXISTS cancelled_by,
-- DROP COLUMN IF EXISTS cancellation_reason;
-- =========================================================

COMMIT;

