# GUÍA DE DEPLOY: Promociones y Stock Negativo

## 📋 Checklist Pre-Deploy

### 1. Compilación
```bash
cd C:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa

# Limpiar y compilar
./mvnw.cmd clean compile

# Si hay errores, revisar:
# - OrderItem.java: Campos nuevos de promociones
# - Order.java: Método recalculateTotal()
# - OrderServiceImpl.java: processPromotions() y processBonifiedItems()
# - InvoiceServiceImpl.java: Agrupación por promotionInstanceId
# - OrderItemMapper.java: Nuevos mappings
```

### 2. Pruebas
```bash
# Ejecutar tests
./mvnw.cmd test

# Tests críticos a validar:
# - OrderItemTest: promotionInstanceId se genera
# - OrderTest: recalculateTotal() respeta packPrice
# - OrderServiceImplTest: processPromotions() crea IDs únicos
# - OrderServiceImplTest: updateOrder() preserva precios
# - InvoiceServiceImplTest: agrupa por promotionInstanceId
```

### 3. Build del Proyecto
```bash
# Generar JAR
./mvnw.cmd package -DskipTests

# Verificar archivo generado:
# target/sistema_gestion_vitalexa-*.jar
```

---

## 🗄️ Migraciones de Base de Datos

### Paso 1: Verificar Migraciones Pendientes
```bash
# Las migraciones Flyway se ejecutarán automáticamente en startup
# Pero puede revisar manualmente:
SELECT * FROM flyway_schema_history ORDER BY success DESC, installed_rank DESC;
```

### Paso 2: Contenido de V29 (Ya creada)
```sql
-- V29__add_promotion_instance_fields.sql
ALTER TABLE order_items ADD COLUMN promotion_instance_id UUID NULL;
ALTER TABLE order_items ADD COLUMN promotion_pack_price NUMERIC(12, 2) NULL;
ALTER TABLE order_items ADD COLUMN promotion_group_index INTEGER NULL;
CREATE INDEX idx_order_items_promotion_instance ON order_items(promotion_instance_id);
```

### Paso 3: Rollback si es Necesario
```sql
-- Si necesitas revertir (aunque no es recomendado):
ALTER TABLE order_items DROP COLUMN promotion_instance_id;
ALTER TABLE order_items DROP COLUMN promotion_pack_price;
ALTER TABLE order_items DROP COLUMN promotion_group_index;
DROP INDEX idx_order_items_promotion_instance;
```

---

## 🚀 Pasos de Deploy

### En Ambiente de Desarrollo/Testing

1. **Parar la aplicación actual**
   ```bash
   # Si está corriendo en Docker
   docker-compose down
   
   # O si está en proceso local
   Ctrl+C en terminal
   ```

2. **Hacer backup de base de datos**
   ```bash
   # PostgreSQL
   pg_dump -U usuario -d vitalexa_db > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

3. **Compilar nueva versión**
   ```bash
   ./mvnw.cmd clean package -DskipTests
   ```

4. **Iniciar aplicación (las migraciones correrán automáticamente)**
   ```bash
   # Docker
   docker-compose up -d
   
   # O local
   java -jar target/sistema_gestion_vitalexa-*.jar
   ```

5. **Verificar logs para migraciones**
   ```bash
   # Buscar en logs
   docker logs sistema_gestion_vitalexa
   
   # Debe ver algo como:
   # 2025-02-13 10:30:45 INFO o.f.c.i.database.Schema - Schema validated successfully
   # 2025-02-13 10:30:46 INFO o.f.c.i.database.validate.Schema - Validating schema
   # 2025-02-13 10:30:47 INFO o.f.c.i.s.PlaceholderResolver - Placeholders - {}
   # 2025-02-13 10:30:48 INFO o.f.c.c.MetaDataTableImpl - Loading migration V29__add_promotion_instance_fields.sql
   ```

6. **Probar endpoints clave**
   ```bash
   # Crear orden con múltiples promociones
   POST /api/orders
   {
     "clientId": "...",
     "items": [...],
     "promotionIds": ["promo-a", "promo-a", "promo-b"],
     "notas": "Test promociones duplicadas"
   }
   
   # Verificar respuesta:
   # - Cada item de promoción tiene promotionInstanceId ✅
   # - Cada item tiene promotionPackPrice ✅
   # - Order.total respeta packPrice (no suma) ✅
   ```

---

## 📊 Validaciones Post-Deploy

### 1. Órdenes Existentes (Backward Compatibility)

```sql
-- Verificar órdenes viejas (sin promotionInstanceId)
SELECT COUNT(*) as total_items,
       COUNT(CASE WHEN promotion_instance_id IS NULL THEN 1 END) as sin_instance_id,
       COUNT(CASE WHEN promotion_instance_id IS NOT NULL THEN 1 END) as con_instance_id
FROM order_items;

-- Todas las órdenes viejas deben funcionar sin cambios
-- El sistema usa promotion_id como fallback si promotion_instance_id es NULL
```

### 2. Nuevas Órdenes con Promociones

```sql
-- Verificar que nuevas órdenes tienen ID único
SELECT promotion_id, 
       promotion_instance_id,
       COUNT(*) as cantidad
FROM order_items
WHERE is_promotion_item = true
  AND promotion_instance_id IS NOT NULL
GROUP BY promotion_id, promotion_instance_id;

-- Resultado esperado: Cada combinación (promo_id + instance_id) aparece UNA SOLA VEZ
```

### 3. Totales de Órdenes

```sql
-- Verificar órdenes con múltiples promociones
SELECT o.id as order_id,
       o.total,
       COUNT(DISTINCT oi.promotion_instance_id) as promo_instances,
       COUNT(DISTINCT oi.promotion_id) as promo_types
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE oi.is_promotion_item = true
GROUP BY o.id, o.total
HAVING COUNT(DISTINCT oi.promotion_instance_id) > 1;

-- Resultado esperado: promo_instances > promo_types (hay duplicadas)
```

### 4. Bonificados sin Stock

```sql
-- Verificar que bonificados se almacenan en una sola línea
SELECT product_id,
       cantidad,
       cantidad_descontada,
       cantidad_pendiente,
       COUNT(*) as lineas
FROM order_items
WHERE is_bonified = true
  AND cantidad_pendiente > 0
GROUP BY product_id, cantidad, cantidad_descontada, cantidad_pendiente;

-- Resultado esperado: Cada combinación tiene COUNT = 1 (NO duplicadas)
```

---

## 🔧 Troubleshooting

### Problema: "Unknown column 'promotion_instance_id'"

**Causa:** La migración V29 no se ejecutó
**Solución:**
```bash
# 1. Verificar que el archivo existe
ls src/main/resources/db/migration/V29*

# 2. Limpiar caché de Flyway y reintentar
DELETE FROM flyway_schema_history WHERE version >= 29;

# 3. Reiniciar aplicación
```

### Problema: "Promociones agrupadas incorrectamente"

**Causa:** Órdenes antiguas sin `promotionInstanceId`
**Solución:**
```bash
# Generar IDs para órdenes antiguas (opcionalmente)
UPDATE order_items
SET promotion_instance_id = gen_random_uuid()
WHERE is_promotion_item = true 
  AND promotion_instance_id IS NULL;

# O dejar NULL y usar fallback (recomendado)
```

### Problema: "Total incorrecto después de editar"

**Causa:** `Order.recalculateTotal()` no está siendo llamado
**Solución:**
```java
// Verificar que Order.addItem() y Order.removeItem() llamen recalculateTotal()
// Debería verse:
public void addItem(OrderItem item) {
    items.add(item);
    item.setOrder(this);
    recalculateTotal(); // ← DEBE estar aquí
}
```

### Problema: "Stock negativo no se refleja"

**Causa:** `processBonifiedItems()` sigue dividiendo en 2 filas
**Solución:**
```java
// Verificar que NO haya esta lógica vieja:
if (!hasStock && currentStock > 0) {
    // ❌ VIEJO: Crear inStockItem y outOfStockItem
}

// Debe haber solo:
int cantidadDescontada = Math.min(currentStock, requestedQuantity);
int cantidadPendiente = Math.max(0, requestedQuantity - currentStock);
item.setCantidadDescontada(cantidadDescontada);
item.setCantidadPendiente(cantidadPendiente);
```

---

## 📞 Rollback Plan

Si es necesario revertir los cambios:

### Opción 1: Rollback de Migraciones (Recomendado)
```bash
# 1. Parar aplicación
docker-compose down

# 2. Restaurar backup de base de datos
psql -U usuario -d vitalexa_db < backup_YYYYMMDD_HHMMSS.sql

# 3. Restaurar versión anterior del código
git checkout HEAD~1

# 4. Recompilar y reiniciar
./mvnw.cmd clean package
docker-compose up -d
```

### Opción 2: Mantener Cambios pero Desactivarlos
```java
// En Order.java - revertir a suma simple si algo falla
public void recalculateTotal() {
    // Temporalmente usar suma simple hasta diagnosticar
    this.total = items.stream()
            .map(OrderItem::getSubTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

---

## ✅ Checklist Final de Deploy

- [ ] Código compilado sin errores
- [ ] Tests pasados
- [ ] Migraciones V29 creada
- [ ] Backup de BD hecho
- [ ] Órdenes antiguas verificadas (backward compatible)
- [ ] Nuevas órdenes con promos tienen promotionInstanceId
- [ ] Totales respetan packPrice de promos
- [ ] Bonificados sin stock en una sola línea
- [ ] Logs limpios sin errores
- [ ] Frontend actualizado para usar promotionInstanceId
- [ ] Tests de smoke realizados
- [ ] Documentación actualizada

---

## 📈 Monitoreo Post-Deploy

### Métricas a Verificar

1. **Órdenes creadas con promociones duplicadas**
   - Esperar: Cada una tiene único `promotionInstanceId`
   - Alerta: Si faltan IDs únicos

2. **Totales de órdenes con múltiples promos**
   - Esperar: Total = suma de `promotionPackPrice` (no suma de items)
   - Alerta: Si total no coincide

3. **Bonificados con stock incompleto**
   - Esperar: Una sola línea con `cantidadDescontada` + `cantidadPendiente`
   - Alerta: Si aparecen múltiples líneas

4. **Performance de cálculo de totales**
   - Monitorear: Tiempo de `Order.recalculateTotal()`
   - Alerta: Si la lógica de Set<UUID> es lenta (no debería serlo)

---

## 📞 Contacto / Soporte

En caso de problemas post-deploy:

1. Revisar logs: `docker logs sistema_gestion_vitalexa`
2. Verificar migraciones: `SELECT * FROM flyway_schema_history`
3. Consultar este documento en sección Troubleshooting
4. Si es persistente: Usar Rollback Plan

---

**Versión:** 1.0
**Fecha:** 2025-02-13
**Estado:** Listo para Deploy


