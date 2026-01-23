-- V16: Rename vendedor usernames
-- This migration runs BEFORE the application starts, so references will work

-- Rename nina -> NinaTorres
UPDATE users SET username = 'NinaTorres' WHERE username = 'nina';

-- Rename gisela -> YicelaSandoval
UPDATE users SET username = 'YicelaSandoval' WHERE username = 'gisela';

-- Rename mercy -> MercyMaestre
UPDATE users SET username = 'MercyMaestre' WHERE username = 'mercy';
