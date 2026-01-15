-- V2__create_missing_tables.sql
-- Crea tablas faltantes para Vitalexa (PostgreSQL).
-- Sin constraints aquí. Solo CREATE TABLE / extensión.

-- Intentar habilitar pgcrypto sin tumbar el deploy si no hay permisos.
DO $$
BEGIN
BEGIN
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
EXCEPTION
    WHEN insufficient_privilege THEN
      RAISE NOTICE 'No permission to create pgcrypto, skipping';
END;
END $$;

-- USERS
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
    );

-- CLIENTS
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

-- PRODUCTS
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

-- ORDERS
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

-- ORDER_ITEMS
CREATE TABLE IF NOT EXISTS order_items (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(12,2) NOT NULL,
    sub_total NUMERIC(12,2) NOT NULL,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL
    );

-- SHOPPING_LISTS
CREATE TABLE IF NOT EXISTS shopping_lists (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
    );

-- SHOPPING_LIST_ITEMS
CREATE TABLE IF NOT EXISTS shopping_list_items (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id UUID NOT NULL,
    product_id UUID NOT NULL,
    default_qty INTEGER NOT NULL
    );

-- REEMBOLSOS + ITEMS
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

-- SALE_GOALS
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
