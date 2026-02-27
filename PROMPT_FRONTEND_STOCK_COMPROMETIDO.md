# CAMBIOS BACKEND - INVENTARIO CON CONTEXTO DE PEDIDOS + FIX NOMBRE CLIENTE

---

## 1. FIX: Nombre del cliente en factura PDF ✅

**Problema:** Al generar la factura PDF, el nombre del cliente no aparecía.
**Estado:** Corregido. Todas las facturas nuevas muestran correctamente: nombre, NIT, teléfono, email, dirección y representante legal.

---

## 2. Cómo funciona el inventario en este sistema (IMPORTANTE leer)

### El flujo actual (NO cambiado):
El sistema descuenta el stock **al momento de crear el pedido**, no al despacharlo.

Esto significa:
- `stockEnBD` (lo que muestra la BD) = stock ya descontado por todos los pedidos creados
- Si hay pedidos pendientes de despacho, el stock puede verse negativo o muy bajo

### Por qué no cambiamos este flujo:
Cambiar cuándo se descuenta el stock requeriría refactorizar decenas de lugares en el código (edición, anulación, cancelación, promociones, bonificados, flete) y rompería funcionalidades que ya están funcionando correctamente.

### Lo que SÍ se implementó:
Se agregaron **2 endpoints nuevos** que muestran el inventario con contexto completo para que el dueño entienda exactamente qué está pasando.

---

## Los 3 números del inventario

| Campo | Significado | Ejemplo |
|---|---|---|
| `stockEnBD` | Lo que dice el sistema (ya descontó pedidos activos) | `-14` |
| `stockComprometido` | Unidades en pedidos activos pendientes de despacho | `18` |
| `stockFisicoReal` | `stockEnBD + stockComprometido` = lo que hay EN BODEGA ahora mismo | `4` |

### Ejemplo del caso real reportado:
- Sistema muestra: **-14**
- Pedidos pendientes tienen: **18 unidades** comprometidas
- En bodega físicamente hay: **-14 + 18 = 4 unidades** ← eso es lo real

---

## NUEVOS ENDPOINTS

### A. Reporte completo de inventario con contexto

```
GET /api/admin/products/inventory/stock-report
GET /api/owner/products/inventory/stock-report
```

**Requiere:** Token JWT con rol `ADMIN` o `OWNER`

**Respuesta:**
```json
[
  {
    "productId": "uuid-del-producto",
    "nombre": "Shampoo Vitalexa 500ml",
    "stockEnBD": -14,
    "stockComprometido": 18,
    "stockFisicoReal": 4,
    "alertaCritica": true,
    "tieneStockComprometido": true
  },
  {
    "productId": "uuid-del-producto",
    "nombre": "Acondicionador 200ml",
    "stockEnBD": 20,
    "stockComprometido": 5,
    "stockFisicoReal": 25,
    "alertaCritica": false,
    "tieneStockComprometido": true
  },
  {
    "productId": "uuid-del-producto",
    "nombre": "Crema 100ml",
    "stockEnBD": 10,
    "stockComprometido": 0,
    "stockFisicoReal": 10,
    "alertaCritica": false,
    "tieneStockComprometido": false
  }
]
```

**Campos del objeto:**
- `alertaCritica: true` → `stockEnBD < 0` (hay más pedidos que stock registrado)
- `tieneStockComprometido: true` → hay pedidos activos con este producto

---

### B. Solo alertas críticas

```
GET /api/admin/products/inventory/stock-alerts
GET /api/owner/products/inventory/stock-alerts
```

Mismo formato, **pero solo productos donde `stockEnBD < 0`** (stock negativo en sistema).
Ideal para badge de alerta en dashboard.

---

## CÓMO MOSTRARLO EN EL FRONTEND

### Tabla de inventario recomendada

```
| Producto       | En Bodega | En Pedidos | Sistema  |
|----------------|-----------|------------|----------|
| Shampoo 500ml  |     4     |     18     |   -14 🔴 |
| Acondicionador |    25     |      5     |    20 ✅ |
| Crema 100ml    |    10     |      0     |    10 ✅ |
```

**Nombres de columnas sugeridos:**
- **"En Bodega"** → `stockFisicoReal` (lo que hay físicamente)
- **"En Pedidos"** → `stockComprometido` (comprometido en pedidos activos)
- **"Sistema"** → `stockEnBD` (lo que muestra el sistema actualmente)

**Reglas de color para la columna "Sistema":**
- `stockEnBD < 0` → 🔴 Rojo
- `stockEnBD === 0` → 🟡 Amarillo
- `stockEnBD > 0` → ✅ Verde

### Badge de alerta en dashboard del dueño

```javascript
// Al cargar el panel del dueño
const alerts = await GET('/api/owner/products/inventory/stock-alerts');
// Mostrar: "⚠️ X productos con inventario negativo"
```

### Tooltip / explicación para el usuario

> **En Bodega:** Unidades físicas reales actualmente en su almacén.
>
> **En Pedidos:** Unidades comprometidas en pedidos que aún no han sido despachados.
>
> **Sistema:** Lo que el sistema tiene registrado (ya descontó los pedidos activos).
> Si está en rojo, significa que hay más pedidos activos que unidades en el sistema — necesita revisar y posiblemente reponer mercancía.

---

## Cuándo se despeja el stock comprometido

El `stockComprometido` de un producto **se reduce** automáticamente cuando:
1. El pedido se marca como **COMPLETADO** (despachado)
2. El pedido se **CANCELA**
3. El pedido se **ANULA**

En esos casos el sistema ya restaura el stock (si se cancela) o lo confirma como vendido (si se completa).

---

## Qué endpoints de inventario existen ahora

| Endpoint | Descripción |
|---|---|
| `GET /api/admin/products` | Lista todos los productos con `stockEnBD` |
| `GET /api/admin/products/low-stock?threshold=10` | Productos con `stockEnBD` bajo |
| `GET /api/admin/products/inventory/export` | Excel de inventario |
| `GET /api/admin/products/inventory/export/pdf` | PDF de inventario |
| `GET /api/admin/products/inventory/stock-report` | ⭐ NUEVO: Reporte con contexto de pedidos |
| `GET /api/admin/products/inventory/stock-alerts` | ⭐ NUEVO: Solo productos con stock negativo |

(Los mismos endpoints existen en `/api/owner/products/inventory/...`)

