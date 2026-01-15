-- ============================
-- 1) ORDERS: invoice_number UNIQUE
-- ============================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uk_orders_invoice_number'
  ) THEN
ALTER TABLE orders
    ADD CONSTRAINT uk_orders_invoice_number UNIQUE (invoice_number);
END IF;
END $$;

-- ============================
-- 2) CLIENTS: user_id UNIQUE + FK, vendedor_asignado FK
-- ============================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uk_clients_user'
  ) THEN
ALTER TABLE clients
    ADD CONSTRAINT uk_clients_user UNIQUE (user_id);
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_clients_user'
  ) THEN
ALTER TABLE clients
    ADD CONSTRAINT fk_clients_user
        FOREIGN KEY (user_id) REFERENCES users(id);
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_clients_vendedor_asignado'
  ) THEN
ALTER TABLE clients
    ADD CONSTRAINT fk_clients_vendedor_asignado
        FOREIGN KEY (vendedor_asignado_id) REFERENCES users(id);
END IF;
END $$;

-- ============================
-- 3) SHOPPING LISTS: UNIQUE(client_id,name) + FK(client_id)
-- ============================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uk_shopping_list_client_name'
  ) THEN
ALTER TABLE shopping_lists
    ADD CONSTRAINT uk_shopping_list_client_name UNIQUE (client_id, name);
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_shopping_lists_client'
  ) THEN
ALTER TABLE shopping_lists
    ADD CONSTRAINT fk_shopping_lists_client
        FOREIGN KEY (client_id) REFERENCES clients(id);
END IF;
END $$;

-- ============================
-- 4) SHOPPING LIST ITEMS: UNIQUE(list_id,product_id) + FKs + CHECK qty
-- ============================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uk_list_product'
  ) THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT uk_list_product UNIQUE (list_id, product_id);
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_list_items_list'
  ) THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_list_items_list
        FOREIGN KEY (list_id) REFERENCES shopping_lists(id);
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_list_items_product'
  ) THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_list_items_product
        FOREIGN KEY (product_id) REFERENCES products(id);
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'ck_list_items_default_qty_min_1'
  ) THEN
ALTER TABLE shopping_list_items
    ADD CONSTRAINT ck_list_items_default_qty_min_1 CHECK (default_qty >= 1);
END IF;
END $$;
