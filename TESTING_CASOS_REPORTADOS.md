## TESTING ESPECÍFICO - Casos que Reportaste

### Test 1: Flete + Edición de Orden de Promo

Este es el problema que reportaste: **Al editar promo con flete, se pierde el estado `[Promoción]`**

#### Pasos:
1. **Crear orden de Promo**:
   - Seleccionar cliente
   - Agregar SOLO una Promoción (sin items normales)
   - Guardar
   - Resultado esperado: Orden `[Promoción]` creada

2. **Editar la orden**:
   - Ir a editar
   - Habilitar Flete: ✅ Marcar "Incluir Flete"
   - Guardar cambios

#### Verificar ✅:
- [ ] En BD: `SELECT notas FROM orders WHERE id='xxx'` → Debe contener `[Promoción]`
- [ ] En UI: Orden muestra `[Promoción]` en las notas
- [ ] Flete aparece en el total
- [ ] Factura dice "Promoción" (no "Normal")
- [ ] Regalos siguen siendo gratuitos

#### Antes (INCORRECTO):
```
Notas: "Urgente" (perdió [Promoción])
Estado: Normal (no Promo)
Factura: Sin promoción
```

#### Después (CORRECTO):
```
Notas: "Urgente [Promoción]" ✅
Estado: Promo ✅
Factura: Con promoción ✅
```

---

### Test 2: Precio de Promo Desaparece

Este es el problema que reportaste: **Al editar, el precio de promo cambia a otro**

#### Pasos:
1. **Crear orden de Promo con precio especial**:
   - Ir a Promociones
   - Crear una promoción con `packPrice = 50000` (especial)
   - Crear orden con esa promoción
   - Verificar en factura que muestre 50,000

2. **Editar la orden**:
   - Ir a editar
   - Cambiar algo (ej: notas, o agregar flete)
   - Guardar cambios

3. **Verificar en factura**:
   - Abrir factura
   - Ver el precio del item de promo

#### Verificar ✅:
- [ ] Factura ANTES de editar: Muestra 50,000 ✅
- [ ] Factura DESPUÉS de editar: SIGUE mostrando 50,000 ✅
- [ ] NO muestra precio calculado (100,000 u otro)
- [ ] Cantidad de regalos igual
- [ ] Total igual

#### Antes (INCORRECTO):
```
Antes de editar: 50,000 ✓
Después de editar: 75,000 ✗ (cambió)
```

#### Después (CORRECTO):
```
Antes de editar: 50,000 ✓
Después de editar: 50,000 ✓ (preservado)
```

---

### Test 3: Editar Normal + S/R + Promo + Flete

Caso complejo: La orden más complicada posible

#### Pasos:
1. **Crear orden**:
   - 10 productos normales
   - 5 productos S/R
   - 1 Promoción
   - Sin Flete
   - Resultado: 3 órdenes creadas

2. **Editar orden de Promo**:
   - Abrir la orden `[Promoción]`
   - Agregar Flete
   - Cambiar notas a "Urgente"
   - Guardar

#### Verificar ✅:
- [ ] Orden 1 (Normal): Solo items normales, sin promo
- [ ] Orden 2 (S/R): Solo items S/R
- [ ] Orden 3 (Promo): 
  - [ ] Mantiene `[Promoción]` en notas
  - [ ] Notas: "Urgente [Promoción]"
  - [ ] Flete en total
  - [ ] Regalos correctos
  - [ ] Factura clara

---

### Test 4: Ver Precios en Factura

La verificación más importante

#### Pasos:
1. Crear orden de Promo (packPrice = 50,000)
2. Editar la orden (cambiar algo)
3. Generar factura
4. Abrir PDF de factura

#### Verificar ✅:
- [ ] La línea de la promo muestra:
  ```
  [PROMOCIÓN NAME]     Qty: X     Price: 50,000     Total: 50,000
  ```
- [ ] NO muestra:
  ```
  [PRODUCTO NAME]      Qty: 10    Price: 5,000      Total: 50,000
  ```
- [ ] Total de orden correcto
- [ ] Dice "Promoción" en algún lado
- [ ] Regalos aparecen como $0

---

### Test 5: Casos de No Debería Fallar

Estos casos deben seguir funcionando:

#### 5.1: Editar Normal (sin promo)
```
1. Crear orden Normal
2. Editar y guardar
3. Verificar: Mantiene estado [Standard]
```

#### 5.2: Editar S/R (sin promo)
```
1. Crear orden S/R
2. Editar y guardar
3. Verificar: Mantiene estado [S/R]
```

#### 5.3: Promo sin Editar
```
1. Crear orden Promo
2. NO editar
3. Verificar: Factura correcta, precio correcto
```

---

## Queries SQL para Testing

```sql
-- Ver estado de orden
SELECT id, notas, estado, include_freight, total 
FROM orders 
WHERE id = 'uuid-orden'
LIMIT 1;

-- Ver items con detalles
SELECT oi.id, p.nombre, oi.cantidad, oi.precio_unitario, 
       oi.is_promotion_item, oi.is_free_item, oi.precio_unitario * oi.cantidad as subtotal
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id
WHERE o.id = 'uuid-orden'
ORDER BY oi.id;

-- Ver si hay promo
SELECT oi.promotion_id, pr.nombre, COUNT(*) as items
FROM order_items oi
LEFT JOIN promotions pr ON oi.promotion_id = pr.id
WHERE oi.order_id = 'uuid-orden'
GROUP BY oi.promotion_id, pr.nombre;

-- Ver precio de promo
SELECT o.id, o.notas, oi.precio_unitario, pr.pack_price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
LEFT JOIN promotions pr ON oi.promotion_id = pr.id
WHERE o.id = 'uuid-orden'
AND oi.is_promotion_item = true;
```

---

## Checklist Final

### Antes de Considerar "Listo"

- [ ] Test 1: Promo + Flete en Edición → Mantiene `[Promoción]`
- [ ] Test 2: Precio de Promo → Se preserva después de editar
- [ ] Test 3: Normal + S/R + Promo + Flete → 3 órdenes correctas
- [ ] Test 4: Factura → Muestra precio correcto
- [ ] Test 5.1: Editar Normal → Mantiene estado
- [ ] Test 5.2: Editar S/R → Mantiene estado
- [ ] Test 5.3: Promo sin editar → Factura correcta
- [ ] Todas las queries SQL funcionan
- [ ] BD consistente (sin duplicados)

### Si Algo Falla

Reportar:
1. Número de test que falló
2. Pasos exactos
3. Output esperado vs actual
4. ID de orden (para debugging)

Ejemplo:
```
Test 1 FALLÓ
Pasos: Crear promo → Editar + Flete → Guardar
Esperado: Notas = "Urgente [Promoción]"
Actual: Notas = "Urgente"
Orden ID: 550e8400-e29b-41d4-a716-446655440000
```


