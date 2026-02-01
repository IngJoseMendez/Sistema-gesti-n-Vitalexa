## 📝 RESUMEN EJECUTIVO PARA FRONTEND

### Objetivo
Actualizar la interfaz de usuario para trabajar con la nueva estructura de órdenes, promociones y bonificados que ha sido implementada en el backend.

---

## 🎯 CAMBIOS PRINCIPALES (3 PUNTOS CLAVE)

### 1️⃣ SEPARAR BONIFICADOS EN SECCIÓN PROPIA
**Antes**: Checkbox `isBonified` mezclado con items regulares ❌
**Ahora**: Sección dedicada "PRODUCTOS BONIFICADOS" ✅

```
ANTES:
├─ Productos
│  ├─ Producto X
│  └─ ☐ Bonificado ← Confuso

AHORA:
├─ Productos Regulares
│  └─ Producto X
└─ Productos Bonificados
   └─ Producto Y (regalo)
```

### 2️⃣ ACTUALIZAR ESTRUCTURA DE PAYLOAD
**Antes**: Todo mezclado en `items` ❌
**Ahora**: Dos campos separados ✅

```typescript
// ❌ ANTES
items: [
  { productId: '1', cantidad: 10, isBonified: false },
  { productId: '2', cantidad: 5, isBonified: true }  // Confuso
]

// ✅ AHORA
items: [
  { productId: '1', cantidad: 10 }  // Solo regulares
],
bonifiedItems: [
  { productId: '2', cantidad: 5 }   // Bonificados separados
]
```

### 3️⃣ FIJAR EDICIÓN DE ÓRDENES DE PROMO
**Problema**: Al editar promo + flete se rompía ❌
**Solución**: Backend ya valida tipo de orden, frontend debe:
- Detectar si es orden de Promo por notas
- No enviar items para órdenes de Promo
- Mantener `promotionIds` siempre

```typescript
// ❌ INCORRECTO (rompe la orden)
PUT /api/admin/orders/{id}
{
  items: [todos los items...],  // ← NO ENVIAR SI ES PROMO
  promotionIds: []  // ← Deja vacío (pierden las promo)
}

// ✅ CORRECTO
PUT /api/admin/orders/{id}
{
  items: [],  // ← VACÍO si es orden de Promo
  bonifiedItems: [],
  promotionIds: ["promo-id"]  // ← MANTENER
}
```

---

## 📋 CHECKLIST DE IMPLEMENTACIÓN

### Fase 1: DTOs (Estructura de Datos)
- [ ] Remover campo `isBonified` de `OrderItemRequestDTO`
- [ ] Crear nuevo DTO `BonifiedItemRequestDTO` (productId + cantidad)
- [ ] Agregar campo `bonifiedItems` a `OrderRequestDto`
- [ ] Actualizar tipos TypeScript en frontend

### Fase 2: Formulario de Crear Orden
- [ ] Crear sección "PRODUCTOS BONIFICADOS" (nueva)
- [ ] Remover checkbox `isBonified` de productos regulares
- [ ] Validar: Al menos 1 de (items, bonificados, promo)
- [ ] Help text: "Bonificados son regalos con precio $0"

### Fase 3: Formulario de Editar Orden
- [ ] Detectar tipo de orden (`[Promoción]`, `[S/R]`, `[Standard]`)
- [ ] Si es Promo: Deshabilitar sección de items
- [ ] Si es Promo: Mostrar solo regalos actuales
- [ ] Permitir agregar flete incluso en promo
- [ ] CRÍTICO: Siempre enviar `promotionIds` (no dejar vacío)

### Fase 4: Tabla de Items
- [ ] Mostrar items y bonificados en tablas separadas
- [ ] Bonificados: Mostrar siempre precio $0
- [ ] Bonificados: Mostrar indicador "Regalo/Bonificado"

### Fase 5: Facturas/Visualización
- [ ] Agrupar items por tipo (regular, bonificado, promo)
- [ ] Promoción: Mostrar con título "PROMOCIÓN"
- [ ] Promoción: Mostrar precio especial (no suma)
- [ ] Regalos: Mostrar con $0

### Fase 6: Testing
- [ ] Crear orden con todo (normal + S/R + promo + bonificados + flete)
- [ ] Editar orden normal: funciona
- [ ] Editar orden promo + agregar flete: NO se rompe
- [ ] Facturas: Todos los tipos se muestran correctamente

---

## 💻 CÓDIGO DE REFERENCIA

### TypeScript Types Actualizados

```typescript
// ✅ NUEVO DTO
interface BonifiedItemRequestDTO {
  productId: string;
  cantidad: number;
}

// ✅ ACTUALIZADO DTO
interface OrderItemRequestDTO {
  productId: string;
  cantidad: number;
  allowOutOfStock?: boolean;
  relatedPromotionId?: string;
  isFreightItem?: boolean;
  // ❌ REMOVIDO: isBonified
}

// ✅ ACTUALIZADO REQUEST
interface CreateOrderRequest {
  clientId: string;
  items?: OrderItemRequestDTO[];           // Regulares/Flete
  bonifiedItems?: BonifiedItemRequestDTO[]; // ✅ NUEVA SECCIÓN
  promotionIds?: string[];
  notas?: string;
  includeFreight?: boolean;
  isFreightBonified?: boolean;
  freightCustomText?: string;
  freightQuantity?: number;
}
```

### Lógica de Construcción de Payload

```typescript
const buildOrderPayload = (): CreateOrderRequest => {
  return {
    clientId: selectedClient.id,
    
    // Items regulares (sin bonificados)
    items: regularItems.map(item => ({
      productId: item.productId,
      cantidad: item.cantidad,
      allowOutOfStock: item.allowOutOfStock,
      isFreightItem: item.isFreightItem
      // ✅ NO incluir isBonified
    })),
    
    // Bonificados en sección separada
    bonifiedItems: bonifiedItems.map(item => ({
      productId: item.productId,
      cantidad: item.cantidad
      // Automáticamente precio $0 en backend
    })),
    
    promotionIds: promotionIds,
    notas: notes,
    includeFreight: includeFreight
  };
};
```

### Lógica de Edición (CRÍTICO)

```typescript
const handleEditOrder = async (orderId: string) => {
  // Detectar tipo de orden por notas
  const orderNotes = order.notas || '';
  const isPromoOrder = orderNotes.includes('[Promoción]');
  const isSROrder = orderNotes.includes('[S/R]');
  
  // Construir payload
  const payload: UpdateOrderRequest = {
    clientId: selectedClient.id,
    
    // ✅ IMPORTANTE: Si es orden Promo, items debe estar vacío
    items: isPromoOrder ? [] : regularItems,
    bonifiedItems: bonifiedItems,
    
    // ✅ IMPORTANTE: Mantener promotionIds (no dejar vacío)
    promotionIds: promotionIds,
    
    notas: notes,
    includeFreight: includeFreight
  };
  
  await api.put(`/api/admin/orders/${orderId}`, payload);
};
```

---

## 🔄 FLUJO DE DATOS

### Crear Orden Compleja (Normal + S/R + Promo + Bonificados + Flete)

```
Usuario agrega en UI:
  • 10 productos normales
  • 5 productos S/R
  • 1 Promoción
  • 3 productos bonificados
  • Habilita Flete

        ↓

Frontend separa en:
  • items: [10 normal, 5 S/R]
  • bonifiedItems: [3 bonificados]
  • promotionIds: [promo]
  • includeFreight: true

        ↓

POST /api/admin/orders

        ↓

Backend crea múltiples órdenes:
  • Orden 1 [Standard]: 10 items + regalos + promo
  • Orden 2 [S/R]: 5 items
  • (Posible Orden 3 si promo surtida)

        ↓

Frontend recibe:
  • Múltiples órdenes creadas
  • Cada una en su tabla
  • Facturas separadas
```

---

## ⚠️ ERRORES COMUNES A EVITAR

### ❌ Error 1: Enviar `isBonified` en items
```typescript
// ❌ MALO - Backend lo rechazará o lo ignorará
items: [
  {
    productId: '1',
    cantidad: 5,
    isBonified: true  // ← NO EXISTE
  }
]
```

### ❌ Error 2: Agregar items a orden de Promo
```typescript
// ❌ MALO - Duplica items en la orden
PUT /api/admin/orders/{id}
{
  items: [prod1, prod2, ...],  // ← Items extra en promo
  promotionIds: ['promo']
}
```

### ❌ Error 3: Dejar `promotionIds` vacío
```typescript
// ❌ MALO - Pierde la promoción
PUT /api/admin/orders/{id}
{
  items: [],
  promotionIds: [],  // ← Vacío, pierde promo
  bonifiedItems: []
}
```

### ✅ Correcciones

1. **Bonificados SEPARADOS**:
```typescript
// ✅ CORRECTO
bonifiedItems: [
  { productId: 'regalo1', cantidad: 3 }
]
```

2. **Orden Promo SIN items**:
```typescript
// ✅ CORRECTO
if (isPromoOrder) {
  items: []  // Vacío
}
```

3. **Mantener Promos**:
```typescript
// ✅ CORRECTO
promotionIds: ['promo1', 'promo2']  // Siempre incluir
```

---

## 📊 IMPACT MATRIX

| Componente | Cambio | Impacto | Esfuerzo |
|-----------|--------|--------|----------|
| DTOs | Nuevo `bonifiedItems` | Alto | Bajo |
| Formulario Crear | Nueva sección | Alto | Medio |
| Formulario Editar | Lógica de promo | Crítico | Medio |
| Validación | Items vs bonificados | Medio | Bajo |
| Tablas | Agrupar por tipo | Bajo | Bajo |
| Facturas | Agregar promoción | Medio | Medio |

---

## 🚀 ORDEN RECOMENDADO DE IMPLEMENTACIÓN

1. **Día 1**: DTOs + Validación
2. **Día 2**: Formulario Crear (agregar sección bonificados)
3. **Día 3**: Formulario Editar (lógica de promo)
4. **Día 4**: Tablas y Visualización
5. **Día 5**: Testing

---

## ✅ DEFINICIÓN DE "LISTO"

- [ ] Crear orden normal funciona
- [ ] Crear orden con bonificados funciona
- [ ] Crear orden con promo + flete funciona
- [ ] Editar orden normal funciona
- [ ] Editar orden promo funciona (NO se rompe)
- [ ] Facturas muestran correctamente
- [ ] Testing completo pasa

---

## 📞 SOPORTE

**Para dudas técnicas consultar**:
- `PROMPT_DETALLADO_FRONTEND.md` - Documentación completa
- `GUIA_FRONTEND_BONIFICADOS.md` - Implementación específica
- `TESTING_CASOS_REPORTADOS.md` - Casos de prueba

**Todos los endpoints ya están funcionando. El backend está listo.**

---

**¡A trabajar!** 💪


