# FIX: Descuento de Stock en Promociones

## 🐛 Problema Reportado

Cuando se vende una promoción, **los productos que la componen NO se descuentan del stock**.

### Ejemplo:
```
Promoción "Combo Pack": 
  - Producto A: 40 unidades
  - Producto B: 10 unidades (regalo)

Al vender la promoción:
❌ Stock de A sigue siendo 100
❌ Stock de B sigue siendo 50
```

---

## 🔧 Causa

En el método `processPromotions()` había dos problemas:

### CASO 1: Promociones Surtidas
```java
// VIEJO: Solo descuenta SI hay stock disponible
if (freeProduct.getStock() < qty) {
    placeholderItem.setOutOfStock(true);  // Marca como sin stock
} else {
    freeProduct.decreaseStock(qty);       // Descuenta solo si hay
}
```
**Resultado:** Si no había stock completo, NO descuentaba nada.

### CASO 2: Promociones Predefinidas  
```java
// VIEJO: Solo descuenta mainProduct, NO descuenta giftItems
if (mainProduct.getStock() < promotion.getBuyQuantity()) {
    buyItem.setOutOfStock(true);
} else {
    mainProduct.decreaseStock(promotion.getBuyQuantity());
}

// NO había descuento para los items de regalo!
```
**Resultado:** Los productos de regalo NUNCA se descontaban.

---

## ✅ Solución Implementada

### CASO 1: Promociones Surtidas
```java
// ✅ NUEVO: Siempre descontar (permitir stock negativo)
freeProduct.decreaseStock(qty);

if (freeProduct.getStock() < 0) {
    placeholderItem.setOutOfStock(true);
    log.warn("Stock NEGATIVO para regalo surtido");
}
```

### CASO 2: Promociones Predefinidas

**Descuento del producto principal:**
```java
// ✅ SIEMPRE descontar (permitir stock negativo)
mainProduct.decreaseStock(promotion.getBuyQuantity());

if (mainProduct.getStock() < 0) {
    buyItem.setOutOfStock(true);
}
```

**Descuento de TODOS los productos en giftItems:**
```java
// ✅ NUEVO: Agregar descuento de cada regalo
if (promotion.getGiftItems() != null) {
    for (PromotionGiftItem gift : promotion.getGiftItems()) {
        Product freeProduct = gift.getProduct();
        Integer qty = gift.getQuantity();
        
        // ✅ DESCUENTAR SIEMPRE
        freeProduct.decreaseStock(qty);
        
        if (freeProduct.getStock() < 0) {
            freeItem.setOutOfStock(true);
        }
        
        order.addItem(freeItem);
    }
}
```

---

## 📊 Ejemplo Corregido

```
Promoción "Combo Pack": 
  - mainProduct (Producto A): 40 unidades
  - giftItem 1 (Producto B): 10 unidades
  - giftItem 2 (Producto C): 5 unidades

Stock Inicial:
  A: 100 | B: 50 | C: 30

Al vender la promoción:

Stock Después:
  A: 60   (100 - 40) ✅
  B: 40   (50 - 10)  ✅
  C: 25   (30 - 5)   ✅

Si había stock insuficiente:
  A: 5 stock iniciales, se solicitan 40
  → A: -35 (stock negativo permitido) ✅
  → outOfStock = true ⚠️
```

---

## 🎯 Cambios Realizados

### Archivo: `OrderServiceImpl.java`

#### 1. CASO 1: Promociones Surtidas (línea ~774)
```diff
- if (freeProduct.getStock() < qty) {
-     placeholderItem.setOutOfStock(true);
- } else {
-     freeProduct.decreaseStock(qty);
- }

+ // ✅ DESCUENTO DE STOCK: Permitir stock negativo
+ freeProduct.decreaseStock(qty);
+ 
+ if (freeProduct.getStock() < 0) {
+     placeholderItem.setOutOfStock(true);
+ }
```

#### 2. CASO 2: Promociones Predefinidas (línea ~816-870)

**2A. Descuento de producto principal:**
```diff
- if (mainProduct.getStock() < promotion.getBuyQuantity()) {
-     buyItem.setOutOfStock(true);
- } else {
-     mainProduct.decreaseStock(promotion.getBuyQuantity());
- }

+ // ✅ DESCUENTO DE STOCK: Permitir stock negativo
+ mainProduct.decreaseStock(promotion.getBuyQuantity());
+
+ if (mainProduct.getStock() < 0) {
+     buyItem.setOutOfStock(true);
+ }
```

**2B. Descuento de regalos (NUEVO):**
```diff
  // ✅ CRÍTICO: Descontar stock de todos los productos en giftItems
+ if (promotion.getGiftItems() != null) {
+     for (PromotionGiftItem gift : promotion.getGiftItems()) {
+         // ✅ DESCUENTO DE STOCK: Permitir stock negativo
+         freeProduct.decreaseStock(qty);
+         
+         if (freeProduct.getStock() < 0) {
+             freeItem.setOutOfStock(true);
+         }
+     }
+ }
```

---

## ✨ Mejoras Incluidas

1. ✅ **Descuento de todos los productos** (mainProduct + giftItems)
2. ✅ **Permite stock negativo** (como solicitado)
3. ✅ **Flags outOfStock correctos** (se marcan cuando hay stock negativo)
4. ✅ **Logging mejorado** (muestra qué se descuenta y el stock resultante)
5. ✅ **Manejo consistente** (mismo comportamiento en CASO 1 y CASO 2)

---

## 🧪 Validaciones

### Test Manual:

**1. Crear promoción con productos:**
- Producto A: 40 unidades (mainProduct)
- Producto B: 10 unidades (regalo)

**2. Crear orden con esa promoción**

**3. Verificar en BD:**
```sql
SELECT nombre, stock FROM products WHERE id IN ('A-uuid', 'B-uuid');

-- ANTES: A=100, B=50
-- DESPUÉS: A=60, B=40 ✅
```

**4. Si stock era insuficiente (A tenía 20):**
```sql
-- DESPUÉS: A=-20, B=40 ✅ (stock negativo permitido)
```

---

## 📝 Casos Cubiertos

| Escenario | Antes | Después |
|-----------|-------|---------|
| Promo surtida con stock | Stock = 50 | Stock = 40 ✅ |
| Promo surtida sin stock | Stock = 5 | Stock = -5 ✅ |
| Promo predefinida mainProduct | Stock = 50 | Stock = 10 ✅ |
| Promo predefinida giftItems | Stock = 50 | **Stock = 40 ✅** |
| Stock negativo en promo | Stock = 5 | Stock = -35 ✅ |

---

**Status:** ✅ **FIX IMPLEMENTADO**  
**Fecha:** 2026-02-13  
**Listo para probar** 🚀


