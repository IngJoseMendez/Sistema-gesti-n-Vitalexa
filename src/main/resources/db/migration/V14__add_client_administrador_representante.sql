-- V14: Add administrador and representante_legal fields to clients table

ALTER TABLE clients ADD COLUMN administrador VARCHAR(255);
ALTER TABLE clients ADD COLUMN representante_legal VARCHAR(255);
