# 🧪 GUÍA DE PRUEBAS - Todos los Fixes

## Pre-requisitos

✅ Base de datos limpia O con datos válidos
✅ Aplicación compilada sin errores
✅ Postman/Insomnia para hacer requests

---

## 📝 Test 1: Descuento de Stock en Promociones

### Crear Promoción
```
POST /api/admin/promotions

{
  "nombre": "Combo A+B",
  "mainProductId": "UUID-A",
  "buyQuantity": 100,
  "packPrice": 150000,
  "giftItems": [
    {
      "productId": "UUID-A",  // ← MISMO producto
      "quantity": 20
    }
  ],
  "active": true
}
```

### Verificar Stock Inicial
```
Producto A: Stock = 1000

GET /api/admin/products/UUID-A
// Verificar: "stock": 1000
```

### Crear Orden con Promo
```
POST /api/admin/orders

{
  "clientId": "client-uuid",
  "promotionIds": ["promo-uuid"],
  "notas": "Test descuento"
}
```

### Verificar Stock Descontado
```
GET /api/admin/products/UUID-A
// Esperado: "stock": 880  (1000 - 100 - 20)
// ✅ Si es 880: CORRECTO
// ❌ Si es 1000: ERROR (no descuenta)
```

---

## 📝 Test 2: Múltiples Instancias de Misma Promo

### Crear Orden con 2x Misma Promo
```
POST /api/admin/orders

{
  "clientId": "client-uuid",
  "promotionIds": ["promo-uuid", "promo-uuid"],  // ← MISMA 2 VECES
  "notas": "Test 2 instancias"
}
```

### Verificar Stock Doblemente Descontado
```
GET /api/admin/products/UUID-A
// Esperado: "stock": 760  (1000 - 240)
// ✅ Si es 760: CORRECTO
// ❌ Si es 880: ERROR (solo descuenta 1)
```

### Obtener ID de la Orden Creada
```
GET /api/admin/orders

// Copiar el "id" de la orden creada
// Ejemplo: "id": "order-uuid-123"
```

---

## 📝 Test 3: Eliminar Una Promoción

### Obtener ID del Primer Item
```
GET /api/admin/orders/order-uuid-123

{
  "items": [
    {
      "id": "item-uuid-1",      // ← COPIAR ESTE ID
      "isPromotionItem": true,
      "isFreeItem": false,
      "product": { "nombre": "Producto A", ... }
    },
    {
      "id": "item-uuid-2",
      "isPromotionItem": true,
      "isFreeItem": true,
      "product": { "nombre": "Producto A", ... }
    },
    // Más items...
  ]
}
```

### Eliminar el Primer Item (mainProduct)
```
DELETE /api/admin/orders/order-uuid-123/items/item-uuid-1

// Esperado: 200 OK
// Response: OrderResponse actualizada
```

### Verificar Stock Parcialmente Restaurado
```
GET /api/admin/products/UUID-A
// Esperado: "stock": 880  (760 + 120)
// ✅ Si es 880: CORRECTO (restauró 1 promo completa)
// ❌ Si es 800: ERROR (solo restauró mainProduct, perdió regalo)
// ❌ Si es 760: ERROR (no restauró nada)
```

---

## 📝 Test 4: Eliminar Segunda Promoción

### Eliminar el Tercer Item (mainProduct de 2da promo)
```
DELETE /api/admin/orders/order-uuid-123/items/item-uuid-3

// Esperado: 200 OK
```

### Verificar Stock Completamente Restaurado
```
GET /api/admin/products/UUID-A
// Esperado: "stock": 1000  (880 + 120)
// ✅ Si es 1000: CORRECTO (orden limpia)
// ❌ Si es 880: ERROR (no restauró la 2da promo)
```

### Verificar Orden sin Promociones
```
GET /api/admin/orders/order-uuid-123

{
  "items": [
    // ✅ DEBE ESTAR VACÍO (todos los items de promo fueron eliminados)
    // ❌ Si quedan items: ERROR
  ]
}
```

---

## 📝 Test 5: Anular Orden Completa

### Crear Nueva Orden con Promo
```
POST /api/admin/orders

{
  "clientId": "client-uuid",
  "promotionIds": ["promo-uuid"],
  "notas": "Test anular"
}
```

### Copiar Stock Inicial
```
GET /api/admin/products/UUID-A
// Copiar el stock actual, ej: 1000
```

### Crear Orden (descuenta stock)
```
Stock: 1000 - 120 = 880
```

### Anular la Orden
```
POST /api/admin/orders/order-uuid-456/annul

{
  "reason": "Test anulación"
}
```

### Verificar Stock Restaurado
```
GET /api/admin/products/UUID-A
// Esperado: "stock": 1000  (880 + 120)
// ✅ Si es 1000: CORRECTO
// ❌ Si es 880: ERROR (no restauró)
// ❌ Si es 1120: ERROR (duplicó negativo)
```

---

## 📝 Test 6: Anular con Stock Negativo

### Stock Inicial Bajo
```
Producto A: Stock = 50
```

### Crear Orden con Promo (descuento 120)
```
Stock: 50 - 120 = -70
```

### Anular Orden
```
POST /api/admin/orders/order-uuid-789/annul

{
  "reason": "Test negativo"
}
```

### Verificar Stock Restaurado
```
GET /api/admin/products/UUID-A
// Esperado: "stock": 50  (-70 + 120)
// ✅ Si es 50: CORRECTO (negativo manejado correctamente)
// ❌ Si es -70: ERROR (no restauró)
// ❌ Si es -190: ERROR (duplicó negativo)
```

---

## 📝 Test 7: Promo con Múltiples Regalos Diferentes

### Crear Promo Compleja
```
POST /api/admin/promotions

{
  "nombre": "Combo Deluxe",
  "mainProductId": "UUID-A",
  "buyQuantity": 100,
  "packPrice": 200000,
  "giftItems": [
    { "productId": "UUID-B", "quantity": 30 },
    { "productId": "UUID-C", "quantity": 15 }
  ],
  "active": true
}
```

### Stock Inicial
```
A: 1000, B: 500, C: 300
```

### Crear Orden
```
POST /api/admin/orders
{
  "promotionIds": ["promo-deluxe"]
}
```

### Verificar Todos los Stocks
```
GET /api/admin/products/UUID-A
GET /api/admin/products/UUID-B
GET /api/admin/products/UUID-C

// Esperado:
// A: 900  (1000 - 100)
// B: 470  (500 - 30)
// C: 285  (300 - 15)

// ✅ Si los 3 son correctos: EXCELENTE
// ❌ Si alguno falla: ERROR
```

### Anular Orden
```
POST /api/admin/orders/order-uuid-999/annul
```

### Verificar Restauración Completa
```
// Esperado:
// A: 1000 (900 + 100)
// B: 500  (470 + 30)
// C: 300  (285 + 15)

// ✅ Si los 3 vuelven al inicial: PERFECTO
```

---

## ✅ Checklist de Validación

```
[ ] Test 1: Descuento inicial correcto
[ ] Test 2: Múltiples instancias descuentan correctamente
[ ] Test 3: Eliminar 1 promo restaura exactamente
[ ] Test 4: Eliminar 2ª promo completa restauración
[ ] Test 5: Anular orden restaura todo
[ ] Test 6: Anular con stock negativo funciona
[ ] Test 7: Múltiples regalos sincronizados

RESULTADO FINAL:
[ ] ✅ TODOS LOS TESTS PASARON - Sistema funcionando perfectamente
[ ] ❌ Algún test falló - Revisar documentación específica
```

---

## 🔍 Si Algo Falla

### 1. Verificar Logs
```
Level: INFO
Buscar: "Stock restaurado" o "Stock descontado"

Ejemplo:
✅ Stock descontado para producto principal 'Producto A': -100
✅ Stock descontado para regalo 'Producto A': -20
```

### 2. Verificar BD Directamente
```sql
SELECT nombre, stock FROM products 
WHERE nombre IN ('Producto A', 'Producto B');
```

### 3. Revisar Movimientos
```sql
SELECT * FROM inventory_movements 
WHERE product_id = 'UUID-A'
ORDER BY timestamp DESC
LIMIT 10;
```

### 4. Verificar Items de Orden
```sql
SELECT id, producto_id, cantidad, is_promotion_item, is_free_item
FROM order_items
WHERE order_id = 'order-uuid'
ORDER BY created_at;
```

---

**¡LISTO PARA PROBAR!** 🚀


