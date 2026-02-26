-- V38: Agrega columnas sales_commission_by_goal y collection_commission_by_goal
-- a vendor_payroll_configs y payrolls.
-- false = comisión directa (% sobre lo vendido/recaudado siempre, sin meta ni umbral)
-- true  = comisión clásica (solo si cumple meta / supera umbral) — valor por defecto

ALTER TABLE vendor_payroll_configs
    ADD COLUMN IF NOT EXISTS sales_commission_by_goal      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS collection_commission_by_goal BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE payrolls
    ADD COLUMN IF NOT EXISTS sales_commission_by_goal      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS collection_commission_by_goal BOOLEAN NOT NULL DEFAULT TRUE;

