## CORRECCIÓN 3: Problemas en Edición de Órdenes de Promoción

### PROBLEMAS ENCONTRADOS Y RESUELTOS

#### Problema 1: Flete daña el estado de Promoción al editar
**Síntoma**: 
- Editar orden de Promoción `[Promoción]`
- Agregar/modificar Flete
- Guardar cambios
- Resultado: Orden pierde el estado `[Promoción]` y se convierte en orden normal

**Causa**:
El método `updateOrder()` estaba sobrescribiendo las notas sin preservar el suffix `[Promoción]`:
```java
// ❌ INCORRECTO - Perdía el suffix
order.setNotas(request.notas());
```

**✅ SOLUCIÓN**:
Detectar y preservar el suffix del tipo de orden:
```java
// ✅ CORRECTO - Preserva suffix
String newNotes = request.notas() != null ? request.notas() : "";
String currentNotes = order.getNotas() != null ? order.getNotas() : "";

String suffix = "";
if (currentNotes.contains("[Promoción]")) {
    suffix = " [Promoción]";
} else if (currentNotes.contains("[S/R]")) {
    suffix = " [S/R]";
} else if (currentNotes.contains("[Standard]")) {
    suffix = " [Standard]";
}

// Si la orden tiene promociones, asegurar suffix [Promoción]
if (hasPromotions && !suffix.contains("[Promoción]")) {
    suffix = " [Promoción]";
}

order.setNotas(newNotes + suffix);
```

---

#### Problema 2: Al editar, desaparece el precio de la promo
**Síntoma**:
- Crear orden de Promoción con precio especial (ej: 50,000 en lugar de 100,000)
- Editar la orden
- El precio ya no es el de la promo sino otro
- La promo pierde su precio especial

**Causa**:
Cuando se llamaba `order.clearItems()`, se borraban TODOS los items incluyendo los de promoción.
Luego, al re-procesar las promociones, creaba nuevos items pero sin preservar el pricing original.

Además, el código restauraba stock de items de promoción innecesariamente.

**✅ SOLUCIÓN**:
Preservar items de promoción durante la edición:

```java
// 1. Identificar items de promoción ANTES de borrar
List<OrderItem> promotionItems = new java.util.ArrayList<>();
for (OrderItem item : order.getItems()) {
    if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
        promotionItems.add(item);
    }
}

// 2. Limpiar solo items no promocionados
order.clearItems();

// 3. Re-agregar items de promoción preservando precios
for (OrderItem promoItem : promotionItems) {
    order.addItem(promoItem);
}

// 4. NO restaurar stock de items de promoción
if (Boolean.TRUE.equals(item.getIsPromotionItem())) {
    log.info("Item de promoción no restaura stock en edición: {}", product.getNombre());
    return;
}
```

**Resultado**:
- ✅ Precio de promo se preserva
- ✅ Items de regalo mantienen sus valores originales
- ✅ Stock no se duplica al editar

---

### CAMBIOS EN CÓDIGO

**Archivo**: `OrderServiceImpl.updateOrder()` (líneas 715-875)

**Antes (INCORRECTO)**:
```java
// Restaurar stock y limpiar
order.getItems().forEach(item -> {
    // Restaurar TODO incluyendo items de promo
});
order.clearItems();  // ❌ Borra items de promo

// Actualizar notas
order.setNotas(request.notas());  // ❌ Pierde suffix [Promoción]

// Flete
if (includeFreight) {
    order.setIncludeFreight(true);  // ❌ Daña orden
}
```

**Después (CORRECTO)**:
```java
// Preservar items de promo
List<OrderItem> promotionItems = new ArrayList<>();
for (OrderItem item : order.getItems()) {
    if (item.getIsPromotionItem()) {
        promotionItems.add(item);  // ✅ Guardar promos
    }
}
order.clearItems();
for (OrderItem promoItem : promotionItems) {
    order.addItem(promoItem);  // ✅ Re-agregar
}

// Actualizar notas preservando suffix
String suffix = " [Promoción]";  // ✅ Preserva
order.setNotas(newNotes + suffix);

// Flete
if (includeFreight) {
    order.setIncludeFreight(true);  // ✅ Mantiene estado
}
```

---

### FLUJO CORRECTO EN EDICIÓN

```
EDITAR ORDEN DE PROMO
         ↓
[PASO 1] Extraer items de promo (preservar precios)
         ↓
[PASO 2] Restaurar stock de items normales
         ↓
[PASO 3] Limpiar items normales (mantener promo)
         ↓
[PASO 4] Re-agregar items normales editados
         ↓
[PASO 5] Re-agregar items de promo (PRECIOS INTACTOS)
         ↓
[PASO 6] Preservar suffix [Promoción] en notas
         ↓
[PASO 7] Aplicar flete sin perder estado
         ↓
RESULTADO: Orden mantiene promo, precios, estado
```

---

### VALIDACIONES APLICADAS

✅ Precio de promo se preserva en edición  
✅ Suffix `[Promoción]` se mantiene en notas  
✅ Flete no daña el estado de orden  
✅ Items de regalo conservan sus valores  
✅ Stock no se duplica al editar  
✅ Promociones se siguen procesando correctamente  

---

### TESTING

**Caso de prueba 1: Editar Promo + Cambiar Flete**
1. Crear orden de Promo con flete = NO
2. Editar la orden
3. Habilitar Flete y guardar
4. Verificar:
   - ✅ Orden mantiene `[Promoción]` en notas
   - ✅ Flete aparece en total
   - ✅ Precio de promo es el correcto

**Caso de prueba 2: Editar Promo + Cambiar Notas**
1. Crear orden de Promo
2. Editar notas (ej: cambiar de "Urgente" a "Normal")
3. Guardar
4. Verificar:
   - ✅ Notas se actualizan pero mantienen `[Promoción]`
   - ✅ Precio no cambia
   - ✅ Regalos intactos

**Caso de prueba 3: Ver Precio en Factura**
1. Crear orden de Promo (precio = 50,000)
2. Editar la orden
3. Generar factura
4. Verificar:
   - ✅ Factura muestra precio correcto (50,000)
   - ✅ NO muestra precio calculado (100,000)
   - ✅ Promoción clara


