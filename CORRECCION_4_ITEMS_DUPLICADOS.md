## CORRECCIÓN 4: Items Duplicados al Editar Orden de Promo

### PROBLEMA DETECTADO

**Síntoma**:
- Crear orden: Normal + S/R + Promo (3 órdenes separadas) ✅
- Editar la orden de Promo
- Agregar Flete
- Guardar cambios
- **Resultado**: TODOS los productos de la venta original aparecen en la orden de Promo
  - Pierde el precio de la promo
  - Se suma como orden normal
  - Orden se "multiplica"

**Ejemplo**:
```
VENTA ORIGINAL:
- 10 productos normales
- 5 productos S/R
- 1 Promoción (con regalos, precio: 50,000)

ÓRDENES CREADAS:
✅ Orden 1 (Standard): 10 productos normales
✅ Orden 2 (S/R): 5 productos S/R
✅ Orden 3 (Promo): Regalos (precio: 50,000)

EDITAR ORDEN 3 + AGREGAR FLETE:
❌ Orden 3 ahora tiene: 10 + 5 + regalos = mal
❌ Precio: suma de todos = mal
```

---

### ROOT CAUSE

En `updateOrder()`, el código estaba procesando `request.items()` **sin validar si la orden es de Promo**.

El frontend estaba enviando todos los items originales en el request, y el backend los agregaba sin verificar el tipo de orden.

```java
// ❌ INCORRECTO - Agregaba items a ANY orden
if (hasItems) {
    request.items().forEach(itemReq -> {
        // Agregar items sin verificar tipo de orden
    });
}
```

---

### ✅ SOLUCIÓN

Detectar el tipo de orden por el suffix en notas y **NO agregar items si es orden de Promo**:

```java
// ✅ CORRECTO - Detectar tipo de orden
String currentNotes = order.getNotas() != null ? order.getNotas() : "";
boolean isPromoOrder = currentNotes.contains("[Promoción]");
boolean isSROrder = currentNotes.contains("[S/R]");

// ✅ CORRECTO - No agregar items a orden de promo
if (hasItems && !isPromoOrder) {  // ← VALIDACIÓN CLAVE
    request.items().forEach(itemReq -> {
        // Agregar items solo si NO es orden de promo
    });
} else if (isPromoOrder && hasItems) {
    // Log: Evitar agregar items a orden de promo
    log.info("Edición de orden de promo: Se ignoran items (solo se preservan regalos)");
}
```

---

### FLUJO CORRECTO AHORA

```
EDITAR ORDEN DE PROMO + FLETE:

[1] Detectar tipo de orden por suffix
    ├─ "[Promoción]" → isPromoOrder = true
    ├─ "[S/R]" → isSROrder = true
    └─ "[Standard]" → normal = true

[2] Si es orden de Promo:
    ├─ Preservar items de promo (regalos)
    ├─ IGNORAR items enviados por frontend
    ├─ Aplicar flete
    └─ Guardar

[3] Si es orden Normal o S/R:
    ├─ Restaurar stock de items normales
    ├─ Agregar items nuevos del request
    ├─ Aplicar flete si aplica
    └─ Guardar
```

---

### CAMBIOS EN CÓDIGO

**Archivo**: `OrderServiceImpl.java` - método `updateOrder()`

**Antes**:
```java
// Limpiar items
order.clearItems();

// Re-agregar promos
for (OrderItem promoItem : promotionItems) {
    order.addItem(promoItem);
}

// ❌ AGREGAR ITEMS SIN VALIDACIÓN
if (hasItems) {
    request.items().forEach(itemReq -> {
        // Agregar todos los items
    });
}
```

**Después**:
```java
// Limpiar items
order.clearItems();

// Re-agregar promos
for (OrderItem promoItem : promotionItems) {
    order.addItem(promoItem);
}

// ✅ DETECTAR TIPO DE ORDEN
String currentNotes = order.getNotas() != null ? order.getNotas() : "";
boolean isPromoOrder = currentNotes.contains("[Promoción]");

// ✅ AGREGAR ITEMS SOLO SI NO ES PROMO
if (hasItems && !isPromoOrder) {
    request.items().forEach(itemReq -> {
        // Agregar items solo si NO es orden de promo
    });
} else if (isPromoOrder && hasItems) {
    log.info("Edición de orden de promo: Se ignoran items (solo se preservan regalos)");
}
```

---

### VALIDACIONES

✅ Orden de Promo mantiene SOLO sus regalos  
✅ No agrega items normales/S/R a orden Promo  
✅ Precio de Promo se preserva  
✅ Orden Normal/S/R siguen funcionando normal  
✅ Flete se aplica correctamente  

---

### TESTING

**Caso 1: Editar Promo + Agregar Flete**
```
1. Crear orden: Normal + S/R + Promo
2. Editar orden de Promo
3. Agregar Flete
4. Guardar

Verificar:
✅ Orden Promo tiene SOLO regalos
✅ NO tiene los 10 productos normales
✅ NO tiene los 5 productos S/R
✅ Precio sigue siendo 50,000
✅ Flete en total
```

**Caso 2: Editar Normal + Agregar Flete**
```
1. Crear orden: Normal + S/R + Promo
2. Editar orden Normal
3. Agregar Flete
4. Guardar

Verificar:
✅ Orden Normal tiene sus 10 productos
✅ NO tiene productos S/R
✅ Flete en total
```

---

### LOGS

Con esta corrección, verás en los logs:

```
[INFO] Edición de orden de promo {id}: Se ignoran items (solo se preservan regalos)
```

Si ves este log, significa que el código detectó correctamente que es orden de promo y evitó agregar items.


