-- ============================================================
-- V34: Tablas para el sistema de nómina mensual de vendedores
-- ============================================================

-- Configuración de nómina personalizada por vendedor
CREATE TABLE vendor_payroll_configs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendedor_id                 UUID NOT NULL UNIQUE REFERENCES users(id),
    base_salary                 NUMERIC(12,2)  NOT NULL DEFAULT 0.00,
    sales_commission_pct        NUMERIC(5,4)   NOT NULL DEFAULT 0.0150,
    collection_commission_pct   NUMERIC(5,4)   NOT NULL DEFAULT 0.0300,
    collection_threshold_pct    NUMERIC(5,4)   NOT NULL DEFAULT 0.8000,
    general_commission_enabled  BOOLEAN        NOT NULL DEFAULT FALSE,
    general_commission_pct      NUMERIC(5,4)   NOT NULL DEFAULT 0.0200,
    updated_at                  TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Nóminas mensuales calculadas
CREATE TABLE payrolls (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendedor_id                 UUID    NOT NULL REFERENCES users(id),
    month                       INTEGER NOT NULL,
    year                        INTEGER NOT NULL,

    -- Salario base
    base_salary                 NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    -- Comisión por meta de ventas
    sales_goal_target           NUMERIC(12,2),
    total_sold                  NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    sales_goal_met              BOOLEAN       NOT NULL DEFAULT FALSE,
    sales_commission_pct        NUMERIC(5,4)  NOT NULL DEFAULT 0.0150,
    sales_commission_amount     NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    -- Comisión por recaudo
    prev_month_total_sold       NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_collected             NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    collection_pct              NUMERIC(7,4)  NOT NULL DEFAULT 0.00,
    collection_goal_met         BOOLEAN       NOT NULL DEFAULT FALSE,
    collection_commission_pct   NUMERIC(5,4)  NOT NULL DEFAULT 0.0300,
    collection_commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    -- Comisión general
    general_commission_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    total_global_goals          NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    general_commission_pct      NUMERIC(5,4)  NOT NULL DEFAULT 0.0200,
    general_commission_amount   NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    -- Totales
    total_commissions           NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_payout                NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    -- Auditoría
    notes                       TEXT,
    calculated_by               UUID,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (vendedor_id, month, year)
);

-- Índices para consultas frecuentes
CREATE INDEX idx_payrolls_vendedor_id    ON payrolls(vendedor_id);
CREATE INDEX idx_payrolls_month_year     ON payrolls(month, year);
CREATE INDEX idx_vpc_vendedor_id         ON vendor_payroll_configs(vendedor_id);

