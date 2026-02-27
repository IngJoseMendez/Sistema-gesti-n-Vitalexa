# 🚀 OPTIMIZACIÓN FRONTEND: Carga con Internet Lento

## Problema resuelto
Las vendedoras reportaban que al abrir la página **las promociones no cargaban** y no podían hacer pedidos cuando tenían mala señal de internet. El problema era que el frontend hacía **3 peticiones HTTP separadas** al iniciar, y con internet lento cualquiera de ellas fallaba o tardaba demasiado.

---

## ✅ Cambio principal: usar el endpoint unificado `/api/vendedor/init`

### Nuevo endpoint disponible en el backend:
```
GET /api/vendedor/init
Authorization: Bearer <token>
```

### Respuesta JSON:
```json
{
  "productos": [
    {
      "id": "uuid",
      "nombre": "Producto A",
      "descripcion": "...",
      "precio": 12500.00,
      "stock": 50,
      "imageUrl": "https://...",
      "active": true,
      "reorderPoint": 5,
      "tagId": "uuid-o-null",
      "tagName": "Categoría",
      "linkedSpecialCount": 0,
      "isSpecialProduct": false,
      "specialProductId": null
    }
    // ... más productos (incluye productos especiales del vendedor)
  ],
  "promociones": [
    {
      "id": "uuid",
      "nombre": "Promo 3x2",
      "descripcion": "...",
      "type": "BUY_GET_FREE",
      "buyQuantity": 3,
      "packPrice": null,
      "mainProduct": { /* ProductResponse */ },
      "giftItems": [
        { "id": "uuid", "product": { /* ProductResponse */ }, "quantity": 1 }
      ],
      "allowStackWithDiscounts": false,
      "requiresAssortmentSelection": true,
      "active": true,
      "validFrom": "2025-01-01T00:00:00",
      "validUntil": "2025-12-31T23:59:59",
      "createdAt": "2025-01-01T00:00:00",
      "isValid": true
    }
    // ... más promociones
  ],
  "promocionesEspeciales": [
    {
      "id": "uuid",
      "nombre": "Promo Especial Vendedor X",
      "descripcion": "...",
      "type": "PACK",
      "buyQuantity": 6,
      "packPrice": 50000.00,
      "mainProductId": "uuid",
      "mainProductName": "Nombre producto",
      "active": true,
      "validFrom": null,
      "validUntil": null,
      "parentPromotionId": "uuid-o-null",
      "parentPromotionName": null,
      "isLinked": false,
      "allowedVendorIds": ["uuid-vendedor"],
      "allowedVendorNames": ["Ana García"]
    }
    // ... más promo especiales
  ]
}
```

---

## 📋 Cómo migrar el código frontend

### ❌ ANTES (3 llamadas separadas — problemático con internet lento):
```javascript
// Esto hacía que con mala señal cualquiera de las 3 fallara
const [productos, promociones, promosEspeciales] = await Promise.all([
  api.get('/api/vendedor/products'),
  api.get('/api/vendedor/promotions'),
  api.get('/api/vendedor/special-promotions'),
]);
```

### ✅ AHORA (1 sola llamada — funciona con internet débil):
```javascript
// Una sola petición trae todo junto
const response = await api.get('/api/vendedor/init');

const productos         = response.data.productos;
const promociones       = response.data.promociones;
const promosEspeciales  = response.data.promocionesEspeciales;
```

---

## 💾 Implementar caché local (Service Worker / localStorage)

Para que la app funcione incluso si se **corta el internet** mientras la vendedora está trabajando, se recomienda guardar la respuesta en localStorage:

```javascript
const CACHE_KEY = 'vendedor_init_data';
const CACHE_TTL = 5 * 60 * 1000; // 5 minutos en milisegundos

async function cargarDatosInicio() {
  // 1. Intentar usar caché local primero
  const cached = localStorage.getItem(CACHE_KEY);
  if (cached) {
    const { data, timestamp } = JSON.parse(cached);
    const esFresco = (Date.now() - timestamp) < CACHE_TTL;
    if (esFresco) {
      console.log('✅ Datos cargados desde caché local (sin internet)');
      return data;
    }
  }

  // 2. Si no hay caché o expiró, ir al servidor
  try {
    const response = await api.get('/api/vendedor/init');
    const datos = response.data;

    // Guardar en localStorage para uso offline
    localStorage.setItem(CACHE_KEY, JSON.stringify({
      data: datos,
      timestamp: Date.now()
    }));

    console.log('✅ Datos frescos del servidor guardados en caché');
    return datos;

  } catch (error) {
    // 3. Si falla la red, usar caché aunque esté vencida
    if (cached) {
      console.warn('⚠️ Sin internet — usando datos guardados anteriormente');
      return JSON.parse(cached).data;
    }
    throw error; // No hay caché ni internet
  }
}
```

### Usar en el componente (React ejemplo):
```javascript
useEffect(() => {
  cargarDatosInicio()
    .then(({ productos, promociones, promocionesEspeciales }) => {
      setProductos(productos);
      setPromociones(promociones);
      setPromosEspeciales(promocionesEspeciales);
    })
    .catch(err => {
      console.error('No se pudo cargar — sin internet y sin caché', err);
      // Mostrar mensaje amigable al usuario
    });
}, []);
```

---

## 🔄 Cuándo invalidar el caché local

Limpiar el caché cuando el usuario hace acciones que cambian datos:

```javascript
// Limpiar caché después de crear un pedido (los stocks cambian)
async function crearPedido(pedidoData) {
  const response = await api.post('/api/vendedor/orders', pedidoData);
  // Invalidar caché de productos porque el stock cambió
  localStorage.removeItem(CACHE_KEY);
  return response.data;
}
```

---

## 📱 Mostrar indicador de estado de conexión

```javascript
// Mostrar a la vendedora si está usando datos viejitos
function IndicadorConexion({ usandoCache }) {
  if (!usandoCache) return null;
  return (
    <div style={{ background: '#fff3cd', padding: '8px', textAlign: 'center' }}>
      ⚠️ Sin internet — mostrando datos guardados. Los precios y stock pueden no estar actualizados.
    </div>
  );
}
```

---

## ⚙️ Lo que el backend ya hace automáticamente

| Optimización | Descripción |
|---|---|
| **GZIP** | Las respuestas JSON se comprimen 70-80%. Menos datos = más rápido con señal débil |
| **Cache-Control: max-age=120** | El navegador guarda la respuesta 2 min. No necesita pedir al servidor si recarga |
| **FETCH JOIN** | Las promociones se cargan en 1 sola query a la BD (antes hacía N+1 queries) |
| **Endpoint unificado** | 1 petición HTTP en vez de 3 al inicio |

---

## 🧪 Probar el endpoint

```bash
curl -X GET https://tu-servidor.com/api/vendedor/init \
  -H "Authorization: Bearer TU_TOKEN_JWT" \
  -H "Accept-Encoding: gzip"
```

---

## ⚠️ Notas importantes

1. Los endpoints anteriores (`/api/vendedor/products`, `/api/vendedor/promotions`, `/api/vendedor/special-promotions`) **siguen funcionando** — no se eliminaron. El cambio es opcional pero muy recomendado.
2. El campo `isSpecialProduct: true` en un producto indica que es un producto especial asignado solo a ese vendedor.
3. El campo `isValid: true` en una promoción confirma que está activa y dentro del período de fechas.
4. Las `promocionesEspeciales` son promociones asignadas solo a vendedores específicos (diferente a las `promociones` que son globales).

