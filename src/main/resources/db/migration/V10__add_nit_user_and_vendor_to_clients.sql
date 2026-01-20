-- Agregar columna NIT
ALTER TABLE clients ADD COLUMN IF NOT EXISTS nit VARCHAR(255);

-- Agregar columna user_id para login de clientes
ALTER TABLE clients ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE clients ADD CONSTRAINT uk_clients_user_id UNIQUE (user_id);
ALTER TABLE clients ADD CONSTRAINT fk_clients_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Agregar columna vendedor_asignado_id
ALTER TABLE clients ADD COLUMN IF NOT EXISTS vendedor_asignado_id UUID;
ALTER TABLE clients ADD CONSTRAINT fk_clients_vendedor_asignado_id FOREIGN KEY (vendedor_asignado_id) REFERENCES users(id);
