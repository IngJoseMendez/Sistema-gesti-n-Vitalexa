# PROMPT FRONTEND – Mejoras en Órdenes de Promoción

## Contexto
Se realizaron dos mejoras en el backend relacionadas con órdenes de promociones. El frontend debe adaptarse para aprovecharlas correctamente.

---

## CAMBIO 1 – Factura PDF ahora agrupa promociones iguales

### Qué cambió
Antes: si una orden tenía 10 instancias de la misma promoción "Pack A", la factura mostraba 10 filas separadas con el encabezado azul "PROMOCIÓN: Pack A".

Ahora: todas las instancias de la misma promoción se muestran en **una sola fila** con la cantidad de instancias y el precio total:

```
[Encabezado azul]  PROMOCIÓN: Pack A
[Fila de datos]    (vacío) | x10 | $50,000 | $500,000
```

### Lo que el frontend NO necesita cambiar
- Los endpoints de factura PDF/preview siguen siendo los mismos:
  - `GET /api/admin/orders/{id}/invoice/pdf`
  - `GET /api/admin/orders/{id}/invoice/preview`
- No hay cambios en los DTOs de respuesta de la orden.

### Lo que sí puede mejorar en frontend (opcional)
- En la vista de detalle de una orden, si muestras los items agrupados, podrías hacer lo mismo: agrupar por `promotion.id` (o `specialPromotion.id`) y mostrar `x{cantidad de instancias}` en lugar de repetir filas. Esto es **opcional** y solo mejora la UI.

---

## CAMBIO 2 – Nuevo endpoint para agregar promociones a órdenes de tipo promoción

### Problema que resuelve
Las órdenes de tipo "[Promoción]" no podían editarse con el `PUT /api/admin/orders/{id}` porque el backend bloqueaba los cambios (solo preservaba las promociones existentes, no permitía agregar nuevas). Ahora hay un endpoint dedicado para **añadir** promociones sin tocar las existentes.

### Nuevo endpoint

```
POST /api/admin/orders/{orderId}/promotions/add
```

**Autenticación:** Requiere `ROLE_ADMIN` o `ROLE_OWNER`

**Body (JSON):**
```json
[
  "uuid-de-promocion-1",
  "uuid-de-promocion-2",
  "uuid-de-promocion-2"
]
```
> Nota: se puede repetir el mismo UUID para agregar **múltiples instancias** de la misma promoción (ej: si el cliente quiere 3 veces "Pack A", enviar el UUID 3 veces).

**Respuesta exitosa (200 OK):**
Devuelve el `OrderResponse` completo actualizado con las nuevas promociones añadidas.

**Respuestas de error posibles:**
| Código | Motivo |
|--------|--------|
| 400 | La lista de promociones está vacía |
| 400 | La orden no es de tipo promoción |
| 400 | La orden está en estado COMPLETADO, CANCELADO o ANULADO |
| 404 | Orden no encontrada |
| 404 | Alguna de las promociones no existe |
| 400 | Alguna promoción no está vigente o no está activa |

### Flujo recomendado en la UI de gestión de órdenes (panel admin)

1. El admin abre el **detalle de una orden** que esté marcada como `[Promoción]` y en estado `PENDIENTE`, `CONFIRMADO` o `EN_PROCESO`.
2. Se muestra un botón o sección **"Agregar promociones"** (visible solo si la orden es de tipo promoción y no está completada/anulada).
3. Al hacer clic, se abre un selector de promociones disponibles (las activas y vigentes). El admin puede seleccionar una o más, incluyendo cantidades.
4. Al confirmar, se llama:
   ```
   POST /api/admin/orders/{orderId}/promotions/add
   Body: [ id1, id2, id2, id2 ]  // repetir id para múltiples instancias
   ```
5. Se refresca el detalle de la orden con la respuesta devuelta.

### Cómo detectar si una orden es "de promoción" en el frontend

La respuesta del `OrderResponse` incluye:
- Campo `notas` que contiene el texto `[Promoción]`
- O bien, alguno de los `items` tiene `isPromotionItem: true`

Condición sugerida:
```js
const isPromoOrder = 
  order.notas?.includes('[Promoción]') || 
  order.items?.some(item => item.isPromotionItem);
```

### Promociones disponibles para el selector

Usa el endpoint existente para listar promociones activas:
```
GET /api/admin/promotions          → promociones normales activas
GET /api/admin/special-promotions  → promociones especiales activas
```
Filtra por `active: true` y que la fecha actual esté dentro de `validFrom`/`validUntil`.

### Cómo construir la lista de IDs con cantidades

Si el admin selecciona "Pack A x3" y "Pack B x1":
```js
const promotionIds = [
  ...Array(3).fill(packAId),
  packBId
];
// resultado: [packAId, packAId, packAId, packBId]
```

### ✅ Soporte completo de duplicados — tanto al crear como al agregar

**Al crear una orden** (`POST /api/admin/orders` o `POST /api/vendedor/orders`):
- Puedes enviar el mismo UUID varias veces en `promotionIds`.
- Cada ocurrencia del UUID genera una **instancia independiente** de la promoción (con su propio `promotionInstanceId` y `promotionGroupIndex` incremental).
- El stock se descuenta tantas veces como instancias se creen.

**Al agregar a una orden existente** (`POST /api/admin/orders/{id}/promotions/add`):
- Puedes enviar un UUID que ya está en la orden → se agrega como instancia nueva **sin eliminar las existentes**.
- Puedes repetir el mismo UUID varias veces en una sola llamada.
- Ejemplo: orden tiene "Pack A x2"; si envías `[packAId, packAId, packAId]` → queda "Pack A x5".

**En la factura PDF** el resultado siempre se muestra agrupado:
```
PROMOCIÓN: Pack A
(vacío) | x5 | $50,000 | $250,000
```

---

## Resumen de cambios en la API

| # | Tipo | Endpoint | Cambio |
|---|------|----------|--------|
| 1 | PDF  | `GET /api/admin/orders/{id}/invoice/pdf` | Sin cambios en endpoint; la factura ahora agrupa promos iguales |
| 2 | NUEVO | `POST /api/admin/orders/{id}/promotions/add` | Permite agregar promociones a órdenes existentes de tipo promo |

---

## Lo que NO cambió

- El `PUT /api/admin/orders/{id}` sigue funcionando igual para órdenes normales y S/R.
- El `DELETE /api/admin/orders/{orderId}/items/{itemId}` sigue permitiendo eliminar items individuales de promoción.
- Los DTOs `OrderResponse` y `OrderItemResponse` son los mismos, sin campos nuevos.
- La lógica de stock sigue siendo la misma (se descuenta al agregar, se restaura al eliminar).
- **La cartera del cliente no se afecta** hasta que la orden se ponga en `COMPLETADO`. El admin puede agregar/quitar promociones libremente mientras la orden esté pendiente, y cuando se complete, el total final (ya con todas las promos) es el que entra al cálculo de saldo.

