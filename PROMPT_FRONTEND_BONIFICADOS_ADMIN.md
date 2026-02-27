# 🎁 ACTUALIZACIÓN FRONTEND: Soporte Completo para Bonificados y Correcciones de Inventario

## 📋 RESUMEN DE CAMBIOS EN BACKEND (ACTUALIZADO)

Se ha actualizado completamente el backend para manejar correctamente el inventario en **TODOS** los tipos de órdenes, incluyendo:
- Órdenes solo con bonificados
- Órdenes con flete
- Órdenes mixtas (normales + bonificados + flete)
- Edición de órdenes preservando items
- Anulación de órdenes con restauración completa

### ✅ Cambios Implementados (13/02/2026)

1. **Validación actualizada** en `OrderServiceImpl.createOrder()`:
   - Ahora acepta: `items` O `promotionIds` O `bonifiedItems`

2. **Validación actualizada** en `OrderServiceImpl.updateOrder()`:
   - Ahora acepta: `items` O `promotionIds` O `bonifiedItems`

3. **Stock negativo en bonificados:**
   - Los bonificados ahora permiten stock negativo (igual que productos normales)
   - NO mostrarán más "Pendiente: X"

4. **Stock negativo en flete:**
   - Los items de flete ahora permiten stock negativo
   - NO mostrarán más "Pendiente: X"

5. **Edición mejorada:**
   - NO restaura stock de items preservados (promociones, flete sin cambios)
   - SÍ restaura stock de bonificados que se eliminan o modifican

6. **Anulación completa:**
   - Restaura stock de TODOS los items incluyendo flete
   - **CRÍTICO:** Antes el flete NO se restauraba (ahora sí)

### 🎯 COMPORTAMIENTO ESPERADO

**Stock Negativo Permitido:**
- ✅ Productos normales
- ✅ Productos S/R
- ✅ Productos bonificados
- ✅ Items de flete
- ✅ Productos de promoción
- ✅ Regalos de promoción

**Ya NO verás:**
- ❌ "Pendiente: 5" en bonificados
- ❌ "Pendiente: 3" en flete
- ❌ Descuadres de inventario al editar
- ❌ Falta de restauración al anular

---

## 🔧 CAMBIOS NECESARIOS EN FRONTEND

### COMPONENTE 1: Nueva Venta (AdminDashboard.js)

#### 1. **Verificar Validación del Formulario**

En tu componente de **Nueva Venta (AdminDashboard.js o similar)**, asegúrate de que la validación permita enviar órdenes solo con bonificados:

**Busca código similar a:**
```javascript
// ❌ ANTES - Validación que impide solo bonificados
if (items.length === 0 && promotionIds.length === 0) {
  setError('Debe agregar al menos un producto o promoción');
  return;
}
```

**Actualiza a:**
```javascript
// ✅ DESPUÉS - Permite solo bonificados
if (items.length === 0 && promotionIds.length === 0 && bonifiedItems.length === 0) {
  setError('Debe agregar al menos un producto, promoción o bonificado');
  return;
}
```

---

#### 2. **Asegurar Estructura Correcta del Payload**

El payload que se envía a `/api/admin/orders` debe tener esta estructura:

```javascript
const orderPayload = {
  clientId: selectedClient.id,
  items: normalItems,           // Productos normales (puede estar vacío)
  bonifiedItems: bonifiedItems,  // Productos bonificados (puede ser el único con datos)
  promotionIds: selectedPromotions, // Promociones (puede estar vacío)
  notas: notas,
  includeFreight: includeFreight,
  isFreightBonified: isFreightBonified,
  freightCustomText: freightCustomText,
  freightQuantity: freightQuantity,
  sellerId: sellerId  // Si aplica
};
```

**IMPORTANTE:** Los bonificados deben ir en el campo `bonifiedItems`, NO en `items`.

---

#### 3. **Visualización de Stock (IMPORTANTE)**

**Cambio de Comportamiento:**

El backend ahora permite stock negativo para **todos** los tipos de productos. Esto significa:

```javascript
// ANTES (comportamiento antiguo):
// Si producto tiene stock 5 y pides 10:
// - Mostraba: "Stock disponible: 5, Pendiente: 5"
// - Creaba 2 líneas en la orden

// DESPUÉS (comportamiento actual):
// Si producto tiene stock 5 y pides 10:
// - Stock queda en: -5
// - Mostraba: 1 línea con cantidad 10
// - Backend permite esto para TODOS los productos
```

**Actualización Recomendada en UI:**

```javascript
// En el componente de selección de productos
const ProductSelector = ({ product, onAdd }) => {
  const [quantity, setQuantity] = useState(1);
  
  return (
    <div className="product-item">
      <span>{product.nombre}</span>
      <span className={product.stock < 0 ? 'text-danger' : 'text-muted'}>
        Stock: {product.stock}
        {product.stock < 0 && ' (NEGATIVO)'}
        {product.stock === 0 && ' (AGOTADO)'}
      </span>
      <input 
        type="number" 
        value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        min="1"
      />
      <button onClick={() => onAdd(product, quantity)}>
        Agregar
      </button>
      {product.stock < quantity && (
        <small className="text-warning">
          ⚠️ Stock insuficiente. El producto quedará en stock negativo.
        </small>
      )}
    </div>
  );
};
```

**NO bloquees la creación de órdenes por stock insuficiente** - El backend lo maneja automáticamente.

---

#### 4. **Formato de Bonificados**

Cada item bonificado debe tener esta estructura:

```javascript
{
  productId: "uuid-del-producto",      // ID del producto a bonificar
  cantidad: 5,                         // Cantidad a regalar
  specialProductId: null               // Opcional: si es producto especial
}
```

**Ejemplo completo:**
```javascript
const bonifiedItems = [
  {
    productId: "550e8400-e29b-41d4-a716-446655440001",
    cantidad: 10,
    specialProductId: null
  },
  {
    productId: "550e8400-e29b-41d4-a716-446655440002",
    cantidad: 5,
    specialProductId: null
  }
];
```

---

#### 4. **Formato de Bonificados**

Cada item bonificado debe tener esta estructura:

```javascript
{
  productId: "uuid-del-producto",      // ID del producto a bonificar
  cantidad: 5,                         // Cantidad a regalar
  specialProductId: null               // Opcional: si es producto especial
}
```

**Ejemplo completo:**
```javascript
const bonifiedItems = [
  {
    productId: "550e8400-e29b-41d4-a716-446655440001",
    cantidad: 10,
    specialProductId: null
  },
  {
    productId: "550e8400-e29b-41d4-a716-446655440002",
    cantidad: 5,
    specialProductId: null
  }
];
```

---

#### 5. **Interfaz de Usuario - Sección de Bonificados**

Asegúrate de tener una sección visible para agregar productos bonificados:

```jsx
{/* Sección de Productos Bonificados */}
<div className="bonified-section">
  <h3>🎁 Productos Bonificados/Regalados</h3>
  <p className="text-muted">Los productos bonificados tienen precio $0 y se descuentan del inventario</p>
  
  {/* Selector de producto */}
  <select 
    value={selectedBonifiedProduct} 
    onChange={(e) => setSelectedBonifiedProduct(e.target.value)}
  >
    <option value="">Seleccionar producto...</option>
    {products.map(p => (
      <option key={p.id} value={p.id}>
        {p.nombre} - Stock: {p.stock}
      </option>
    ))}
  </select>

  {/* Campo de cantidad */}
  <input 
    type="number" 
    min="1"
    placeholder="Cantidad"
    value={bonifiedQuantity}
    onChange={(e) => setBonifiedQuantity(e.target.value)}
  />

  {/* Botón para agregar */}
  <button onClick={handleAddBonified}>
    Agregar Bonificado
  </button>

  {/* Lista de bonificados agregados */}
  {bonifiedItems.length > 0 && (
    <div className="bonified-list">
      <h4>Bonificados en esta orden:</h4>
      {bonifiedItems.map((item, index) => (
        <div key={index} className="bonified-item">
          <span>{item.productName} x {item.cantidad}</span>
          <button onClick={() => removeBonified(index)}>Quitar</button>
        </div>
      ))}
    </div>
  )}
</div>
```

---

#### 5. **Handlers de Bonificados**

```javascript
// Estado para bonificados
const [bonifiedItems, setBonifiedItems] = useState([]);
const [selectedBonifiedProduct, setSelectedBonifiedProduct] = useState('');
const [bonifiedQuantity, setBonifiedQuantity] = useState(1);

// Agregar bonificado
const handleAddBonified = () => {
  if (!selectedBonifiedProduct || bonifiedQuantity < 1) {
    alert('Seleccione un producto y cantidad válida');
    return;
  }

  const product = products.find(p => p.id === selectedBonifiedProduct);
  
  const newBonified = {
    productId: product.id,
    cantidad: parseInt(bonifiedQuantity),
    specialProductId: null,
    productName: product.nombre  // Para mostrar en UI
  };

  setBonifiedItems([...bonifiedItems, newBonified]);
  setSelectedBonifiedProduct('');
  setBonifiedQuantity(1);
};

// Quitar bonificado
const removeBonified = (index) => {
  setBonifiedItems(bonifiedItems.filter((_, i) => i !== index));
};

// Al enviar orden
const handleSubmitOrder = async () => {
  // Validación
  if (items.length === 0 && promotionIds.length === 0 && bonifiedItems.length === 0) {
    setError('Debe agregar al menos un producto, promoción o bonificado');
    return;
  }

  // Preparar bonificados (quitar campos de UI)
  const bonifiedItemsToSend = bonifiedItems.map(({ productName, ...rest }) => rest);

  const orderPayload = {
    clientId: selectedClient.id,
    items: items,
    bonifiedItems: bonifiedItemsToSend,
    promotionIds: promotionIds,
    notas: notas,
    includeFreight: includeFreight,
    isFreightBonified: isFreightBonified,
    freightCustomText: freightCustomText,
    freightQuantity: freightQuantity,
    sellerId: sellerId
  };

  try {
    const response = await axios.post('/api/admin/orders', orderPayload);
    console.log('Orden creada exitosamente:', response.data);
    // Limpiar formulario y mostrar éxito
  } catch (error) {
    console.error('Error al crear orden:', error);
    setError(error.response?.data?.message || 'Error al crear la orden');
  }
};
```

---

#### 6. **Limpiar Formulario Después de Crear Orden**

```javascript
const resetForm = () => {
  setItems([]);
  setBonifiedItems([]);
  setPromotionIds([]);
  setSelectedClient(null);
  setNotas('');
  setIncludeFreight(false);
  setIsFreightBonified(false);
  setFreightCustomText('');
  setFreightQuantity(1);
};
```

---

### COMPONENTE 2: Editar Orden (EditOrderModal.js)

#### 1. **Validación en Formulario de Edición**

Al igual que en Nueva Venta, la validación debe permitir órdenes solo con bonificados:

```javascript
// ❌ ANTES - Validación que impide solo bonificados
if (editedItems.length === 0 && promotionIds.length === 0) {
  setError('Debe tener al menos un producto o promoción');
  return;
}
```

**Actualiza a:**
```javascript
// ✅ DESPUÉS - Permite solo bonificados
if (editedItems.length === 0 && promotionIds.length === 0 && bonifiedItems.length === 0) {
  setError('Debe tener al menos un producto, promoción o bonificado');
  return;
}
```

---

#### 2. **Cargar Bonificados Existentes al Abrir Modal**

Cuando se abre el modal de edición, debes cargar los bonificados existentes:

```javascript
useEffect(() => {
  if (order && isOpen) {
    // Cargar items normales
    const normalItems = order.items
      .filter(item => !item.isBonified && !item.isPromotionItem && !item.isFreightItem)
      .map(item => ({
        productId: item.productId,
        cantidad: item.cantidad,
        // ... otros campos
      }));
    setEditedItems(normalItems);

    // Cargar bonificados
    const bonifiedItems = order.items
      .filter(item => item.isBonified)
      .map(item => ({
        productId: item.productId,
        cantidad: item.cantidad,
        specialProductId: item.specialProductId || null,
        productName: item.productName  // Para mostrar en UI
      }));
    setBonifiedItems(bonifiedItems);

    // Cargar promociones
    const promoIds = order.items
      .filter(item => item.isPromotionItem && item.promotionId)
      .map(item => item.promotionId);
    setPromotionIds([...new Set(promoIds)]);  // Eliminar duplicados
  }
}, [order, isOpen]);
```

---

#### 3. **Interfaz de Usuario para Editar Bonificados**

Agregar sección en el modal de edición:

```jsx
{/* Sección de Bonificados en Modal de Edición */}
<div className="edit-bonified-section">
  <h4>🎁 Productos Bonificados</h4>
  
  {/* Mostrar bonificados actuales */}
  {bonifiedItems.length > 0 && (
    <div className="current-bonified-list">
      <h5>Bonificados actuales:</h5>
      {bonifiedItems.map((item, index) => (
        <div key={index} className="bonified-item-row">
          <span>{item.productName}</span>
          <input 
            type="number" 
            min="1"
            value={item.cantidad}
            onChange={(e) => {
              const updated = [...bonifiedItems];
              updated[index].cantidad = parseInt(e.target.value) || 1;
              setBonifiedItems(updated);
            }}
          />
          <button 
            className="btn-remove"
            onClick={() => {
              setBonifiedItems(bonifiedItems.filter((_, i) => i !== index));
            }}
          >
            Quitar
          </button>
        </div>
      ))}
    </div>
  )}

  {/* Agregar nuevos bonificados */}
  <div className="add-bonified">
    <h5>Agregar bonificado:</h5>
    <select 
      value={selectedBonifiedProduct} 
      onChange={(e) => setSelectedBonifiedProduct(e.target.value)}
    >
      <option value="">Seleccionar producto...</option>
      {availableProducts.map(p => (
        <option key={p.id} value={p.id}>
          {p.nombre} - Stock: {p.stock}
        </option>
      ))}
    </select>

    <input 
      type="number" 
      min="1"
      placeholder="Cantidad"
      value={newBonifiedQuantity}
      onChange={(e) => setNewBonifiedQuantity(e.target.value)}
    />

    <button onClick={handleAddBonifiedToEdit}>
      Agregar Bonificado
    </button>
  </div>
</div>
```

---

#### 4. **Handler para Actualizar Orden con Bonificados**

```javascript
const handleSubmit = async () => {
  // Validación
  if (editedItems.length === 0 && promotionIds.length === 0 && bonifiedItems.length === 0) {
    setError('Debe tener al menos un producto, promoción o bonificado');
    return;
  }

  // Preparar items normales (sin campos de UI)
  const itemsToSend = editedItems.map(({ productName, ...rest }) => rest);

  // Preparar bonificados (sin campos de UI)
  const bonifiedToSend = bonifiedItems.map(({ productName, ...rest }) => rest);

  // Construir payload
  const updatePayload = {
    clientId: order.clientId,
    items: itemsToSend,
    bonifiedItems: bonifiedToSend,
    promotionIds: promotionIds,
    notas: editedNotas,
    includeFreight: order.includeFreight,
    isFreightBonified: order.isFreightBonified,
    freightCustomText: order.freightCustomText,
    freightQuantity: order.freightQuantity
  };

  try {
    const response = await axios.put(`/api/admin/orders/${order.id}`, updatePayload);
    console.log('Orden actualizada exitosamente:', response.data);
    onSuccess();  // Cerrar modal y refrescar lista
  } catch (error) {
    console.error('Error al actualizar orden:', error);
    setError(error.response?.data?.message || 'Error al actualizar la orden');
  }
};
```

---

#### 5. **Estado Inicial del Modal**

Agregar estados para bonificados en el modal:

```javascript
const [bonifiedItems, setBonifiedItems] = useState([]);
const [selectedBonifiedProduct, setSelectedBonifiedProduct] = useState('');
const [newBonifiedQuantity, setNewBonifiedQuantity] = useState(1);

// Handler para agregar bonificado
const handleAddBonifiedToEdit = () => {
  if (!selectedBonifiedProduct || newBonifiedQuantity < 1) {
    alert('Seleccione un producto y cantidad válida');
    return;
  }

  const product = availableProducts.find(p => p.id === selectedBonifiedProduct);
  
  setBonifiedItems([
    ...bonifiedItems,
    {
      productId: product.id,
      cantidad: parseInt(newBonifiedQuantity),
      specialProductId: null,
      productName: product.nombre
    }
  ]);

  setSelectedBonifiedProduct('');
  setNewBonifiedQuantity(1);
};
```

---

## 🧪 PRUEBAS NECESARIAS

### Test 1: CREAR Orden Solo con Bonificados
```
1. Ir a Panel Admin > Nueva Venta
2. Seleccionar un cliente
3. NO agregar productos normales
4. NO seleccionar promociones
5. Agregar solo bonificados (ej: Producto X x10)
6. Hacer clic en "Crear Orden"
7. ✅ RESULTADO ESPERADO:
   - Orden se crea exitosamente (Status 201)
   - Bonificados aparecen con precio $0
   - Stock se descuenta correctamente
   - Factura muestra "BONIFICADO" en los items
```

### Test 2: EDITAR Orden Solo con Bonificados
```
1. Crear una orden con solo bonificados (Test 1)
2. Ir a Panel Admin > Órdenes
3. Hacer clic en "Editar" en la orden creada
4. Modificar la cantidad de bonificados
5. Agregar un nuevo bonificado
6. Quitar un bonificado existente
7. Hacer clic en "Guardar Cambios"
8. ✅ RESULTADO ESPERADO:
   - Orden se actualiza exitosamente (Status 200)
   - Cambios se reflejan correctamente
   - Stock se ajusta según las modificaciones
   - Factura actualizada refleja los cambios
```

### Test 3: CONVERTIR Orden Normal a Solo Bonificados
```
1. Crear una orden con productos normales
2. Editar la orden
3. Eliminar todos los productos normales
4. Agregar bonificados
5. Guardar cambios
6. ✅ RESULTADO ESPERADO:
   - Orden se actualiza sin errores
   - Ahora solo tiene bonificados
   - Total de orden = $0
   - Stock restaurado de productos eliminados
   - Stock descontado de bonificados nuevos
```

### Test 4: CONVERTIR Orden de Bonificados a Normal
```
1. Crear una orden con solo bonificados
2. Editar la orden
3. Agregar productos normales
4. Opcionalmente: eliminar algunos bonificados
5. Guardar cambios
6. ✅ RESULTADO ESPERADO:
   - Orden se actualiza correctamente
   - Total = suma de productos normales
   - Bonificados siguen con precio $0
   - Stock ajustado correctamente

```

### Test 5: Orden con Productos Normales + Bonificados
```
1. Agregar productos normales (ej: Producto A x5)
2. Agregar bonificados (ej: Producto B x3)
3. Crear orden
4. ✅ RESULTADO ESPERADO:
   - Orden tiene ambos tipos de productos
   - Productos normales con precio real
   - Bonificados con precio $0
   - Total = suma solo de productos normales
```

### Test 6: Orden con Promoción + Bonificados
```
1. Seleccionar una promoción
2. Agregar bonificados adicionales
3. Crear orden
4. ✅ RESULTADO ESPERADO:
   - Promoción se aplica correctamente
   - Bonificados se agregan aparte
   - Factura muestra ambos claramente
```

---

## 📝 NOTAS IMPORTANTES

1. **Stock:** Los bonificados SÍ descuentan del inventario, solo que tienen precio $0.

2. **Permisos:** Solo `ADMIN` y `OWNER` pueden crear órdenes con bonificados.

3. **Validación de stock:** Los bonificados pueden ignorar validación de stock si se incluye `allowOutOfStock: true` en el item.

4. **Factura:** Los bonificados se marcan como "BONIFICADO" en la factura PDF.

5. **Total de orden:** Los bonificados NO suman al total de la orden (precio $0).

---

## 🐛 TROUBLESHOOTING

### Error 400: "La venta debe tener al menos un producto o una promoción"
- **Causa:** Frontend no está enviando `bonifiedItems` o está vacío
- **Solución:** Verificar que `bonifiedItems` se incluya en el payload

### Bonificados no aparecen en la factura
- **Causa:** Backend los está procesando pero frontend no muestra la respuesta
- **Solución:** Verificar el componente de visualización de órdenes

### Stock no se descuenta
- **Causa:** Campos incorrectos en bonifiedItems
- **Solución:** Asegurar que cada item tiene `productId` y `cantidad`

---

## 📚 REFERENCIAS

- **Endpoint:** `POST /api/admin/orders`
- **DTOs:** `OrderRequestDto`, `BonifiedItemRequestDTO`
- **Servicio:** `OrderServiceImpl.java`
- **Documentación relacionada:** 
  - `GUIA_FRONTEND_BONIFICADOS.md`
  - `CORRECCION_5_BONIFICADOS_FLETE_PROMOS.md`

---

## ✅ CHECKLIST DE IMPLEMENTACIÓN

### Nueva Venta (AdminDashboard.js)
- [ ] Actualizar validación del formulario para permitir solo bonificados
- [ ] Agregar sección UI para bonificados
- [ ] Implementar handlers para agregar/quitar bonificados
- [ ] Actualizar payload para incluir `bonifiedItems`
- [ ] Implementar función de limpiar formulario

### Editar Orden (EditOrderModal.js)
- [ ] Actualizar validación del formulario de edición para permitir solo bonificados
- [ ] Agregar sección UI para gestionar bonificados existentes
- [ ] Implementar carga de bonificados existentes al abrir modal
- [ ] Implementar handlers para agregar/modificar/quitar bonificados en edición
- [ ] Actualizar payload de edición para incluir `bonifiedItems`

### Pruebas - Crear
- [ ] Probar crear orden solo con bonificados
- [ ] Probar crear orden mixta (normales + bonificados)
- [ ] Probar crear orden con promoción + bonificados

### Pruebas - Editar
- [ ] Probar editar orden solo con bonificados
- [ ] Probar convertir orden normal a solo bonificados
- [ ] Probar convertir orden de bonificados a normal
- [ ] Probar modificar cantidades de bonificados existentes
- [ ] Probar agregar/quitar bonificados en edición

### Validación General
- [ ] Verificar que la factura muestre correctamente los bonificados
- [ ] Verificar que el stock se descuente correctamente en creación
- [ ] Verificar que el stock se ajuste correctamente en edición
- [ ] Verificar que el total de la orden sea correcto ($0 para solo bonificados)

---

**Fecha de actualización:** 2026-02-13  
**Versión del backend:** Compatible con sistema actual  
**Requiere recompilación:** ✅ Backend ya compilado  

