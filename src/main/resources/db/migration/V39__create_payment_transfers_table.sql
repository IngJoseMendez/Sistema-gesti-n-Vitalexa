-- V39: Tabla de transferencias de pagos entre vendedores
-- Permite asignar el valor de un pago ya realizado a otro vendedor
-- en un mes específico, afectando su totalSold para la nómina.
CREATE TABLE payment_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Pago origen (debe estar activo/no cancelado)
    payment_id UUID NOT NULL REFERENCES payments(id),

    -- Vendedor dueño de la orden (se deriva del pago, pero se guarda para auditoría)
    origin_vendedor_id UUID NOT NULL REFERENCES users(id),

    -- Vendedor que recibirá el crédito de ventas
    dest_vendedor_id UUID NOT NULL REFERENCES users(id),

    -- Monto transferido (≤ saldo disponible del pago)
    amount NUMERIC(12, 2) NOT NULL,

    -- Mes y año al que se suma en la nómina del vendedor destino
    target_month INTEGER NOT NULL CHECK (target_month BETWEEN 1 AND 12),
    target_year  INTEGER NOT NULL CHECK (target_year  >= 2020),

    -- Motivo de la transferencia (opcional)
    reason TEXT,

    -- Estado: false = activa, true = revocada
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,

    -- Auditoría de revocación
    revoked_at         TIMESTAMP,
    revoked_by         UUID REFERENCES users(id),
    revocation_reason  TEXT,

    -- Auditoría de creación
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  UUID NOT NULL REFERENCES users(id)
);

-- Índices para las consultas más frecuentes
CREATE INDEX idx_pt_payment_id     ON payment_transfers(payment_id);
CREATE INDEX idx_pt_dest_month     ON payment_transfers(dest_vendedor_id, target_year, target_month);
CREATE INDEX idx_pt_origin_vendor  ON payment_transfers(origin_vendedor_id);
