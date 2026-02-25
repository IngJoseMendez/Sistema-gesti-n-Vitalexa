-- Ampliar precisión de collection_pct en payrolls para soportar
-- valores de porcentaje reales (0.00 a 999.9999).
-- La definición original NUMERIC(5,4) solo permitía hasta 9.9999,
-- pero el porcentaje de recaudo puede ser 83.33, 120.00, etc.
ALTER TABLE payrolls
    ALTER COLUMN collection_pct TYPE NUMERIC(7,4);

