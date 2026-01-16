-- V7__create_invoice_number_seq.sql
-- Crea la secuencia usada para numeración de facturas

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_class c
    WHERE c.relkind = 'S'
      AND c.relname = 'invoice_number_seq'
  ) THEN
CREATE SEQUENCE invoice_number_seq
    START WITH 1000
    INCREMENT BY 1
    MINVALUE 1;
END IF;
END $$;
