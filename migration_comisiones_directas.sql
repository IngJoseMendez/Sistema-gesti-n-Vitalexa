-- ============================================================
-- Migración: Comisiones directas (sin meta / sin umbral)
-- Fecha: 2026-02-25
-- ============================================================
-- Agrega dos flags a vendor_payroll_configs y payrolls:
--   sales_commission_by_goal    → true = solo si cumple meta (comportamiento previo)
--                                 false = % directo sobre lo vendido, siempre
--   collection_commission_by_goal → true = solo si supera umbral (comportamiento previo)
--                                   false = % directo sobre lo recaudado, siempre
-- ============================================================

-- ── vendor_payroll_configs ───────────────────────────────────
ALTER TABLE vendor_payroll_configs
    ADD COLUMN IF NOT EXISTS sales_commission_by_goal     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS collection_commission_by_goal BOOLEAN NOT NULL DEFAULT TRUE;

-- ── payrolls (para auditoría histórica) ──────────────────────
ALTER TABLE payrolls
    ADD COLUMN IF NOT EXISTS sales_commission_by_goal     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS collection_commission_by_goal BOOLEAN NOT NULL DEFAULT TRUE;

-- ── Comentarios descriptivos ──────────────────────────────────
COMMENT ON COLUMN vendor_payroll_configs.sales_commission_by_goal
    IS 'true = comisión de ventas solo si cumple meta; false = % directo sobre lo vendido siempre';
COMMENT ON COLUMN vendor_payroll_configs.collection_commission_by_goal
    IS 'true = comisión de recaudo solo si supera umbral; false = % directo sobre lo recaudado siempre';
COMMENT ON COLUMN payrolls.sales_commission_by_goal
    IS 'Modalidad de comisión de ventas aplicada en este cálculo';
COMMENT ON COLUMN payrolls.collection_commission_by_goal
    IS 'Modalidad de comisión de recaudo aplicada en este cálculo';

