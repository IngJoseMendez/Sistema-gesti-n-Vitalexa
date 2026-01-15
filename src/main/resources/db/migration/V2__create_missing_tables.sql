-- V2__create_missing_tables.sql
-- Crea tablas faltantes para Vitalexa (PostgreSQL)
-- Diseñado para no fallar si las tablas ya existen.

-- UUID default helper (Postgres)
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- para gen_random_uuid() [web:516][web:517]

-- ============================
-- 1) USERS
-- ============================
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
    );

-- ============================
-- 2) CLIENTS
-- ============================
CREATE TABLE IF NOT EXISTS clients (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255),
    total_compras NUMERIC(12,2) NOT NULL DEFAULT 0,
    email VARCHAR(255),
    ultima_compra TIMESTAMP,
    direccion VARCHAR(255),
    telefono VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    user_id UUID,
    vendedor_asignado_id UUID
    );

-- ============================
-- 3) PRODUCTS
-- ============================
CREATE TABLE IF NOT EXISTS products (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255),
    descripcion TEXT,
    precio NUMERIC(12,2),
    stock INTEGER,
    image_url VARCHAR(2048),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    reorder_point INTEGER
    );

-- ============================
-- 4) ORDERS
-- ============================
CREATE TABLE IF NOT EXISTS orders (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha TIMESTAMP NOT NULL,
    total NUMERIC(12,2) NOT NULL,
    estado VARCHAR(50) NOT NULL,
    notas TEXT,
    invoice_number BIGINT,
    vendedor_id UUID NOT NULL,
    client_id UUID
    );

-- ============================
-- 5) ORDER_ITEMS
-- ============================
CREATE TABLE IF NOT EXISTS order_items (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(12,2) NOT NULL,
    sub_total NUMERIC(12,2) NOT NULL,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL
    );

-- ============================
-- 6) SHOPPING_LISTS
-- ============================
CREATE TABLE IF NOT EXISTS shopping_lists (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
    );

-- ============================
-- 7) SHOPPING_LIST_ITEMS
-- ============================
CREATE TABLE IF NOT EXISTS shopping_list_items (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id UUID NOT NULL,
    product_id UUID NOT NULL,
    default_qty INTEGER NOT NULL
    );

-- ============================
-- 8) REEMBOLSOS + ITEMS
-- ============================
CREATE TABLE IF NOT EXISTS reembolsos (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empacador_id UUID NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT NOW(),
    notas VARCHAR(500),
    estado VARCHAR(50) NOT NULL
    );

CREATE TABLE IF NOT EXISTS reembolso_items (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reembolso_id UUID NOT NULL,
    producto_id UUID NOT NULL,
    cantidad INTEGER NOT NULL
    );

-- ============================
-- 9) SALE_GOALS
-- ============================
CREATE TABLE IF NOT EXISTS sale_goals (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendedor_id UUID NOT NULL,
    target_amount NUMERIC(12,2) NOT NULL,
    current_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- =========================================================
-- Constraints / FKs idempotentes (si no existen, las crea)
-- =========================================================

-- CLIENTS constraints
DO $$
BEGIN
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
END $$;

-- ORDERS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_orders_invoice_number') THEN
ALTER TABLE orders ADD CONSTRAINT uk_orders_invoice_number UNIQUE (invoice_number);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_vendedor') THEN
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_vendedor FOREIGN KEY (vendedor_id) REFERENCES users(id);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_client') THEN
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_client FOREIGN KEY (client_id) REFERENCES clients(id);
END IF;
END $$;

-- ORDER_ITEMS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_items_order') THEN
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_items_product') THEN
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id);
END IF;
END $$;

-- SHOPPING_LISTS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_shopping_list_client_name') THEN
ALTER TABLE shopping_lists
    ADD CONSTRAINT uk_shopping_list_client_name UNIQUE (client_id, name);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_shopping_lists_client') THEN
ALTER TABLE shopping_lists
    ADD CONSTRAINT fk_shopping_lists_client FOREIGN KEY (client_id) REFERENCES clients(id);
END IF;
END $$;

-- SHOPPING_LIST_ITEMS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_list_product') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT uk_list_product UNIQUE (list_id, product_id);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_list_items_list') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_list_items_list FOREIGN KEY (list_id) REFERENCES shopping_lists(id);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_list_items_product') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_list_items_product FOREIGN KEY (product_id) REFERENCES products(id);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_list_items_default_qty_min_1') THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT ck_list_items_default_qty_min_1 CHECK (default_qty >= 1);
END IF;
END $$;

-- REEMBOLSOS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reembolsos_empacador') THEN
ALTER TABLE reembolsos
    ADD CONSTRAINT fk_reembolsos_empacador FOREIGN KEY (empacador_id) REFERENCES users(id);
END IF;
END $$;

-- REEMBOLSO_ITEMS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reembolso_items_reembolso') THEN
ALTER TABLE reembolso_items
    ADD CONSTRAINT fk_reembolso_items_reembolso FOREIGN KEY (reembolso_id) REFERENCES reembolsos(id);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reembolso_items_producto') THEN
ALTER TABLE reembolso_items
    ADD CONSTRAINT fk_reembolso_items_producto FOREIGN KEY (producto_id) REFERENCES products(id);
END IF;
END $$;

-- SALE_GOALS constraints
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sale_goal_vendedor_month_year') THEN
ALTER TABLE sale_goals
    ADD CONSTRAINT uk_sale_goal_vendedor_month_year UNIQUE (vendedor_id, month, year);
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sale_goals_vendedor') THEN
ALTER TABLE sale_goals
    ADD CONSTRAINT fk_sale_goals_vendedor FOREIGN KEY (vendedor_id) REFERENCES users(id);
END IF;
END $$;
