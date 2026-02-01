## TESTING MANUAL - VERIFICAR CORRECCIONES

### Casos de Prueba

Prueba los siguientes escenarios para verificar que todo funciona correctamente:

---

## Test 1: Orden Normal + S/R + Promoción (SIN Flete)

### Pasos:
1. Ir a crear orden
2. Seleccionar cliente
3. Agregar:
   - **Productos Normales**: 10 unidades de un producto normal
   - **Productos S/R**: 5 unidades de un producto con etiqueta S/R
   - **Promoción**: Seleccionar una promoción activa
4. Guardar

### Resultado ESPERADO ✅:
- **Se crean 3 órdenes separadas**:
  1. Orden Standard `[Standard]` - Con los 10 productos normales, SIN promoción
  2. Orden S/R `[S/R]` - Con los 5 productos S/R
  3. Orden Promoción `[Promoción]` - Con los regalos de la promoción

- **En la factura de Orden 1 (Normal)**:
  - ✅ Aparecen los 10 productos normales
  - ✅ NO aparece la promoción
  - ✅ NO aparecen los regalos

- **En la factura de Orden 3 (Promo)**:
  - ✅ Aparecen los regalos de la promoción
  - ✅ Título dice `[Promoción]`

### Resultado INCORRECTO ❌ (Lo que pasaba antes):
- Orden 1 contenía AMBOS: productos normales Y promoción (duplicada)
- Orden 3 contenía solo regalos
- Las facturas mostraban la promo duplicada

---

## Test 2: Orden Promoción + Flete

### Pasos:
1. Ir a crear orden
2. Seleccionar cliente
3. Agregar:
   - **Promoción**: Seleccionar una promoción activa
4. **Habilitar Flete**: ✅ Marcar "Incluir Flete"
5. Guardar

### Resultado ESPERADO ✅:
- **Se crea 1 orden de Promoción** `[Promoción]`
- **En la orden**:
  - ✅ Mantiene el estado `[Promoción]` en las notas
  - ✅ El flete aparece en el total
  - ✅ Se ve claramente como orden de promoción, no como normal

- **En la factura**:
  - ✅ Dice "Promoción" arriba
  - ✅ Muestra los regalos
  - ✅ Muestra el flete en totales
  - ✅ Total correcto

### Resultado INCORRECTO ❌ (Lo que pasaba antes):
- Orden perdía el estado `[Promoción]`
- Se veía como orden normal
- Factura no mostraba promoción claramente

---

## Test 3: Editar Orden con Promoción

### Pasos:
1. Crear una orden con:
   - Productos normales
   - Promoción activa
2. Esperar a que se cree exitosamente
3. Ir a editar la orden de promoción `[Promoción]`
4. Cambiar cantidad de algo o agregar un bonificado
5. Guardar cambios

### Resultado ESPERADO ✅:
- ✅ La promoción se RESTAURA automáticamente
- ✅ No necesitas volver a seleccionar la promoción
- ✅ Los regalos siguen ahí
- ✅ El estado `[Promoción]` se mantiene

### Resultado INCORRECTO ❌ (Lo que pasaba antes):
- Orden perdía la promoción al editar
- Los regalos desaparecían
- Había que volver a crear la orden

---

## Test 4: Normal + S/R + Promo + Flete

### Pasos:
1. Crear orden con:
   - Productos Normales: 10 unidades
   - Productos S/R: 5 unidades
   - Promoción: Activa
   - Flete: ✅ Habilitado (solo admin/owner)
2. Guardar

### Resultado ESPERADO ✅:
- **3 órdenes creadas**:
  1. Orden Standard - con 10 productos + flete
  2. Orden S/R - con 5 productos
  3. Orden Promo - con regalos

- **Verificar en cada factura**:
  - Orden 1: Productos + Flete ✅
  - Orden 2: Productos S/R ✅
  - Orden 3: Promoción + Regalos ✅

---

## Test 5: Solo Promoción (sin items normales)

### Pasos:
1. Crear orden con:
   - SIN Productos Normales
   - SIN Productos S/R
   - CON Promoción
2. Guardar

### Resultado ESPERADO ✅:
- **Se crea 1 orden**: Orden Promoción `[Promoción]`
- ✅ Los regalos aparecen
- ✅ Estado claramente es promoción

---

## Verificación en Base de Datos (SQL)

Si quieres verificar manualmente:

```sql
-- Ver todas las órdenes creadas
SELECT id, notas, estado, total 
FROM orders 
ORDER BY fecha DESC 
LIMIT 10;

-- Ver items de una orden de promoción
SELECT oi.id, p.nombre, oi.cantidad, oi.is_promotion_item, oi.is_free_item, oi.precio_unitario
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id
WHERE o.id = 'uuid-de-orden'
ORDER BY oi.id;

-- Ver promociones en items
SELECT oi.is_promotion_item, oi.promotion_id, COUNT(*)
FROM order_items oi
GROUP BY oi.is_promotion_item, oi.promotion_id;
```

---

## Checklist de Validación

- [ ] Test 1: Orden Normal + S/R + Promo (NO duplica)
- [ ] Test 2: Orden Promo + Flete (mantiene estado)
- [ ] Test 3: Edición restaura promo
- [ ] Test 4: Normal + S/R + Promo + Flete (3 órdenes)
- [ ] Test 5: Solo Promo (1 orden)
- [ ] Facturas muestran correctamente cada orden
- [ ] Stock se descuenta correctamente
- [ ] Al editar, no hay duplicación
- [ ] Flete no daña órdenes de promo

---

## Reportar Problemas

Si encuentras algún problema durante el testing:

1. **Documenta el paso exacto donde ocurre**
2. **Describe lo que esperabas vs lo que pasó**
3. **Proporciona los IDs de las órdenes generadas**
4. **Screenshot de la factura si es visual**

Ejemplo:
```
Problema: Al crear orden Normal + S/R + Promo, aparecen 4 órdenes en lugar de 3
Pasos: ...
Orden creada: uuid-xxxxx
Esperado: 3 órdenes (Standard, S/R, Promo)
Actual: 4 órdenes
```

