-- Remove constraint that prevents pack_price for BUY_GET_FREE promotions
ALTER TABLE promotions DROP CONSTRAINT IF EXISTS chk_pack_price;
