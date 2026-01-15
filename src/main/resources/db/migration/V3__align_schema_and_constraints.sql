-- V3__align_schema_and_constraints.sql
-- 1) Añade columnas faltantes (para BD existentes)
-- 2) Luego crea constraints / FKs de forma idempotente

-- ============================
-- A) Añadir columnas faltantes
-- ============================

-- clients
ALTER TABLE clients ADD COLUMN IF NOT EXISTS user_id UUID; [web:616]
ALTER TABLE clients ADD COLUMN IF NOT EXISTS vendedor_asignado_id UUID; [web:616]

-- orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS invoice_number BIGINT; [web:616]
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vendedor_id UUID; [web:616]
ALTER TABLE orders ADD COLUMN IF NOT EXISTS client_id UUID; [web:616]

-- products
ALTER TABLE products ADD COLUMN IF NOT EXISTS reorder_point INTEGER; [web:616]
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMP; [web:616]
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP; [web:616]

-- shopping_lists
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS client_id UUID; [web:616]
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS name VARCHAR(255); [web:616]
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS created_at TIMESTAMP; [web:616]

-- shopping_list_items
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS list_id UUID; [web:616]
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS product_id UUID; [web:616]
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS default_qty INTEGER; [web:616]

-- reembolsos
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS empacador_id UUID; [web:616]
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS fecha TIMESTAMP; [web:616]
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS notas VARCHAR(500); [web:616]
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS estado VARCHAR(50); [web:616]

-- reembolso_items
ALTER TABLE reembolso_items ADD COLUMN IF NOT EXISTS reembolso_id UUID; [web:616]
ALTER TABLE reembolso_items ADD COLUMN IF NOT EXISTS producto_id UUID; [web:616]
ALTER TABLE reembolso_items ADD COLUMN IF NOT EXISTS cantidad INTEGER; [web:616]

-- sale_goals
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS vendedor_id UUID; [web:616]
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS target_amount NUMERIC(12,2); [web:616]
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS current_amount NUMERIC(12,2); [web:616]
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS month INTEGER; [web:616]
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS year INTEGER; [web:616]
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS created_at TIMESTAMP; [web:616]
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP; [web:616]

-- ============================
-- B) Constraints / FKs (safe)
-- ============================

-- CLIENTS
DO $$
BEGIN
  IF to_regclass('public.clients') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_clients_user') THEN
ALTER TABLE clients ADD CONSTRAINT uk_clients_user UNIQUE (user_id);
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_clients_user') THEN
ALTER TABLE clients
    ADD CONSTRAINT fk_clients_user FOREIGN KEY (user_id) REFERENCES users(id);
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_clients_vendedor_asignado') THEN
ALTER TABLE clients
    ADD CONSTRAINT fk_clients_vendedor_asignado FOREIGN KEY (vendedor_asignado_id) REFERENCES users(id);
END IF;
END IF;
END $$;

-- ORDERS
DO $$
BEGIN
  IF to_regclass('public.orders') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_orders_invoice_number') THEN
ALTER TABLE orders ADD CONSTRAINT uk_orders_invoice_number UNIQUE (invoice_number);
END IF;
END IF;

  IF to_regclass('public.orders') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_vendedor') THEN
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_vendedor FOREIGN KEY (vendedor_id) REFERENCES users(id);
END IF;
END IF;

  IF to_regclass('public.orders') IS NOT NULL AND to_regclass('public.clients') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_client') THEN
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_client FOREIGN KEY (client_id) REFERENCES clients(id);
END IF;
END IF;
END $$;

-- ORDER_ITEMS
DO $$
BEGIN
  IF to_regclass('public.order_items') IS NOT NULL AND to_regclass('public.orders') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_items_order') THEN
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id);
END IF;
END IF;

  IF to_regclass('public.order_items') IS NOT NULL AND to_regclass('public.products') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_items_product') THEN
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id);
END IF;
END IF;
END $$;

-- SHOPPING_LISTS
DO $$
BEGIN
  IF to_regclass('public.shopping_lists') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_shopping_list_client_name') THEN
ALTER TABLE shopping_lists
    ADD CONSTRAINT uk_shopping_list_client_name UNIQUE (client_id, name);
END IF;
END IF;

  IF to_regclass('public.shopping_lists') IS NOT NULL AND to_regclass('public.clients') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_shopping_lists_client') THEN
ALTER TABLE shopping_lists
    ADD CONSTRAINT fk_shopping_lists_client FOREIGN KEY (client_id) REFERENCES clients(id);
END IF;
END IF;
END $$;

-- SHOPPING_LIST_ITEMS
DO $$
BEGIN
  IF to_regclass('public.shopping_list_items') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_list_product') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT uk_list_product UNIQUE (list_id, product_id);
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_list_items_default_qty_min_1') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT ck_list_items_default_qty_min_1 CHECK (default_qty >= 1);
END IF;
END IF;

  IF to_regclass('public.shopping_list_items') IS NOT NULL AND to_regclass('public.shopping_lists') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_list_items_list') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_list_items_list FOREIGN KEY (list_id) REFERENCES shopping_lists(id);
END IF;
END IF;

  IF to_regclass('public.shopping_list_items') IS NOT NULL AND to_regclass('public.products') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_list_items_product') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_list_items_product FOREIGN KEY (product_id) REFERENCES products(id);
END IF;
END IF;
END $$;

-- REEMBOLSOS
DO $$
B
