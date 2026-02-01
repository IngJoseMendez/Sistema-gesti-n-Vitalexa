## GUÍA DE INTEGRACIÓN FRONTEND - BONIFICADOS COMO SECCIÓN SEPARADA

### Cambios Necesarios en el Frontend

Este documento describe cómo actualizar la interfaz de usuario para trabajar con la nueva estructura de bonificados.

---

## 1. ESTRUCTURA ANTERIOR (A DEPRECAR)

```javascript
// ❌ ANTERIOR - Checkbox mezclado
const orderItemsFormData = {
  productId: "uuid",
  cantidad: 5,
  isBonified: true,  // ❌ Checkbox - Confuso
  isFreightItem: false
}
```

---

## 2. NUEVA ESTRUCTURA (A IMPLEMENTAR)

### 2.1 Items Regulares (SIN bonificados)

```javascript
// ✅ NUEVO - Items regulares/flete
const regularItems = [
  {
    productId: "uuid-producto-1",
    cantidad: 10,
    allowOutOfStock: false,
    relatedPromotionId: null,
    isFreightItem: false  // Solo para items de flete
  },
  {
    productId: "uuid-producto-2",
    cantidad: 5,
    allowOutOfStock: true,  // Puede no tener stock
    relatedPromotionId: null,
    isFreightItem: false
  }
]
```

### 2.2 Productos Bonificados (SECCIÓN SEPARADA)

```javascript
// ✅ NUEVO - Sección dedicada a bonificados
const bonifiedItems = [
  {
    productId: "uuid-regalo-1",
    cantidad: 5
  },
  {
    productId: "uuid-regalo-2",
    cantidad: 3
  }
]
```

### 2.3 Payload Completo de Orden

```javascript
const createOrderPayload = {
  clientId: "uuid-cliente",
  items: regularItems,           // Items normales/flete
  bonifiedItems: bonifiedItems,  // SECCIÓN SEPARADA
  promotionIds: ["uuid-promo-1", "uuid-promo-2"],
  notas: "Observaciones de la orden",
  includeFreight: false,
  isFreightBonified: false,
  freightCustomText: null,
  freightQuantity: 1,
  sellerId: null  // Para admin creando a nombre de otro
}
```

---

## 3. CAMBIOS EN INTERFAZ DE USUARIO

### 3.1 Secciones del Formulario de Orden

#### ANTES:
```
┌─────────────────────────────┐
│ CREAR ORDEN                 │
├─────────────────────────────┤
│ Cliente: [selector]         │
│                             │
│ PRODUCTOS:                  │
│ • Producto: [selector]      │
│   Cantidad: [input]         │
│   ☐ Bonificado    ← Confuso │
│   ☐ Es flete               │
│   [Agregar]                │
│                             │
│ NOTAS: [textarea]           │
│ [Guardar]  [Cancelar]       │
└─────────────────────────────┘
```

#### DESPUÉS:
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
│ │ [Agregar]              │   │
│ │ [Tabla de productos]   │   │
│ └────────────────────────┘   │
│                              │
│ PRODUCTOS BONIFICADOS:       │
│ ┌────────────────────────┐   │
│ │ Producto: [selector]   │ ← Sección separada
│ │ Cantidad: [input]      │   │
│ │ [Agregar Bonificado]   │   │
│ │ [Tabla de bonificados] │   │
│ └────────────────────────┘   │
│                              │
│ PROMOCIONES: [checkboxes]    │
│                              │
│ NOTAS: [textarea]            │
│ [Guardar]  [Cancelar]        │
└──────────────────────────────┘
```

### 3.2 Componentes React (Ejemplo)

#### Sección Productos Regulares:
```jsx
<div className="productos-regulares-section">
  <h3>Productos Regulares</h3>
  
  <div className="form-group">
    <label>Producto</label>
    <ProductSelector 
      value={selectedProduct}
      onChange={setSelectedProduct}
    />
  </div>
  
  <div className="form-group">
    <label>Cantidad</label>
    <input 
      type="number" 
      min="1"
      value={cantidad}
      onChange={(e) => setCantidad(e.target.value)}
    />
  </div>
  
  <div className="form-group">
    <label>
      <input 
        type="checkbox"
        checked={allowOutOfStock}
        onChange={(e) => setAllowOutOfStock(e.target.checked)}
      />
      Permitir sin stock
    </label>
  </div>
  
  <div className="form-group">
    <label>
      <input 
        type="checkbox"
        checked={isFreightItem}
        onChange={(e) => setIsFreightItem(e.target.checked)}
        disabled={!isAdmin}
      />
      Incluir en flete (solo admin)
    </label>
  </div>
  
  <button onClick={addRegularItem}>
    Agregar Producto
  </button>
  
  {/* Tabla de productos agregados */}
  <ItemsTable 
    items={regularItems}
    onRemove={removeRegularItem}
  />
</div>
```

#### Sección Productos Bonificados:
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
      value={bonifiedQuantity}
      onChange={(e) => setBonifiedQuantity(e.target.value)}
    />
  </div>
  
  <button onClick={addBonifiedItem}>
    Agregar Bonificado
  </button>
  
  {/* Tabla de bonificados agregados */}
  <BonifiedTable 
    items={bonifiedItems}
    onRemove={removeBonifiedItem}
  />
</div>
```

---

## 4. LÓGICA DE VALIDACIÓN

### En el Frontend:

```javascript
// ✅ Validar que haya al menos items O promociones
const validateOrder = () => {
  const hasItems = regularItems.length > 0;
  const hasPromotions = promotionIds.length > 0;
  const hasBonified = bonifiedItems.length > 0;
  
  if (!hasItems && !hasPromotions && !hasBonified) {
    alert("Debe agregar al menos un producto o una promoción");
    return false;
  }
  
  return true;
}

// ✅ Construir payload correcto
const buildOrderPayload = () => {
  return {
    clientId: selectedClient.id,
    items: regularItems,           // ✅ Separado
    bonifiedItems: bonifiedItems,  // ✅ Separado
    promotionIds: promotionIds,
    notas: notes,
    includeFreight: includeFreight,
    isFreightBonified: isFreightBonified,
    freightCustomText: freightCustomText,
    freightQuantity: freightQuantity
  }
}
```

---

## 5. EDICIÓN DE ÓRDENES

### Importante: Promociones se restauran automáticamente

```javascript
// Al editar, NO necesitas volver a seleccionar promociones
// El sistema las restaura automáticamente si envías promotionIds

const updateOrder = async (orderId, payload) => {
  // ✅ Las promociones se restauran automáticamente
  // NO necesitas hacer nada especial
  
  const response = await api.put(`/api/admin/orders/${orderId}`, {
    clientId: payload.clientId,
    items: payload.items,
    bonifiedItems: payload.bonifiedItems,  // Enviar siempre
    promotionIds: payload.promotionIds,    // ✅ Se restauran
    notas: payload.notas
  })
  
  return response
}
```

---

## 6. ENDPOINTS QUE NO CAMBIAN

Los siguientes endpoints siguen funcionando igual:

```
POST   /api/admin/orders           - Crear orden
GET    /api/admin/orders           - Listar
GET    /api/admin/orders/{id}      - Obtener
PUT    /api/admin/orders/{id}      - Editar ← Ahora restaura promos
PATCH  /api/admin/orders/{id}/status - Cambiar estado
```

---

## 7. ENDPOINTS OPCIONALES (Futuros)

Estos endpoints se pueden agregar para gestión granular de bonificados:

```
POST   /api/admin/orders/{orderId}/bonified-items
       - Agregar bonificados a orden existente

PUT    /api/admin/orders/{orderId}/bonified-items/{itemId}
       - Actualizar cantidad de un bonificado

DELETE /api/admin/orders/{orderId}/bonified-items/{itemId}
       - Remover un bonificado
```

---

## 8. MIGRATION PATH (Recomendado)

### Fase 1: Backend (✅ COMPLETADA)
- [x] DTOs actualizados
- [x] OrderServiceImpl actualizado
- [x] Lógica de split corregida
- [x] Promociones se restauran en edición

### Fase 2: Frontend (A HACER)
- [ ] Agregar sección de bonificados al formulario
- [ ] Remover checkbox `isBonified` de items regulares
- [ ] Actualizar payload a nueva estructura
- [ ] Probar edición de órdenes (verificar promociones se restauran)
- [ ] Probar split Normal/S/R/Promo

### Fase 3: Testing
- [ ] Crear orden con normal + bonificados + promo
- [ ] Editar orden (verificar promo no se pierde)
- [ ] Verificar split correcto con stock mixto
- [ ] Validar bonificados sin stock

---

## 9. CÓDIGO DE REFERENCIA

### TypeScript Interfaces (sugerido)

```typescript
// DTO para items regulares
interface OrderItemRequest {
  productId: string;
  cantidad: number;
  allowOutOfStock?: boolean;
  relatedPromotionId?: string | null;
  isFreightItem?: boolean;
}

// DTO para items bonificados
interface BonifiedItemRequest {
  productId: string;
  cantidad: number;
}

// DTO completo de orden
interface CreateOrderRequest {
  clientId: string;
  items?: OrderItemRequest[];
  bonifiedItems?: BonifiedItemRequest[];
  promotionIds?: string[];
  notas?: string;
  includeFreight?: boolean;
  isFreightBonified?: boolean;
  freightCustomText?: string;
  freightQuantity?: number;
  sellerId?: string;
}
```

---

## 10. CHECKLIST ANTES DE DEPLOY

- [ ] Formulario tiene sección separada para bonificados
- [ ] Checkbox `isBonified` removido de items regulares
- [ ] Payload incluye `bonifiedItems` como array separado
- [ ] Validación permite al menos 1 de: items, bonificados, o promos
- [ ] Edición restaura promociones automáticamente
- [ ] UI es clara sobre qué es bonificado vs regular
- [ ] Help text explica que bonificados son regalos con precio 0
- [ ] Tests de integración pasan

---

**¡Éxito con la implementación!**

