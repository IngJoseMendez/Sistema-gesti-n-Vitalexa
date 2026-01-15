-- V3__align_schema_and_constraints.sql
-- 1) Añade columnas faltantes (para BD existentes)
-- 2) Luego crea constraints / FKs de forma idempotente

-- ============================
-- A) Añadir columnas faltantes
-- ============================

-- clients
ALTER TABLE clients ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS vendedor_asignado_id UUID;

-- orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS invoice_number BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vendedor_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS client_id UUID;

-- products
ALTER TABLE products ADD COLUMN IF NOT EXISTS reorder_point INTEGER;
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- shopping_lists
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS client_id UUID;
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

-- shopping_list_items
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS list_id UUID;
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS product_id UUID;
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS default_qty INTEGER;

-- reembolsos
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS empacador_id UUID;
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS fecha TIMESTAMP;
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS notas VARCHAR(500);
ALTER TABLE reembolsos ADD COLUMN IF NOT EXISTS estado VARCHAR(50);

-- reembolso_items
ALTER TABLE reembolso_items ADD COLUMN IF NOT EXISTS reembolso_id UUID;
ALTER TABLE reembolso_items ADD COLUMN IF NOT EXISTS producto_id UUID;
ALTER TABLE reembolso_items ADD COLUMN IF NOT EXISTS cantidad INTEGER;

-- sale_goals
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS vendedor_id UUID;
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS target_amount NUMERIC(12,2);
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS current_amount NUMERIC(12,2);
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS month INTEGER;
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS year INTEGER;
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE sale_goals ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

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
BEGIN
  IF to_regclass('public.reembolsos') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reembolsos_empacador') THEN
ALTER TABLE reembolsos
    ADD CONSTRAINT fk_reembolsos_empacador FOREIGN KEY (empacador_id) REFERENCES users(id);
END IF;
END IF;
END $$;

-- REEMBOLSO_ITEMS
DO $$
BEGIN
  IF to_regclass('public.reembolso_items') IS NOT NULL AND to_regclass('public.reembolsos') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reembolso_items_reembolso') THEN
ALTER TABLE reembolso_items
    ADD CONSTRAINT fk_reembolso_items_reembolso FOREIGN KEY (reembolso_id) REFERENCES reembolsos(id);
END IF;
END IF;

  IF to_regclass('public.reembolso_items') IS NOT NULL AND to_regclass('public.products') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reembolso_items_producto') THEN
ALTER TABLE reembolso_items
    ADD CONSTRAINT fk_reembolso_items_producto FOREIGN KEY (producto_id) REFERENCES products(id);
END IF;
END IF;
END $$;

-- SALE_GOALS
DO $$
BEGIN
  IF to_regclass('public.sale_goals') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sale_goal_vendedor_month_year') THEN
ALTER TABLE sale_goals
    ADD CONSTRAINT uk_sale_goal_vendedor_month_year UNIQUE (vendedor_id, month, year);
END IF;
END IF;

  IF to_regclass('public.sale_goals') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sale_goals_vendedor') THEN
ALTER TABLE sale_goals
    ADD CONSTRAINT fk_sale_goals_vendedor FOREIGN KEY (vendedor_id) REFERENCES users(id);
END IF;
END IF;
END $$;
