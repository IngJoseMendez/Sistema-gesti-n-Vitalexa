# 🐛 Bug: Duplicación de Items en Órdenes de Promoción al Agregar Flete

## Problema

Cuando se edita una **orden de promoción** para agregar flete, los productos de la promoción se **duplican** en la orden:

**Antes de agregar flete:**
- ✅ 1 línea de promoción (40 + 10)
- ✅ Items bonificados

**Después de agregar flete:**
- ❌ 1 línea de promoción (40 + 10)
- ❌ Items bonificados
- ❌ **40 unidades de "prueba normal" (duplicado)**
- ❌ **10 unidades de "narturaljadz" (duplicado)**
- ✅ 1 línea de flete

---

## Causa Raíz

El frontend está enviando **todos los items de la orden** (incluyendo los items de la promoción) en el request de edición, cuando solo debería enviar los **items de flete nuevos**.

### Request Actual (INCORRECTO) ❌

```json
PUT /api/orders/{orderId}
{
  "items": [
    {
      "productId": "product-1-uuid",
      "cantidad": 40,
      "isFreightItem": false
    },
    {
      "productId": "product-2-uuid",
      "cantidad": 10,
      "isFreightItem": false
    },
    {
      "productId": "freight-product-uuid",
      "cantidad": 5,
      "isFreightItem": true
    }
  ],
  "promotionIds": ["promo-uuid"],
  "includeFreight": true,
  "clientId": "client-uuid",
  "notas": "Pedido de prueba"
}
```

### Request Correcto (ESPERADO) ✅

```json
PUT /api/orders/{orderId}
{
  "items": [
    {
      "productId": "freight-product-uuid",
      "cantidad": 5,
      "isFreightItem": true
    }
  ],
  "promotionIds": ["promo-uuid"],
  "includeFreight": true,
  "clientId": "client-uuid",
  "notas": "Pedido de prueba"
}
```

---

## Solución

Cuando se edita una **orden de promoción**, el array `items` debe contener **SOLO**:
- Items de **flete** (con `isFreightItem: true`)
- Items **bonificados** (si se están agregando/modificando)

**NO debe incluir:**
- ❌ Items normales de la promoción (el backend los preserva automáticamente)

---

## Implementación

### Paso 1: Identificar si es Orden de Promoción

Agregar una función helper en el componente de edición:

```typescript
const isPromotionOrder = (order: Order): boolean => {
  return order.promotionIds && order.promotionIds.length > 0;
  // O alternativamente:
  // return order.notas?.includes('[Promoción]');
};
```

### Paso 2: Filtrar Items al Editar

Modificar la función que construye el request de edición:

**Antes (INCORRECTO):**

```typescript
const handleSaveOrder = async () => {
  const requestPayload = {
    items: allItems,  // ❌ Envía TODOS los items incluyendo promoción
    promotionIds: order.promotionIds,
    includeFreight: includeFreight,
    freightQuantity: freightQuantity,
    clientId: selectedClient?.id,
    notas: notes,
  };
  
  await updateOrder(orderId, requestPayload);
};
```

**Después (CORRECTO):**

```typescript
const handleSaveOrder = async () => {
  // Filtrar items según tipo de orden
  let itemsToSend = allItems;
  
  if (isPromotionOrder(order)) {
    // Para órdenes de promoción: SOLO enviar flete y bonificados
    itemsToSend = allItems.filter(item => 
      item.isFreightItem === true || item.isBonified === true
    );
  }
  
  const requestPayload = {
    items: itemsToSend,  // ✅ Solo flete/bonificados para órdenes promo
    promotionIds: order.promotionIds,
    includeFreight: includeFreight,
    freightQuantity: freightQuantity,
    clientId: selectedClient?.id,
    notas: notes,
  };
  
  await updateOrder(orderId, requestPayload);
};
```

### Paso 3: Lógica Específica para Agregar Flete

Si solo se está agregando flete sin modificar otros items:

```typescript
const handleAddFreight = async () => {
  const freightItems = [{
    productId: freightProductId,
    cantidad: freightQuantity,
    isFreightItem: true,
    allowOutOfStock: true
  }];
  
  const requestPayload = {
    items: freightItems,  // ✅ SOLO el flete
    promotionIds: order.promotionIds,  // Preservar promociones existentes
    includeFreight: true,
    freightQuantity: freightQuantity,
    clientId: order.client?.id,
    notas: order.notas,
  };
  
  await updateOrder(order.id, requestPayload);
};
```

---

## Ejemplo Completo (React/TypeScript)

```typescript
// En el componente EditOrderModal.tsx o similar

const EditOrderModal: React.FC<EditOrderModalProps> = ({ order, onClose }) => {
  const [items, setItems] = useState<OrderItem[]>([]);
  const [includeFreight, setIncludeFreight] = useState(order.includeFreight);
  
  const isPromotionOrder = useMemo(() => {
    return order.promotionIds && order.promotionIds.length > 0;
  }, [order]);

  const handleSubmit = async () => {
    // Construir lista de items a enviar
    let itemsToSend: OrderItemRequest[] = [];
    
    if (isPromotionOrder) {
      // ORDEN DE PROMOCIÓN: Solo enviar flete y bonificados
      itemsToSend = items
        .filter(item => item.isFreightItem || item.isBonified)
        .map(item => ({
          productId: item.product.id,
          cantidad: item.cantidad,
          isFreightItem: item.isFreightItem,
          isBonified: item.isBonified,
          allowOutOfStock: true
        }));
      
      console.log('✅ Orden de Promoción: Enviando solo flete/bonificados');
    } else {
      // ORDEN NORMAL: Enviar todos los items
      itemsToSend = items.map(item => ({
        productId: item.product.id,
        cantidad: item.cantidad,
        isFreightItem: item.isFreightItem,
        allowOutOfStock: item.allowOutOfStock
      }));
      
      console.log('✅ Orden Normal: Enviando todos los items');
    }

    const payload: UpdateOrderRequest = {
      items: itemsToSend,
      promotionIds: order.promotionIds,
      bonifiedItems: bonifiedItems,
      includeFreight: includeFreight,
      freightQuantity: freightQuantity,
      isFreightBonified: isFreightBonified,
      freightCustomText: freightCustomText,
      clientId: selectedClient?.id,
      notas: notes
    };

    try {
      await orderService.updateOrder(order.id, payload);
      toast.success('Orden actualizada correctamente');
      onClose();
    } catch (error) {
      toast.error('Error al actualizar orden');
    }
  };

  return (
    // ... JSX del modal
  );
};
```

---

## Verificación

### Backend Logs (Esperados)

Después del fix, al editar orden promo + agregar flete deberías ver:

```
📝 Orden xxx: Notas='Pedido [Promoción]', esPromocion=true
📦 Request tiene 1 items totales                    ← Solo flete
📦 Items filtrados: 0 normales, 1 flete             ← Sin items normales
Items de flete procesados: 1 items                   ← Flete agregado
Promociones sin cambios: [uuid] - Items preservados ← Promo preservada
```

### Frontend Logs (Agregar)

Para debug, agregar logs antes de enviar el request:

```typescript
console.log('🔍 Orden es promoción:', isPromotionOrder);
console.log('📦 Items originales:', allItems.length);
console.log('📦 Items a enviar:', itemsToSend.length);
console.log('📦 Detalle items:', itemsToSend.map(i => ({
  product: i.productId,
  cantidad: i.cantidad,
  esFlete: i.isFreightItem
})));
```

### UI - Resultado Esperado

**Factura después de agregar flete:**
- ✅ 1 línea: "PROMOCIÓN: 40 + 10 - Precio: $450000"
- ✅ 1 línea: "narturaljadz (BONIFICADO) - 10 x $0.00"
- ✅ 1 línea: "FLETE - 5 x $0.00"
- ✅ Total: $450000 (sin cambios)

**NO debe aparecer:**
- ❌ "prueba normal - 40 x $15000"
- ❌ "narturaljadz - 10 x $25000"

---

## Archivos Probables a Modificar

Buscar en el frontend:

```bash
# Buscar componentes de edición de órdenes
grep -r "updateOrder\|editOrder" src/components
grep -r "OrderEditModal\|EditOrderModal" src/

# Buscar construcción de payload de órdenes
grep -r "items:" src/ | grep -i order
```

Archivos comunes:
- `src/components/orders/EditOrderModal.tsx`
- `src/components/admin/OrdersPanel.tsx`
- `src/services/orderService.ts`
- `src/hooks/useOrders.ts`

---

## Resumen

**Cambio Principal:**
```typescript
// ❌ ANTES
const itemsToSend = allItems;

// ✅ DESPUÉS  
const itemsToSend = isPromotionOrder(order)
  ? allItems.filter(item => item.isFreightItem || item.isBonified)
  : allItems;
```

**Regla de Oro:**
> Para órdenes de promoción, el backend preserva automáticamente los items de la promoción. El frontend solo debe enviar items de flete o bonificados que se estén agregando/modificando.

---

## Contacto

Si tienes dudas sobre el backend o necesitas más información sobre qué está recibiendo el servidor, contacta al equipo de backend.

**Logs útiles en backend:**
```bash
# Ver request completo
logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping=DEBUG

# Ver items procesados
logging.level.org.example.sistema_gestion_vitalexa.service.impl.OrderServiceImpl=INFO
```
