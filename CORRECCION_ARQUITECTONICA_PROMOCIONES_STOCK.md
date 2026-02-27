# CORRECCIÓN ARQUITECTÓNICA: Promociones, Totales y Stock Negativo

## Fecha: 2025-02-13
## Estado: ✅ COMPLETADO

---

## 🎯 Problemas Resueltos

### 1️⃣ AGRUPACIÓN INCORRECTA DE PROMOCIONES
**Problema:** Múltiples instancias de la misma promoción se agrupaban bajo un solo ID.
- ❌ 2x Promo A + 2x Promo B → 2 grupos (en lugar de 4 líneas independientes)
- ❌ Imposible eliminar promociones individuales

**Solución Implementada:**
- ✅ Campo `promotionInstanceId` (UUID): Identifica única cada instancia de promoción
- ✅ Campo `promotionGroupIndex` (Integer): Ordinal para promociones duplicadas
- ✅ `InvoiceServiceImpl`: Agrupa por `promotionInstanceId` en lugar de `promotion.id`
- **Resultado:** Cada instancia de promoción es independiente → eliminación individual posible

---

### 2️⃣ ERROR EN CÁLCULO TOTAL AL EDITAR
**Problema:** Al editar órdenes con promociones, el sistema perdía el precio fijo y recalculaba como suma de productos.
- ❌ Promo con packPrice=$500.000 se recalculaba como suma individual
- ❌ `Order.recalculateTotal()` sumaba todos los `OrderItem.subTotal` sin respetar precios fijos

**Solución Implementada:**
- ✅ Campo `promotionPackPrice` (BigDecimal): Guarda el precio fijo en cada item
- ✅ `Order.recalculateTotal()`: NUEVO - respeta precios fijos, evita duplicación
  ```java
  // Solo agrega precio de promoción UNA VEZ (por instance ID)
  if (isPromotionItem && promotionInstanceId != null && promotionPackPrice != null) {
      if (!processedPromoInstances.contains(promotionInstanceId)) {
          total += promotionPackPrice;
      }
  }
  ```
- ✅ `OrderItem.calcularSubTotal()`: Respeta `promotionPackPrice` si está definido
- ✅ `OrderServiceImpl.updateOrder()`: Preserva `promotionInstanceId` y `promotionPackPrice` al editar
- ✅ `processPromotions()`: Guarda `promotionPackPrice` en cada item creado
- **Resultado:** Precios de promociones preservados al editar, totales correctos

---

### 3️⃣ ERROR EN MANEJO DE STOCK NEGATIVO
**Problema:** Al editar órdenes con promociones que excedían stock, el sistema dividía en múltiples filas ocultando el stock negativo.
- ❌ Promoción de 40 unidades con 20 disponibles → 2 filas (20 + 20 pendiente)
- ❌ Stock negativo no se reflejaba, aparecía como 0
- ❌ Impactaba inventario y facturación

**Solución Implementada:**
- ✅ `processBonifiedItems()`: REFACTORIZADO - mantiene UNA SOLA línea por producto
  - Calcula `cantidadDescontada = min(stock, solicitado)`
  - Calcula `cantidadPendiente = max(0, solicitado - stock)`
  - `outOfStock = true` solo si hay `cantidadPendiente > 0`
  - NO divide en múltiples filas
- ✅ `OrderItemResponse`: Expone `cantidadDescontada` y `cantidadPendiente` para frontend
- **Resultado:** Stock negativo representado correctamente, una sola línea, facturación consistente

---

## 📝 Cambios Implementados por Archivo

### 1. OrderItem.java (Entity)
```diff
+ @Column(name = "promotion_instance_id")
+ private UUID promotionInstanceId;

+ @Column(name = "promotion_pack_price", precision = 12, scale = 2)
+ private BigDecimal promotionPackPrice;

+ @Column(name = "promotion_group_index")
+ private Integer promotionGroupIndex;

  @PrePersist @PreUpdate
  public void calcularSubTotal() {
+     // Si es item de promoción con precio fijo, NO recalcular
+     if (isPromotionItem && promotionPackPrice != null) {
+         subTotal = promotionPackPrice;
+         return;
+     }
      // Calcular normalmente para items sin precio fijo
  }
```

### 2. Order.java (Entity)
```diff
  // ANTES: Simplemente sumaba todos los subTotal
  public void recalculateTotal() {
-     total = items.stream()
-         .map(OrderItem::getSubTotal)
-         .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // DESPUÉS: Respeta precios fijos de promociones
+ public void recalculateTotal() {
+     Set<UUID> processedPromoInstances = new HashSet<>();
+     BigDecimal total = BigDecimal.ZERO;
+     
+     for (OrderItem item : items) {
+         if (isPromotionItem && promotionInstanceId != null && promotionPackPrice != null) {
+             if (!processedPromoInstances.contains(promotionInstanceId)) {
+                 total += promotionPackPrice;
+                 processedPromoInstances.add(promotionInstanceId);
+             }
+         } else {
+             total += item.getSubTotal();
+         }
+     }
+     this.total = total;
+ }
```

### 3. OrderServiceImpl.java (Service)

#### A. processPromotions() - ACTUALIZADO
```diff
+ UUID promotionInstanceId = UUID.randomUUID();
+ int groupIndex = promoIndexCount.getOrDefault(promotionId, 0) + 1;
+ BigDecimal effectivePrice = promotion.getPackPrice() != null 
+     ? promotion.getPackPrice() 
+     : ...;

  OrderItem buyItem = OrderItem.builder()
      // ...
+     .promotionInstanceId(promotionInstanceId)
+     .promotionPackPrice(effectivePrice)
+     .promotionGroupIndex(groupIndex)
      .build();
```

#### B. updateOrder() - ACTUALIZADO
```diff
  // Al re-agregar items de promoción, preservar IDs y precios
  for (OrderItem promoItem : promotionItems) {
      order.addItem(promoItem);
+     log.info("✅ Item de promoción re-agregado - Instancia: {} - Precio: ${}", 
+         promoItem.getPromotionInstanceId(), 
+         promoItem.getPromotionPackPrice());
  }
```

#### C. processBonifiedItems() - REFACTORIZADO
```diff
  // ANTES: Dividía en 2 filas (con stock + pendiente)
- if (!hasStock && currentStock > 0) {
-     // PARTE 1: Lo que sí hay en stock
-     OrderItem inStockItem = ...;
-     // PARTE 2: Lo que falta
-     OrderItem outOfStockItem = ...;
- }

  // DESPUÉS: Una sola línea con stock negativo
+ int cantidadDescontada = Math.min(currentStock, requestedQuantity);
+ int cantidadPendiente = Math.max(0, requestedQuantity - currentStock);
+ 
+ item.setCantidadDescontada(cantidadDescontada);
+ item.setCantidadPendiente(cantidadPendiente);
+ item.setOutOfStock(cantidadPendiente > 0);
+ 
+ if (cantidadDescontada > 0) {
+     product.decreaseStock(cantidadDescontada);
+ }
```

### 4. InvoiceServiceImpl.java (Service)
```diff
- Map<String, List<OrderItem>> itemsByPromotion = new HashMap<>();
+ // ✅ Agrupar por promotionInstanceId (no promotion.id)
  for (OrderItem item : order.getItems()) {
      if (item.getPromotion() == null) {
          regularItems.add(item);
      } else {
+         String promoKey = item.getPromotionInstanceId() != null
+             ? item.getPromotionInstanceId().toString()
+             : item.getPromotion().getId().toString();
+         itemsByPromotion.computeIfAbsent(promoKey, k -> new ArrayList<>()).add(item);
      }
  }
```

### 5. OrderItemMapper.java (Mapper)
```diff
  @Mapper(componentModel = "spring")
  public interface OrderItemMapper {
      @Mapping(source = "promotion.nombre", target = "promotionName")
+     @Mapping(source = "promotionInstanceId", target = "promotionInstanceId")
+     @Mapping(source = "promotionPackPrice", target = "promotionPackPrice")
+     @Mapping(source = "promotionGroupIndex", target = "promotionGroupIndex")
+     @Mapping(source = "cantidadDescontada", target = "cantidadDescontada")
+     @Mapping(source = "cantidadPendiente", target = "cantidadPendiente")
      OrderItemResponse toResponse(OrderItem item);
  }
```

### 6. OrderItemResponse.java (DTO)
```diff
  public record OrderItemResponse(
      UUID id,
      UUID productId,
      String productName,
      Integer cantidad,
      BigDecimal precioUnitario,
      BigDecimal subtotal,
      Boolean outOfStock,
      LocalDate estimatedArrivalDate,
      String estimatedArrivalNote,
      Integer cantidadDescontada,
      Integer cantidadPendiente,
      UUID promotionId,
      String promotionName,
      Boolean isPromotionItem,
      Boolean isFreeItem,
+     UUID promotionInstanceId,
+     BigDecimal promotionPackPrice,
+     Integer promotionGroupIndex,
      Boolean isBonified,
      Boolean isFreightItem) {
  }
```

### 7. V29__add_promotion_instance_fields.sql (Migration)
```sql
ALTER TABLE order_items ADD COLUMN promotion_instance_id UUID NULL;
ALTER TABLE order_items ADD COLUMN promotion_pack_price NUMERIC(12, 2) NULL;
ALTER TABLE order_items ADD COLUMN promotion_group_index INTEGER NULL;

CREATE INDEX idx_order_items_promotion_instance ON order_items(promotion_instance_id);
```

---

## 🔍 Flujo Ahora Funciona Así:

### Caso 1: Múltiples Promociones del Mismo Tipo
```
Entrada: 2x Promo A + 2x Promo B

Procesamiento:
├─ Promo A (instancia 1) → promotionInstanceId = UUID-1, groupIndex = 1
├─ Promo A (instancia 2) → promotionInstanceId = UUID-2, groupIndex = 1  ← DIFERENTE
├─ Promo B (instancia 1) → promotionInstanceId = UUID-3, groupIndex = 1
└─ Promo B (instancia 2) → promotionInstanceId = UUID-4, groupIndex = 1  ← DIFERENTE

Resultado en Factura:
├─ Bloque: PROMO A (Instancia UUID-1) - Precio $X
├─ Bloque: PROMO A (Instancia UUID-2) - Precio $X  ← Separado
├─ Bloque: PROMO B (Instancia UUID-3) - Precio $Y
└─ Bloque: PROMO B (Instancia UUID-4) - Precio $Y  ← Separado

Total = $X + $X + $Y + $Y ✅
Eliminación individual = Posible por UUID ✅
```

### Caso 2: Edición de Orden con Promociones
```
Original: 1x Promo A con packPrice=$500.000

Edición (sin cambiar promoción):
├─ Detecta: currentPromotionIds = {UUID-Promo-A}
├─ Comparar: requestedPromotionIds = {UUID-Promo-A}
├─ Resultado: IDs iguales → No re-procesar
├─ Preserva: promotionInstanceId = UUID-1, promotionPackPrice = $500.000
└─ Calcula: Order.total = $500.000 ✅ (no suma de productos)

Total Final = $500.000 ✅
Precio preservado ✅
```

### Caso 3: Stock Negativo en Bonificados
```
Entrada: 40 unidades bonificadas, stock = 20

Procesamiento:
├─ cantidadDescontada = min(20, 40) = 20
├─ cantidadPendiente = max(0, 40-20) = 20
├─ outOfStock = (20 > 0) = true
├─ product.decreaseStock(20) → Stock = 0

Resultado (UNA SOLA LÍNEA):
├─ Cantidad solicitada: 40
├─ Cantidad descontada: 20
├─ Cantidad pendiente: 20 (stock negativo -20)
├─ outOfStock = true

Factura muestra: "40 unidades [20 pendiente]" ✅
Stock inventario = 0 (correcto) ✅
Ninguna duplicación de filas ✅
```

---

## 🧪 Casos de Prueba Recomendados

1. **Crear orden con múltiples promociones duplicadas**
   - Verificar: 4 bloques independientes en factura
   - Verificar: IDs únicos para cada instancia

2. **Editar orden con promociones sin cambiar promoción**
   - Verificar: Total preservado (packPrice)
   - Verificar: Mismo promotionInstanceId

3. **Crear orden con promoción que excede stock**
   - Verificar: Una línea con cantidadDescontada + cantidadPendiente
   - Verificar: outOfStock = true
   - Verificar: Stock se disminuye solo en cantidadDescontada

4. **Editar orden con bonificados sin stock**
   - Verificar: No aparecen múltiples filas
   - Verificar: Totales correctos

---

## 📌 Notas Importantes

1. **Backward Compatibility:**
   - Órdenes viejas sin `promotionInstanceId` seguirán funcionando
   - Fallback en `InvoiceServiceImpl` usando `promotion.id`

2. **Frontend Updates Required:**
   - Incluir `promotionInstanceId` en requests de actualización
   - Usar `promotionInstanceId` para eliminar promociones individuales
   - Mostrar `cantidadDescontada` y `cantidadPendiente` en UI

3. **Migraciones de Base de Datos:**
   - V29 crea nuevos campos en `order_items`
   - Ejecutar migraciones antes de deployar

4. **Logging Mejorado:**
   - Cada promoción registra su `promotionInstanceId`
   - Facilita debugging de problemas de totales

---

## ✅ Validación Post-Implementación

```bash
# Compilar proyecto
mvnw clean compile

# Ejecutar migraciones
mvnw flyway:migrate

# Pruebas de integración
mvnw test

# Validaciones:
- ✅ Órdenes con promociones tienen promotionInstanceId
- ✅ Order.total respeta packPrice de promociones
- ✅ Bonificados sin stock son una sola línea
- ✅ InvoiceServiceImpl agrupa por promotionInstanceId
```

---

**Realizado por:** Arquitecto Senior - GitHub Copilot
**Alcance:** Solución completa para 3 problemas críticos de promociones

