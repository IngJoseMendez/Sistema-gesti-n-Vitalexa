-- Add audit fields for tracking client creation
ALTER TABLE clients ADD COLUMN IF NOT EXISTS creado_por_id UUID REFERENCES users(id);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
