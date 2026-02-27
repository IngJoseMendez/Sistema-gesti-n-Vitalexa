# GUÍA FRONTEND: Promociones Independientes y Stock Negativo

## 📚 Cambios en Respuestas de API

### OrderItemResponse - Nuevos Campos

```typescript
interface OrderItemResponse {
  // ... campos existentes ...
  
  // ✅ NUEVOS: Promociones Independientes
  promotionInstanceId?: UUID;      // ID único para cada instancia de promoción
  promotionPackPrice?: BigDecimal; // Precio fijo de la promoción
  promotionGroupIndex?: number;    // Ordinal si hay duplicadas (1, 2, 3...)
  
  // ✅ NUEVOS: Stock Negativo
  cantidadDescontada?: number;     // Cantidad descontada del stock
  cantidadPendiente?: number;      // Cantidad pendiente (stock negativo)
}
```

---

## 🎯 Casos de Uso

### 1. Mostrar Promociones Independientes en UI

**Antes (INCORRECTO):**
```typescript
// Agrupaba por promotion.id → 2 Promo A + 2 Promo B = 2 grupos
const promoGroups = items.reduce((acc, item) => {
  const key = item.promotionId;
  if (!acc[key]) acc[key] = [];
  acc[key].push(item);
  return acc;
}, {});

// Resultado: 2 grupos (cada promo duplicada se une)
```

**Después (CORRECTO):**
```typescript
// Agrupa por promotionInstanceId → 2 Promo A + 2 Promo B = 4 grupos
const promoGroups = items.reduce((acc, item) => {
  // Usar promotionInstanceId si está disponible, fallback a promotion.id para compatibilidad
  const key = item.promotionInstanceId || item.promotionId;
  if (!acc[key]) acc[key] = [];
  acc[key].push(item);
  return acc;
}, {});

// Resultado: 4 grupos (cada instancia es independiente)
```

### 2. Eliminar Promociones Individuales

**Endpoint Propuesto:**
```typescript
// DELETE /api/orders/{orderId}/items/{itemId}
// Donde itemId es el item de promoción a eliminar

async function deletePromotionInstance(
  orderId: UUID,
  promotionInstanceId: UUID
): Promise<void> {
  // 1. Encontrar el item con este promotionInstanceId
  const itemToDelete = order.items.find(
    item => item.promotionInstanceId === promotionInstanceId
  );
  
  if (!itemToDelete) {
    throw new Error(`Instancia de promoción no encontrada: ${promotionInstanceId}`);
  }
  
  // 2. Eliminar el item
  await deleteOrderItem(orderId, itemToDelete.id);
}
```

### 3. Mostrar Stock Negativo en Bonificados

**Antes (INCORRECTO):**
```tsx
// Se mostraban 2 filas (una con stock, otra sin stock)
export const BonifiedItemRow = ({ item }: Props) => {
  return (
    <>
      <tr>
        <td>{item.productName}</td>
        <td>{item.cantidadDescontada}</td> {/* 20 */}
        <td>$0</td>
      </tr>
      {item.cantidadPendiente > 0 && (
        <tr style={{ backgroundColor: '#ffcccc' }}>
          <td>{item.productName} (PENDIENTE)</td>
          <td>{item.cantidadPendiente}</td> {/* 20 */}
          <td>$0</td>
        </tr>
      )}
    </>
  );
};

// Resultado: 2 filas para 40 unidades bonificadas (20 + 20)
```

**Después (CORRECTO):**
```tsx
// Una sola fila con cantidad total y nota de pendiente
export const BonifiedItemRow = ({ item }: Props) => {
  const hasPending = item.cantidadPendiente && item.cantidadPendiente > 0;
  
  return (
    <tr style={hasPending ? { backgroundColor: '#fff3cd' } : {}}>
      <td>{item.productName}</td>
      <td>
        {item.cantidad}
        {hasPending && (
          <span style={{ color: '#d9534f', marginLeft: '8px' }}>
            [{item.cantidadPendiente} pendiente]
          </span>
        )}
      </td>
      <td>$0</td>
    </tr>
  );
};

// Resultado: 1 fila para 40 unidades bonificadas (40 [20 pendiente])
```

### 4. Cálculo de Totales Respetando Precios Fijos

**Antes (INCORRECTO):**
```typescript
// Sumaba todos los subTotal (incluyendo ítems individuales de promo)
const calculateTotal = (items: OrderItemResponse[]): BigDecimal => {
  return items.reduce((sum, item) => sum + item.subtotal, 0);
};

// Resultado: Promo de 40+10=$500 se recalculaba como suma individual
```

**Después (CORRECTO):**
```typescript
// Respeta precios fijos de promociones
const calculateTotal = (items: OrderItemResponse[]): BigDecimal => {
  const processedPromos = new Set<UUID>();
  let total = 0;
  
  for (const item of items) {
    // Si es item de promoción con precio fijo, contar UNA SOLA VEZ
    if (item.isPromotionItem && item.promotionInstanceId && item.promotionPackPrice) {
      if (!processedPromos.has(item.promotionInstanceId)) {
        total += item.promotionPackPrice;
        processedPromos.add(item.promotionInstanceId);
      }
    } else {
      // Items normales: suma normal
      total += item.subtotal;
    }
  }
  
  return total;
};

// Resultado: Promo preserva su packPrice ($500), total correcto
```

---

## 🔄 Flujo de Actualización de Órdenes

### Crear Orden Con Promociones

```typescript
const createOrderPayload = {
  clientId: "client-uuid",
  items: [
    { productId: "prod1", cantidad: 10 },
    { productId: "prod2", cantidad: 5 }
  ],
  promotionIds: [
    "promo-a-uuid",
    "promo-a-uuid", // ← Misma promo 2 veces
    "promo-b-uuid"
  ],
  notas: "Orden con múltiples promociones"
};

// ✅ Backend generará promotionInstanceId único para cada una
// Respuesta incluirá 3 bloques de promoción separados
```

### Editar Orden (SIN cambiar promociones)

```typescript
const editOrderPayload = {
  clientId: "client-uuid",
  items: [
    { productId: "prod3", cantidad: 2 } // Agregar nuevo item
  ],
  promotionIds: [
    "promo-a-uuid",
    "promo-a-uuid",
    "promo-b-uuid"
  ], // ← MISMO que antes
  notas: "Orden actualizada"
};

// ✅ Backend detectará que promotionIds no cambiaron
// ✅ Preservará promotionInstanceId y promotionPackPrice originales
// ✅ Total se recalculará respetando precios fijos
```

### Editar Orden (CAMBIANDO promociones)

```typescript
const editOrderPayload = {
  clientId: "client-uuid",
  items: [],
  promotionIds: [
    "promo-c-uuid" // ← DIFERENTE
  ],
  notas: "Orden actualizada"
};

// ✅ Backend detectará cambio en promotionIds
// ✅ Limpiará items de promociones viejas
// ✅ Procesará nuevas promociones con NEW promotionInstanceIds
// ✅ Recalculará total correctamente
```

### Eliminar Promoción Individual

```typescript
// Endpoint propuesto:
DELETE /api/orders/{orderId}/items/{itemId}

// Donde:
// - orderId: ID de la orden
// - itemId: ID del OrderItem a eliminar (que tiene promotionInstanceId)

// Backend:
// 1. Encuentra el OrderItem por ID
// 2. Verifica que sea item de promoción
// 3. Lo elimina
// 4. Recalcula Order.total() respetando precios fijos restantes
// 5. Retorna OrderResponse actualizada
```

---

## 🎨 Componentes React Recomendados

### PromotionBlock Component

```tsx
interface PromotionBlockProps {
  promotionInstanceId: UUID;
  promotionName: string;
  promotionGroupIndex?: number;
  items: OrderItemResponse[];
  price: BigDecimal;
  onDelete: (promotionInstanceId: UUID) => Promise<void>;
  isEditable: boolean;
}

export const PromotionBlock: React.FC<PromotionBlockProps> = ({
  promotionInstanceId,
  promotionName,
  promotionGroupIndex,
  items,
  price,
  onDelete,
  isEditable
}) => {
  return (
    <div style={{ border: '1px solid #ddd', padding: '12px', marginBottom: '12px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <div>
          <h4>
            {promotionName}
            {promotionGroupIndex && ` (${promotionGroupIndex})`}
          </h4>
          <p>${price.toFixed(2)}</p>
        </div>
        {isEditable && (
          <button
            onClick={() => onDelete(promotionInstanceId)}
            style={{ background: '#dc3545', color: 'white' }}
          >
            Eliminar
          </button>
        )}
      </div>

      <table style={{ width: '100%', marginTop: '8px' }}>
        <tbody>
          {items
            .filter(item => item.promotionInstanceId === promotionInstanceId)
            .map(item => (
              <tr key={item.id}>
                <td>{item.productName}</td>
                <td>
                  {item.cantidad}
                  {item.cantidadPendiente > 0 && (
                    <span style={{ color: '#d9534f' }}>
                      {' '}[{item.cantidadPendiente} pendiente]
                    </span>
                  )}
                </td>
                <td>${item.subtotal}</td>
              </tr>
            ))}
        </tbody>
      </table>
    </div>
  );
};
```

### OrderItemsView Component

```tsx
export const OrderItemsView: React.FC<Props> = ({ order, isEditable }) => {
  // Separar items regulares vs promociones
  const regularItems = order.items.filter(i => !i.isPromotionItem);
  const promoItems = order.items.filter(i => i.isPromotionItem);

  // Agrupar por promotionInstanceId
  const promoGroups = promoItems.reduce((acc, item) => {
    const key = item.promotionInstanceId!;
    if (!acc.has(key)) {
      acc.set(key, []);
    }
    acc.get(key)!.push(item);
    return acc;
  }, new Map<UUID, OrderItemResponse[]>());

  return (
    <>
      {/* Items Regulares */}
      <h3>Items</h3>
      <table>
        {regularItems.map(item => (
          <tr key={item.id}>
            <td>{item.productName}</td>
            <td>{item.cantidad}</td>
            <td>${item.subtotal}</td>
          </tr>
        ))}
      </table>

      {/* Promociones Agrupadas */}
      {promoGroups.size > 0 && (
        <>
          <h3>Promociones</h3>
          {Array.from(promoGroups.entries()).map(([promotionInstanceId, items]) => {
            const firstItem = items[0];
            return (
              <PromotionBlock
                key={promotionInstanceId}
                promotionInstanceId={promotionInstanceId}
                promotionName={firstItem.promotionName!}
                promotionGroupIndex={firstItem.promotionGroupIndex}
                items={items}
                price={firstItem.promotionPackPrice || firstItem.subtotal}
                onDelete={handleDeletePromotion}
                isEditable={isEditable}
              />
            );
          })}
        </>
      )}
    </>
  );
};
```

---

## 🧪 Casos de Prueba para Frontend

### Test 1: Múltiples Promociones Independientes
```typescript
const orderResponse = {
  items: [
    {
      id: 'item1',
      productName: 'Producto A',
      promotionInstanceId: 'uuid-1',
      promotionName: 'Promo A',
      promotionGroupIndex: 1,
      promotionPackPrice: 500000
    },
    {
      id: 'item2',
      productName: 'Producto A',
      promotionInstanceId: 'uuid-2',
      promotionName: 'Promo A',
      promotionGroupIndex: 1,
      promotionPackPrice: 500000
    }
  ]
};

// ✅ Debe mostrar 2 bloques separados de "Promo A"
// ✅ Total = 500000 + 500000 = 1000000
```

### Test 2: Stock Negativo en Bonificados
```typescript
const orderResponse = {
  items: [
    {
      id: 'item3',
      productName: 'Bonificado X',
      cantidad: 40,
      cantidadDescontada: 20,
      cantidadPendiente: 20,
      outOfStock: true
    }
  ]
};

// ✅ Debe mostrar UNA SOLA línea
// ✅ Debe mostrar "40 [20 pendiente]"
// ✅ Total = $0 (es bonificado)
```

### Test 3: Edición sin Cambiar Promociones
```typescript
// Original: Promo A + Promo B
// Edición: Agregar item + Mismo Promo A + Mismo Promo B

// ✅ Debe preservar promotionInstanceId originales
// ✅ Debe mantener mismo promotionPackPrice
// ✅ Total debe incluir precios fijos de promos preservadas
```

---

## 📋 Checklist de Migración Frontend

- [ ] Actualizar tipos TypeScript para incluir `promotionInstanceId`, `promotionPackPrice`, `promotionGroupIndex`
- [ ] Cambiar agrupación de promociones de `promotionId` a `promotionInstanceId`
- [ ] Implementar componente `PromotionBlock` para mostrar promociones independientes
- [ ] Implementar botón "Eliminar" para cada promoción individual
- [ ] Actualizar componente de bonificados para mostrar una sola línea con stock negativo
- [ ] Actualizar cálculo de totales para respetar `promotionPackPrice`
- [ ] Testear creación, edición y eliminación de órdenes con múltiples promociones
- [ ] Testear órdenes con bonificados sin stock completo
- [ ] Validar que UI refleja cambios en tiempo real

---

**Notas:**
- Todos estos cambios son **backward compatible** con órdenes antiguas
- El frontend puede usar `promotionInstanceId || promotionId` como fallback
- En caso de dudas sobre campos, verificar la respuesta real de la API


