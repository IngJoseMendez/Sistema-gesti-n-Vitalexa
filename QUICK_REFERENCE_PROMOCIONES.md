# QUICK REFERENCE: Promociones Independientes y Stock Negativo

## 🚀 Inicio Rápido

### ¿Cuál es el problema que se solucionó?

| Problema | Síntoma | Solución |
|----------|---------|----------|
| Promos duplicadas se agrupan | 2 Promo A = 1 grupo | UUID único por instancia |
| Totales incorrectos | Precio fijo se pierde al editar | `promotionPackPrice` guardado en OrderItem |
| Stock negativo fragmentado | 40 unidades = 2 filas | Una línea con `cantidadDescontada`+`cantidadPendiente` |

---

## 📝 Cambios Clave

### 1. OrderItem.java
```java
// ✅ NUEVOS CAMPOS
@Column(name = "promotion_instance_id")
private UUID promotionInstanceId;  // ID único por instancia

@Column(name = "promotion_pack_price")
private BigDecimal promotionPackPrice;  // Precio fijo

@Column(name = "promotion_group_index")
private Integer promotionGroupIndex;  // Ordinal (1, 2, 3...)
```

### 2. Order.java
```java
// ✅ REFACTORIZADO
public void recalculateTotal() {
    Set<UUID> processedPromoInstances = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    
    for (OrderItem item : items) {
        // Si es promo, contar UNA VEZ por instance ID
        if (item.isPromotionItem && item.promotionInstanceId != null) {
            if (!processedPromoInstances.contains(item.promotionInstanceId)) {
                total = total.add(item.promotionPackPrice);
                processedPromoInstances.add(item.promotionInstanceId);
            }
        } else {
            total = total.add(item.getSubTotal());
        }
    }
    this.total = total;
}
```

### 3. OrderServiceImpl.processPromotions()
```java
// ✅ Generar UUID único para cada instancia
UUID promotionInstanceId = UUID.randomUUID();
int groupIndex = promoIndexCount.getOrDefault(promotionId, 0) + 1;

// ✅ Guardar precio fijo
BigDecimal effectivePrice = promotion.getPackPrice();

// ✅ Crear OrderItem con IDs únicos
OrderItem item = OrderItem.builder()
    .promotionInstanceId(promotionInstanceId)
    .promotionPackPrice(effectivePrice)
    .promotionGroupIndex(groupIndex)
    .build();
```

### 4. OrderServiceImpl.processBonifiedItems()
```java
// ✅ Una sola línea (NO dividir en 2)
int cantidadDescontada = Math.min(currentStock, requestedQuantity);
int cantidadPendiente = Math.max(0, requestedQuantity - currentStock);

item.setCantidadDescontada(cantidadDescontada);
item.setCantidadPendiente(cantidadPendiente);
item.setOutOfStock(cantidadPendiente > 0);

if (cantidadDescontada > 0) {
    product.decreaseStock(cantidadDescontada);
}
```

### 5. InvoiceServiceImpl
```java
// ✅ Agrupa por promotionInstanceId (no promotion.id)
String promoKey = item.getPromotionInstanceId() != null
    ? item.getPromotionInstanceId().toString()
    : item.getPromotion().getId().toString();
itemsByPromotion.computeIfAbsent(promoKey, k -> new ArrayList<>()).add(item);
```

---

## 🧪 Casos de Prueba Rápidos

### Test 1: Múltiples Promos del Mismo Tipo
```bash
POST /api/orders
{
  "promotionIds": ["uuid-promo-a", "uuid-promo-a", "uuid-promo-b"]
}

# Validar:
# ✅ items tienen promotionInstanceId diferente
# ✅ items tienen groupIndex (1, 1, 1)
# ✅ total = packPrice-A + packPrice-A + packPrice-B
```

### Test 2: Editar Orden sin Cambiar Promos
```bash
PUT /api/orders/{id}
{
  "promotionIds": ["uuid-promo-a", "uuid-promo-a"],
  "items": [{"productId": "...", "cantidad": 5}]
}

# Validar:
# ✅ promotionInstanceId se PRESERVA (mismo UUID que antes)
# ✅ promotionPackPrice se PRESERVA
# ✅ total correcto
```

### Test 3: Bonificado sin Stock
```bash
POST /api/orders
{
  "bonifiedItems": [{"productId": "prod-x", "cantidad": 40}]
}
# Stock disponible: 20

# Validar:
# ✅ 1 solo OrderItem (NO 2)
# ✅ cantidadDescontada = 20
# ✅ cantidadPendiente = 20
# ✅ outOfStock = true
```

---

## 🔍 Debugging

### ¿Dónde se genera promotionInstanceId?
```
OrderServiceImpl.processPromotions() línea ~750
├─ Genera UUID.randomUUID() para cada promo
└─ Se guarda en cada OrderItem creado
```

### ¿Dónde se preserva al editar?
```
OrderServiceImpl.updateOrder() línea ~1050
├─ Extrae items de promoción ANTES de limpiar
├─ Los re-agrega preservando promotionInstanceId
└─ Order.recalculateTotal() respeta precios
```

### ¿Dónde se agrupa en factura?
```
InvoiceServiceImpl.generateOrderInvoicePdf() línea ~217
├─ Agrupa por promotionInstanceId (nueva lógica)
└─ Crea bloque separado para cada instancia
```

### ¿Dónde se valida stock negativo?
```
OrderServiceImpl.processBonifiedItems() línea ~1400
├─ Calcula cantidadDescontada = min(stock, cantidad)
├─ Calcula cantidadPendiente = max(0, cantidad - stock)
└─ Crea 1 solo item (NO divide en 2)
```

---

## 📊 Impacto en Base de Datos

### Nueva Migración V29
```sql
-- Tres columnas nuevas en order_items
promotion_instance_id UUID          -- Nuevo, NULL en órdenes viejas
promotion_pack_price NUMERIC(12,2)  -- Nuevo, NULL en órdenes viejas
promotion_group_index INTEGER       -- Nuevo, NULL en órdenes viejas

-- Índice para búsquedas rápidas
idx_order_items_promotion_instance
```

### Backward Compatibility ✅
- Órdenes antiguas sin `promotionInstanceId` siguen funcionando
- Fallback: `InvoiceServiceImpl` usa `promotion.id` si `promotionInstanceId` es NULL
- No hay breaking changes

---

## 🎯 Checklist de Implementación

### Backend
- [x] OrderItem.java - Agregados 3 campos
- [x] Order.java - Refactorizado recalculateTotal()
- [x] OrderServiceImpl.java - Actualizado processPromotions()
- [x] OrderServiceImpl.java - Actualizado updateOrder()
- [x] OrderServiceImpl.java - Refactorizado processBonifiedItems()
- [x] InvoiceServiceImpl.java - Actualizado grouping
- [x] OrderItemMapper.java - Agregados mappings
- [x] OrderItemResponse.java - Agregados campos
- [x] V29 Migration - Creada

### Frontend (Pendiente)
- [ ] Actualizar tipos TypeScript con nuevos campos
- [ ] Cambiar agrupación de `promotionId` → `promotionInstanceId`
- [ ] Mostrar una sola línea para bonificados con stock negativo
- [ ] Actualizar componentes de edición de orden

### Testing
- [ ] Compilar `mvnw clean compile`
- [ ] Tests `mvnw test`
- [ ] Validar backward compatibility
- [ ] Pruebas manuales de cada caso

---

## ⚡ Performance

| Operación | Complejidad | Notas |
|-----------|-------------|-------|
| Generar promotionInstanceId | O(1) | UUID.randomUUID() es muy rápido |
| recalculateTotal() | O(n) | Set<UUID> lookup es O(1) |
| Agrupar por promotionInstanceId | O(n) | Una pasada sobre items |
| processBonifiedItems() | O(m) | m = número de bonificados |

**Conclusión:** ✅ Sin impacto significativo en performance

---

## 🚨 Errores Comunes

### ❌ "NullPointerException en recalculateTotal()"
```java
// Verificar que promotionInstanceId y promotionPackPrice NO sean null
if (Boolean.TRUE.equals(item.getIsPromotionItem()) &&
    item.getPromotionInstanceId() != null &&  // ← VALIDAR
    item.getPromotionPackPrice() != null) {   // ← VALIDAR
    ...
}
```

### ❌ "Promociones se agrupan incorrectamente"
```java
// Verificar que InvoiceServiceImpl use promotionInstanceId
String promoKey = item.getPromotionInstanceId() != null
    ? item.getPromotionInstanceId().toString()  // ← USAR ESTE
    : item.getPromotion().getId().toString();  // ← fallback
```

### ❌ "Bonificados aparecen en 2 filas"
```java
// Verificar que NO haya este código viejo:
if (!hasStock && currentStock > 0) {
    // ❌ VIEJO: Crear 2 items (inStock + outOfStock)
}
// ✅ DEBE SER: Crear 1 item con cantidadDescontada + cantidadPendiente
```

### ❌ "Total incorrecto después de editar"
```java
// Verificar que updateOrder() llame a recalculateTotal()
Order updatedOrder = ordenRepository.save(order);
// order.recalculateTotal() debería haberse llamado en addItem()
```

---

## 📞 ¿Preguntas Frecuentes?

**P: ¿Se puede eliminar una sola promoción de una orden con múltiples?**
A: Sí, usando el endpoint DELETE /api/orders/{id}/items/{itemId} donde itemId es el OrderItem con promotionInstanceId específico.

**P: ¿Se preservan los precios fijos al editar?**
A: Sí, porque promotionInstanceId y promotionPackPrice se preservan y no se re-procesan si los IDs de promoción no cambian.

**P: ¿Qué pasa con órdenes viejas sin promotionInstanceId?**
A: Siguen funcionando. El sistema usa promotion.id como fallback. Al re-procesar, se generan promotionInstanceId nuevos.

**P: ¿Por qué una sola línea para bonificados?**
A: Para evitar fragmentación de líneas y permitir auditoría correcta del stock. cantidadDescontada + cantidadPendiente expresan correctamente el déficit.

**P: ¿Impacta en facturación?**
A: Mejora. Ahora cada instancia de promo tiene su propio bloque, y bonificados sin stock se muestran correctamente en una línea.

---

## 📚 Referencias

- `CORRECCION_ARQUITECTONICA_PROMOCIONES_STOCK.md` - Detalle técnico
- `GUIA_FRONTEND_PROMOCIONES_INDEPENDIENTES.md` - Cambios en API
- `GUIA_DEPLOY_PROMOCIONES.md` - Instrucciones de deploy
- `RESUMEN_CORRECCION_PROMOCIONES.md` - Resumen ejecutivo

---

**Última actualización:** 2025-02-13
**Versión:** 1.0
**Estado:** ✅ Listo para implementar


