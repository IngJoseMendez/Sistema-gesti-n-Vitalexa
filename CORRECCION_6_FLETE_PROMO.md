## ✅ CORRECCIÓN 6: Flete Personalizado en Órdenes de Promoción

### PROBLEMA ESPECÍFICO

**Síntoma**:
- En órdenes de NORMAL/S/R: ✅ Permite agregar productos específicos para flete personalizado
- En órdenes de PROMOCIÓN: ❌ NO permite agregar productos específicos (solo genérico)

**Causa**:
En `updateOrder()`, cuando detectaba que era orden de Promo (`isPromoOrder=true`), el código bloqueaba el procesamiento de TODOS los items, incluyendo items de flete personalizado.

**Lógica Incorrecta**:
```java
// ❌ INCORRECTO - Bloqueaba TODO incluyendo flete
if (hasItems && !isPromoOrder) {
    // Procesa items... pero solo si NO es promo
}
```

---

### ✅ SOLUCIÓN

**Lógica Correcta**:
```java
// ✅ CORRECTO - Distingue entre items normales e items de flete
if (hasItems) {
    request.items().forEach(itemReq -> {
        // ✅ PERMITIR items de flete incluso en orden de Promo
        if (isPromoOrder && !Boolean.TRUE.equals(itemReq.isFreightItem())) {
            // ❌ BLOQUEAR items normales en orden de Promo
            log.debug("Item normal ignorado en edición de orden promo");
            return;
        }
        
        // Procesar item (sea flete o normal)
        // ... rest of processing
    });
}
```

---

### CAMBIOS REALIZADOS

**Archivo**: `OrderServiceImpl.java` - método `updateOrder()` (línea ~787)

**Lógica nueva**:
1. Si es orden de Promo Y el item NO es flete → **IGNORAR** (bloquear)
2. Si es orden de Promo Y el item SÍ es flete → **PROCESAR** (permitir)
3. Si es orden Normal/S/R → **PROCESAR** (permitir todo)

---

### RESULTADO

| Escenario | Antes | Después |
|-----------|-------|---------|
| Orden Promo + Flete Genérico | ✅ Funciona | ✅ Funciona |
| Orden Promo + Flete Personalizado | ❌ No funciona | ✅ Funciona |
| Orden Normal + Flete Personalizado | ✅ Funciona | ✅ Funciona |
| Orden Promo + Items Normales | ❌ Error | ❌ Error (correcto) |

---

### VERIFICACIÓN

**Test**: Editar orden de Promo con flete personalizado

```
1. Crear venta con Promoción
2. Editar la orden de Promo
3. Habilitar Flete
4. Agregar producto específico para flete (ej: "Cajas de envío")
5. Guardar cambios

ESPERADO:
✅ Flete se guarda
✅ Producto personalizado se guarda
✅ Orden mantiene estado [Promoción]
✅ Factura muestra flete personalizado
```

---

## 📊 RESUMEN

**Problema**: Flete personalizado bloqueado en órdenes de Promo
**Causa**: Lógica de bloqueo demasiado agresiva
**Solución**: Permitir items de flete incluso en órdenes de Promo
**Status**: ✅ CORREGIDO


