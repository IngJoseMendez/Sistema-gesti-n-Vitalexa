## 📋 PROMPT DETALLADO PARA CORRECCIONES EN FRONTEND

### ✅ Estado Actual del Backend
El backend ha sido completamente corregido. Se han resuelto 8 problemas críticos relacionados con órdenes, promociones, flete y bonificados.

**Todos los endpoints funcionan correctamente. El frontend necesita ser actualizado para funcionar con la nueva estructura.**

---

## 🎯 CAMBIOS PRINCIPALES NECESARIOS

### 1. NUEVA ESTRUCTURA DE DTOs

#### ✅ NUEVO: `BonifiedItemRequestDTO`
Los productos bonificados ahora se envían en una sección SEPARADA, no como checkbox.

```typescript
interface BonifiedItemRequestDTO {
  productId: string;
  cantidad: number;
}
```

#### ✅ ACTUALIZADO: `OrderRequestDto`
Ahora tiene dos secciones de items:

```typescript
interface OrderRequestDto {
  clientId: string;
  items?: OrderItemRequestDTO[];        // Items regulares/flete
  bonifiedItems?: BonifiedItemRequestDTO[]; // ✅ NUEVA SECCIÓN
  promotionIds?: string[];
  notas?: string;
  includeFreight?: boolean;
  isFreightBonified?: boolean;
  freightCustomText?: string;
  freightQuantity?: number;
  sellerId?: string; // Para admin
}
```

#### ✅ ACTUALIZADO: `OrderItemRequestDTO`
Se removió el campo `isBonified`:

```typescript
interface OrderItemRequestDTO {
  productId: string;
  cantidad: number;
  allowOutOfStock?: boolean;
  relatedPromotionId?: string;
  isFreightItem?: boolean;  // Solo para items de flete
  // ❌ isBonified - REMOVIDO (ahora va en bonifiedItems)
}
```

---

## 2. CAMBIOS EN LA UI DEL FORMULARIO DE ORDEN

### ✅ ANTES (INCORRECTO)
```
┌─────────────────────────────┐
│ CREAR ORDEN                 │
├─────────────────────────────┤
│ Cliente: [selector]         │
│                             │
│ PRODUCTOS:                  │
│ • Producto: [selector]      │
│   Cantidad: [input]         │
│   ☐ Bonificado    ← CONFUSO │
│   ☐ Es flete               │
│   [Agregar]                │
└─────────────────────────────┘
```

### ✅ DESPUÉS (CORRECTO)
```
┌──────────────────────────────┐
│ CREAR ORDEN                  │
├──────────────────────────────┤
│ Cliente: [selector]          │
│                              │
│ PRODUCTOS REGULARES:         │
│ ┌────────────────────────┐   │
│ │ Producto: [selector]   │   │
│ │ Cantidad: [input]      │   │
│ │ ☐ Sin Stock Ok        │   │
│ │ ☐ Es flete (admin)    │   │
│ │ [Agregar Producto]     │   │
│ │ [Tabla de productos]   │   │
│ └────────────────────────┘   │
│                              │
│ PRODUCTOS BONIFICADOS:       │ ← NUEVA SECCIÓN
│ ┌────────────────────────┐   │
│ │ Producto: [selector]   │   │
│ │ Cantidad: [input]      │   │
│ │ [Agregar Bonificado]   │   │
│ │ [Tabla de bonificados] │   │
│ └────────────────────────┘   │
│                              │
│ PROMOCIONES: [checkboxes]    │
│                              │
│ NOTAS: [textarea]            │
│ ☐ Incluir Flete             │
│ [Guardar]  [Cancelar]        │
└──────────────────────────────┘
```

---

## 3. VALIDACIONES EN FRONTEND

### ✅ Orden Debe Tener:
- AL MENOS uno de:
  - Items regulares
  - Items bonificados
  - Promociones

```typescript
const validateOrder = () => {
  const hasItems = regularItems.length > 0;
  const hasBonified = bonifiedItems.length > 0;
  const hasPromotions = promotionIds.length > 0;
  
  if (!hasItems && !hasBonified && !hasPromotions) {
    showError("Debe agregar al menos un producto, bonificado o promoción");
    return false;
  }
  return true;
};
```

### ✅ Items Regulares NO Pueden Tener `isBonified`
- El campo `isBonified` debe removerse de items regulares
- Los bonificados van EN LA SECCIÓN SEPARADA

### ✅ Bonificados NO Pueden Tener Precio
- Los bonificados siempre tienen `precioUnitario = 0` (backend)
- No mostrar campo de precio para bonificados

---

## 4. ENDPOINTS QUE NO CAMBIAN

Estos endpoints siguen funcionando IGUAL:

```
POST   /api/vendedor/orders           - Crear orden
POST   /api/admin/orders              - Crear orden (admin)
GET    /api/admin/orders              - Listar
GET    /api/admin/orders/{id}         - Obtener
PUT    /api/admin/orders/{id}         - Editar ← AHORA RESTAURA PROMO
PATCH  /api/admin/orders/{id}/status  - Cambiar estado
GET    /api/vendedor/orders           - Mis órdenes
```

**IMPORTANTE**: Al editar, las promociones se restauran automáticamente. No necesitas hacer nada especial.

---

## 5. PAYLOAD CORRECTO PARA CREAR ORDEN

### ✅ Ejemplo Completo: Normal + S/R + Promo + Bonificados + Flete

```json
{
  "clientId": "550e8400-e29b-41d4-a716-446655440001",
  "items": [
    {
      "productId": "prod-001",
      "cantidad": 10,
      "allowOutOfStock": false,
      "relatedPromotionId": null,
      "isFreightItem": false
    },
    {
      "productId": "prod-sr-001",
      "cantidad": 5,
      "allowOutOfStock": false,
      "relatedPromotionId": null,
      "isFreightItem": false
    }
  ],
  "bonifiedItems": [
    {
      "productId": "prod-regalo-001",
      "cantidad": 3
    },
    {
      "productId": "prod-regalo-002",
      "cantidad": 2
    }
  ],
  "promotionIds": [
    "promo-50k",
    "promo-descuento"
  ],
  "notas": "Venta urgente",
  "includeFreight": true,
  "isFreightBonified": false,
  "freightCustomText": "Flete express",
  "freightQuantity": 1
}
```

**Resultado**: Se crean hasta 3 órdenes separadas:
- Orden 1: [Standard] - 10 productos normales + regalos + promo
- Orden 2: [S/R] - 5 productos S/R
- (Orden 3: [Promoción] - solo si hay items relacionados a promo específica)

---

## 6. PAYLOAD CORRECTO PARA EDITAR ORDEN

### ✅ EDICIÓN DE ORDEN NORMAL (SIN PROMO)

```json
{
  "clientId": "550e8400-e29b-41d4-a716-446655440001",
  "items": [
    {
      "productId": "prod-001",
      "cantidad": 15,  // Cambió de 10 a 15
      "allowOutOfStock": false
    }
  ],
  "bonifiedItems": [],
  "promotionIds": [],
  "notas": "Actualizado",
  "includeFreight": false
}
```

### ✅ EDICIÓN DE ORDEN DE PROMOCIÓN (IMPORTANTE)

```json
{
  "clientId": "550e8400-e29b-41d4-a716-446655440001",
  "items": [],  // ← VACÍO - No agregar items a orden de promo
  "bonifiedItems": [],
  "promotionIds": ["promo-50k"],  // ← MANTENER para restaurar promo
  "notas": "Urgente - actualizado",
  "includeFreight": true  // ← Ahora se puede agregar flete sin romper
}
```

**CRÍTICO**: Al editar orden de Promo:
- ✅ Mantener `promotionIds`
- ✅ Items debe estar VACÍO
- ✅ Puede agregar flete sin problema

---

## 7. COMPORTAMIENTO ESPERADO DESPUÉS DE EDITAR

### ✅ Editar Orden Normal
- Items se actualizan
- Stock se recalcula
- Total se recalcula
- Estado se preserva `[Standard]`

### ✅ Editar Orden S/R
- Items se actualizan
- Stock se recalcula
- Total se recalcula
- Estado se preserva `[S/R]`

### ✅ Editar Orden de Promo (NUEVO)
- Items de promo (regalos) se preservan
- Precio de promo se preserva
- Puede agregar flete sin perder `[Promoción]`
- Notas se actualizan pero mantienen sufijo
- Promociones se restauran automáticamente

---

## 8. SECCIONES A ACTUALIZAR EN UI

### ✅ Formulario de Crear Orden
- [x] Agregar sección "PRODUCTOS BONIFICADOS" separada
- [x] Remover checkbox `isBonified` de items regulares
- [x] Agregar validación: al menos 1 de (items, bonificados, promos)
- [x] UI clara indicando que bonificados son regalos con precio 0

### ✅ Formulario de Editar Orden
- [x] Detectar tipo de orden por notas (contiene `[Promoción]`, `[S/R]`, etc)
- [x] Si es orden Promo: DESHABILITAR sección de items
- [x] Si es orden Promo: MOSTRAR solo los regalos que ya tiene
- [x] PERMITIR agregar flete a orden Promo
- [x] Preservar promociones al enviar (no dejar vacío promotionIds)

### ✅ Facturas
- [x] Mostrar orden Promo con título "PROMOCIÓN"
- [x] Mostrar precio correcto de promo (no suma de productos)
- [x] Mostrar regalos como $0
- [x] Agrupar por tipo: items normales, bonificados, promociones

---

## 9. COMPONENTES REACT (EJEMPLO DE REFERENCIA)

### ✅ Sección Productos Bonificados

```jsx
<div className="bonificados-section">
  <h3>Productos Bonificados (Regalos)</h3>
  <p className="help-text">
    Los productos bonificados siempre tienen precio 0 y pueden estar sin stock
  </p>
  
  <div className="form-group">
    <label>Producto a Regalar</label>
    <ProductSelector 
      value={selectedBonified}
      onChange={setSelectedBonified}
    />
  </div>
  
  <div className="form-group">
    <label>Cantidad</label>
    <input 
      type="number" 
      min="1"
      value={bonifiedQty}
      onChange={(e) => setBonifiedQty(e.target.value)}
    />
  </div>
  
  <button onClick={() => addBonified(selectedBonified, bonifiedQty)}>
    Agregar Bonificado
  </button>
  
  {bonifiedItems.length > 0 && (
    <table>
      <thead>
        <tr>
          <th>Producto</th>
          <th>Cantidad</th>
          <th>Precio</th>
          <th>Acción</th>
        </tr>
      </thead>
      <tbody>
        {bonifiedItems.map(item => (
          <tr key={item.productId}>
            <td>{item.productName}</td>
            <td>{item.cantidad}</td>
            <td>$0 (Bonificado)</td>
            <td>
              <button onClick={() => removeBonified(item.productId)}>
                Eliminar
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )}
</div>
```

### ✅ Lógica de Construcción del Payload

```typescript
const buildOrderPayload = () => {
  // Separar items
  const regularItems = items.filter(i => !i.isBonified);  // ← isBonified REMOVIDO
  
  return {
    clientId: selectedClient.id,
    items: regularItems,           // ✅ Separado
    bonifiedItems: bonifiedItems,  // ✅ Separado
    promotionIds: promotionIds,
    notas: notes,
    includeFreight: includeFreight,
    isFreightBonified: isFreightBonified,
    freightCustomText: freightCustomText,
    freightQuantity: freightQuantity,
    sellerId: sellerId  // Si es admin
  };
};
```

---

## 10. CASOS DE USO CRÍTICOS

### ✅ Caso 1: Crear Venta Compleja
```
Vendedor agrega:
- 10 productos normales
- 5 productos S/R
- 1 Promoción
- 3 productos bonificados
- Habilita Flete

Resultado Esperado:
✅ Se crean 3 órdenes (puede haber 4 si hay promo surtida)
✅ Cada orden tiene lo suyo
✅ Facturas limpias
✅ Stock correcto
```

### ✅ Caso 2: Editar Orden de Promo + Agregar Flete
```
1. Abrir orden de Promo
2. Agregar Flete
3. Guardar

Resultado Esperado:
✅ Orden mantiene [Promoción]
✅ Flete en total
✅ Precio de promo intacto
✅ Factura correcta
```

### ✅ Caso 3: Editar Orden Normal + Cambiar Items
```
1. Abrir orden Normal
2. Cambiar cantidad de productos
3. Guardar

Resultado Esperado:
✅ Orden mantiene [Standard]
✅ Items actualizados
✅ Stock recalculado
✅ Total correcto
```

---

## 11. TESTING CHECKLIST

Antes de considerar "listo", verificar:

- [ ] Crear orden normal funciona
- [ ] Crear orden S/R funciona
- [ ] Crear orden de Promo funciona
- [ ] Crear orden con bonificados funciona
- [ ] Crear orden con flete funciona
- [ ] Crear venta compleja (normal + S/R + promo + bonificados + flete) crea múltiples órdenes
- [ ] Editar orden normal: Items se actualizan
- [ ] Editar orden S/R: Items se preservan
- [ ] Editar orden Promo: Regalos se preservan, precio intacto
- [ ] Editar orden Promo + agregar flete: Mantiene [Promoción]
- [ ] Factura orden normal: Muestra items correctos
- [ ] Factura orden Promo: Muestra promoción, precio correcto
- [ ] Bonificados: Siempre precio 0
- [ ] Bonificados: Pueden estar sin stock

---

## 12. ERRORES COMUNES A EVITAR

❌ **NO HACER**:
1. Enviar `isBonified` en items regulares → ❌ (va en bonifiedItems)
2. Agregar items a orden de Promo en edición → ❌ (rompe la orden)
3. Dejar `promotionIds` vacío al editar promo → ❌ (se pierde la promo)
4. Permitir editar "Bonificado" como checkbox → ❌ (debe ser sección)
5. Calcular precio de promo como suma de items → ❌ (usar precio especial)

✅ **HACER**:
1. Enviar `items` y `bonifiedItems` separados → ✅
2. Si es orden Promo: items[] vacío → ✅
3. Siempre incluir promotionIds al editar → ✅
4. Bonificados como sección dedicada → ✅
5. Usar `packPrice` de promoción → ✅

---

## 13. DOCUMENTACIÓN DE REFERENCIA

Para más detalles técnicos, consultar:
- `GUIA_FRONTEND_BONIFICADOS.md` - Implementación específica
- `TESTING_CASOS_REPORTADOS.md` - Casos de prueba
- `CORRECCION_4_ITEMS_DUPLICADOS.md` - Por qué items no van en promo

---

## 14. RESUMEN DE CAMBIOS

| Área | Cambio | Impacto |
|------|--------|--------|
| DTOs | Nuevo `bonifiedItems` | Items separados |
| UI | Nueva sección bonificados | Más clara, menos confusión |
| Validación | Permite items O bonificados O promo | Más flexible |
| Edición | No agregar items a promo | Preserva orden |
| Flete | Funciona en todas las órdenes | Sin quebrar nada |
| Facturas | Agrupa por tipo | Más legible |

---

## 15. CONTACTO/ESCALACIÓN

Si encuentras:
- ❌ Error 400 en payload → Revisar estructura de items vs bonifiedItems
- ❌ Orden pierde promo → Verificar que promotionIds está incluido
- ❌ Aparecen items extra → Verificar que no se están agregando items a orden Promo
- ❌ Flete no aparece → Verificar que `includeFreight: true`

**Todos estos casos están cubiertos en el backend. El frontend solo necesita enviar el payload correcto.**

---

**¡Listo para implementar!** 🚀


