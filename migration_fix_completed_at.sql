-- ============================================================
-- MIGRACIÓN: Corregir completed_at en órdenes históricas
-- ============================================================
-- Problema: Muchas órdenes con estado COMPLETADO tienen
--   completed_at = NULL porque fueron completadas antes de que
--   se implementara ese campo.
--
-- Solución: Para esas órdenes, asignar completed_at = fecha
--   (fecha de creación) como mejor aproximación disponible.
--   En el sistema ya quedan correctas las órdenes futuras,
--   porque desde ahora se guarda completed_at al momento de
--   cambiar el estado a COMPLETADO.
-- ============================================================

UPDATE orders
SET completed_at = fecha
WHERE estado = 'COMPLETADO'
  AND completed_at IS NULL;

-- Verificación: cuántas filas se actualizaron (ejecutar después)
-- SELECT COUNT(*) FROM orders WHERE estado = 'COMPLETADO' AND completed_at IS NULL;

