# 📋 Guía: Agregar Facturas Históricas para Cuadre de Caja

## 🎯 Propósito
Para hacer un **cuadre de caja** de las facturas anteriores al sistema, el Owner puede agregar facturas históricas con todos los datos relevantes.

## 📍 Endpoints

```
GET  /api/owner/invoices/clients     → Obtener lista de clientes disponibles
POST /api/owner/invoices              → Crear factura histórica
```

**Requiere:** Autenticación con rol OWNER

## 👥 Cómo Obtener Lista de Clientes

**Request:**
```
GET /api/owner/invoices/clients
```

**Response (200 OK):**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nombre": "Juan García",
    "telefono": "3001234567",
    "email": "juan@example.com",
    "direccion": "Calle 5 #123",
    "nit": "12345678",
    ...
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "nombre": "Distribuidora ABC",
    "telefono": "3109876543",
    "email": "info@distribuidora.com",
    "direccion": "Avenida Principal 456",
    "nit": "987654321",
    ...
  }
]
```

Una vez obtengas la lista, **usa el `id` del cliente** que necesites vincular a la factura.

## 📦 Datos de la Solicitud

### ✅ Campos Obligatorios

| Campo | Tipo | Descripción | Ejemplo |
|-------|------|-------------|---------|
| `invoiceNumber` | Long | Número único de la factura | 1001 |
| `fecha` | LocalDateTime | Fecha de emisión de la factura | 2025-01-15T14:30:00 |
| `totalValue` | BigDecimal | Monto total de la factura | 450000.00 |
| `amountPaid` | BigDecimal | **Cuánto pagó el cliente** (0 o mayor) | 300000.00 |
| `invoiceType` | String | **Tipo de factura:** NORMAL, SR o PROMO | NORMAL |

### 📝 Campos Opcionales

| Campo | Tipo | Descripción | Ejemplo |
|-------|------|-------------|---------|
| `clientId` | UUID | **[RECOMENDADO]** ID del cliente si está registrado | 550e8400-e29b-41d4-a716-446655440000 |
| `clientName` | String | Nombre del cliente (si no está registrado) | Juan García |
| `clientPhone` | String | Teléfono del cliente | 3001234567 |
| `clientEmail` | String | Email del cliente | juan@example.com |
| `clientAddress` | String | Dirección del cliente | Calle 5 #123 |
| `notes` | String | Notas adicionales | Entrega pendiente |

### 💡 Cómo funciona el cálculo automático

**Ejemplo 1: Cliente que pagó parcialmente**
```
totalValue = 600,000
amountPaid = 300,000
→ Sistema calcula automáticamente: DEBE = 300,000
```

**Ejemplo 2: Cliente que pagó todo**
```
totalValue = 600,000
amountPaid = 600,000
→ Sistema calcula automáticamente: DEBE = 0 (Pagado completo)
```

**Ejemplo 3: Cliente sin haber pagado**
```
totalValue = 600,000
amountPaid = 0
→ Sistema calcula automáticamente: DEBE = 600,000 (Total debe)
```

### 👥 Asignación del Vendedor

✅ **La factura se asigna AUTOMÁTICAMENTE al vendedor correcto:**

- **Si el cliente pertenece a VendedorX** → Factura se registra como venta de VendedorX
- **Si el cliente no tiene vendedor asignado** → Se asigna al Owner (vendedor default)

**Ejemplo:**
- Cliente "Juan García" es cliente de "MariaVendedor"
- Owner crea factura histórica de Juan García
- Resultado: ✅ La factura aparece en las ventas de MariaVendedor, no del Owner

## 🏷️ Tipos de Factura

El campo `invoiceType` determina cómo se clasifica la factura en los reportes:

| Tipo | Valor | Descripción | En Reportes |
|------|-------|-------------|-------------|
| **Normal** | `NORMAL` | Factura estándar de venta | Se cuenta como venta normal |
| **Remisión** | `SR` | Sin Retorno (devolución posible) | Se marca como `[S/R]` en Excel |
| **Promoción** | `PROMO` | Venta con promoción especial | Se marca como `[Promoción]` en Excel |

### 💡 Cómo elegir el tipo:

- **NORMAL**: La mayoría de facturas (venta regular de productos)
- **SR (Remisión)**: Cuando es sin retorno (venta a consignación o devolución posible)
- **PROMO**: Cuando fue una venta especial con promoción o descuento

## 📨 Ejemplo de Solicitud (NORMAL - RECOMENDADO)

```json
{
  "invoiceNumber": 1001,
  "fecha": "2025-01-15T14:30:00",
  "totalValue": 450000.00,
  "amountPaid": 300000.00,
  "invoiceType": "NORMAL",
  "clientId": "550e8400-e29b-41d4-a716-446655440000",
  "notes": "Factura histórica del período anterior"
}
```
**Resultado:** Cliente pagó 300,000 y debe 150,000

## 📨 Ejemplo de Solicitud (REMISIÓN S/R)

```json
{
  "invoiceNumber": 1002,
  "fecha": "2025-01-14T10:00:00",
  "totalValue": 250000.00,
  "amountPaid": 250000.00,
  "invoiceType": "SR",
  "clientId": "550e8400-e29b-41d4-a716-446655440001",
  "notes": "Remisión sin retorno - Pago completo"
}
```
**Resultado:** Cliente pagó todo, no debe nada

## 📨 Ejemplo de Solicitud (PROMOCIÓN)

```json
{
  "invoiceNumber": 1003,
  "fecha": "2025-01-10T09:30:00",
  "totalValue": 800000.00,
  "amountPaid": 0.00,
  "invoiceType": "PROMO",
  "clientId": "550e8400-e29b-41d4-a716-446655440000",
  "notes": "Venta con promoción especial - Pendiente de pago"
}
```
**Resultado:** Cliente no pagó nada, debe la factura completa (800,000)

## ✅ Ejemplo de Solicitud Mínima

```json
{
  "invoiceNumber": 1001,
  "fecha": "2025-01-15T14:30:00",
  "totalValue": 450000.00,
  "amountPaid": 0.00,
  "invoiceType": "NORMAL"
}
```

## 📊 Respuesta Exitosa (201 Created)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "invoiceNumber": 1001,
  "fecha": "2025-01-15T14:30:00",
  "total": 450000.00,
  "estado": "COMPLETADO",
  "vendedor": "owner_username",
  "cliente": "Juan García",
  "notas": "[HISTÓRICA] Cliente: Juan García | Tel: 3001234567 | Email: juan@example.com | Dir: Calle 5 #123, Apartado | Monto Adeudado: $450000.00 - Factura histórica del período anterior"
}
```

## ⚠️ Errores Posibles

### 400 Bad Request
- Falta algún campo obligatorio
- El `invoiceNumber` no es positivo
- El `totalValue` o `dueAmount` no son positivos

### 409 Conflict
- Ya existe una factura con ese número
- Usar un `invoiceNumber` diferente

### 403 Forbidden
- No tienes permisos (debes ser Owner)

## 🔍 Características Importantes

✅ **Sin productos**: Las facturas históricas NO tienen items de productos, solo son registros  
✅ **Estado completado**: Automáticamente se marcan como COMPLETADAS  
✅ **Registro automático**: Si el cliente existe, se registra la compra en su balance  
✅ **Datos auditables**: Todos los datos se guardan en las notas para trazabilidad  
✅ **Número único**: Cada factura debe tener un número diferente  

## 💡 Casos de Uso

### Caso 1: Cliente pagó todo
```json
{
  "invoiceNumber": 1002,
  "fecha": "2025-01-14T10:00:00",
  "totalValue": 125000.00,
  "amountPaid": 125000.00,
  "invoiceType": "NORMAL",
  "clientName": "Cliente Ocasional",
  "notes": "Pago completo realizado"
}
```
**Resultado:** Factura completamente pagada, cliente no debe nada

### Caso 2: Cliente pagó parcialmente
```json
{
  "invoiceNumber": 1003,
  "fecha": "2025-01-10T09:30:00",
  "totalValue": 800000.00,
  "amountPaid": 500000.00,
  "invoiceType": "SR",
  "clientId": "550e8400-e29b-41d4-a716-446655440000",
  "notes": "Pago parcial - Pendiente 300,000"
}
```
**Resultado:** Cliente pagó 500k, debe 300k

### Caso 3: Cliente no ha pagado nada
```json
{
  "invoiceNumber": 1004,
  "fecha": "2025-01-05T16:45:00",
  "totalValue": 250000.00,
  "amountPaid": 0.00,
  "invoiceType": "PROMO",
  "clientName": "Distribuidora ABC",
  "clientPhone": "3109876543",
  "clientEmail": "info@distribuidora.com",
  "clientAddress": "Avenida Principal 456",
  "notes": "Pago diferido a 30 días"
}
```
**Resultado:** Cliente debe la factura completa (250k)

## 🛠️ Integración con el Sistema

### ✅ Reportes Afectados

Las facturas históricas **se incluyen automáticamente** en todos los reportes:

| Reporte | Efecto |
|---------|--------|
| **Reporte de Ventas** | ✅ Se suma el monto a ingresos totales y órdenes completadas |
| **Reporte General** | ✅ Se incluye en el total de ingresos del período |
| **Excel/PDF/CSV** | ✅ Aparecen con tag `[HISTÓRICA]` en las exportaciones |
| **Balance de Clientes** | ✅ Se actualiza el balance si está vinculado un cliente |
| **Cuadre de Caja** | ✅ Se cuenta como orden completada |

### 📊 Ejemplo: Cómo Afectan los Reportes

**Escenario:**
- Órdenes del sistema (1-15 de enero): $2,500,000
- Facturas históricas agregadas (16-31 de diciembre): $1,800,000

**Reporte de Ventas (Rango: Diciembre - Enero):**
```
Ingresos Totales: $4,300,000 ✅ (suma ambas)
Órdenes Completadas: 8 ✅ (4 del sistema + 4 históricas)
Valor Promedio: $537,500 ✅ (calculado sobre todas)
```

### 🏦 Ejemplo: Cuadre de Caja

Supongamos que necesitas cuadrar el efectivo de hasta el 31 de diciembre (antes del sistema):

**Paso 1: Obtener clientes**
```bash
GET /api/owner/invoices/clients
```

**Paso 2: Agregar facturas históricas**
```bash
POST /api/owner/invoices

{
  "invoiceNumber": 500,
  "fecha": "2024-12-20T15:00:00",
  "totalValue": 450000.00,
  "dueAmount": 0.00,
  "clientId": "550e8400-e29b-41d4-a716-446655440000",
  "notes": "Pago completo recibido"
}
```

**Resultado:**
- ✅ Factura guardada con estado COMPLETADO
- ✅ Se suma automáticamente a los reportes
- ✅ Si cliente existe, su balance se actualiza
- ✅ Aparece en reportes con tag `[HISTÓRICA]`

### 🔍 Cómo Identificar Facturas Históricas

En los reportes, busca el tag **`[HISTÓRICA]`** en las notas:

```
[HISTÓRICA] Tipo: Normal | Cliente: Juan García | ... | Monto Adeudado: $450000.00 [Standard]
[HISTÓRICA] Tipo: Remisión (S/R) | Cliente: ABC | ... | Monto Adeudado: $300000.00 [S/R]
[HISTÓRICA] Tipo: Promoción | Cliente: XYZ | ... | Monto Adeudado: $100000.00 [Promoción]
```

**Diferenciación por tipo en Excel/Reportes:**
- **[Standard]**: Aparece como factura normal de venta
- **[S/R]**: Aparece como Remisión (Sin Retorno) - puede tener devolución
- **[Promoción]**: Aparece como venta con promoción especial

Esto diferencia:
- **Facturas del sistema**: Creadas normalmente por vendedores
- **Facturas históricas**: Creadas por Owner para cuadre de caja

Además, todas llevan el marcador **`[HISTÓRICA]`** para auditoría.

### 💰 Cálculo Automático - Pagado vs Debe

✅ **El sistema calcula automáticamente cuánto debe cada cliente:**
- Fórmula: `Debe = totalValue - amountPaid`
- El Owner solo ingresa cuánto pagó
- El sistema calcula el resto automáticamente

**Impacto en Balance:**
- Se registra automáticamente como un Payment
- Aparece en el **saldo pendiente** del cliente
- Se incluye en los reportes de balance
- Permite que el Owner haga cuadre de caja correcto

**Ejemplo:**
```json
{
  "invoiceNumber": 1001,
  "fecha": "2025-01-15T14:30:00",
  "totalValue": 450000.00,
  "amountPaid": 300000.00,    ← Owner ingresa lo que pagó
  "invoiceType": "NORMAL",
  "clientId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Sistema calcula automáticamente:**
- Pagado: $300,000 ✅
- Debe: $150,000 (450,000 - 300,000) ✅
- Aparece en reportes de balance ✅
- Aparece en "Saldo por Cliente" en el Excel
- El Owner puede hacer seguimiento de cobros

## 📊 NUEVOS ENDPOINTS - Reportes por Vendedor

Además del reporte general, ahora el Owner puede descargar reportes específicos de cada vendedor:

### 📥 Descargar Excel de UN vendedor específico

```
GET /api/reports/export/vendor/{vendedorId}/excel?startDate=2025-01-01&endDate=2025-01-31
```

**Parámetros:**
- `vendedorId`: UUID del vendedor (obtenido de las órdenes)
- `startDate`: Opcional, rango de fechas
- `endDate`: Opcional

**Respuesta:** Excel con una hoja contiene todas las ventas diarias de ese vendedor

### 📄 Descargar PDF de UN vendedor específico

```
GET /api/reports/export/vendor/{vendedorId}/pdf?startDate=2025-01-01&endDate=2025-01-31
```

**Respuesta:** PDF con reporte de ventas del vendedor

### ✨ Caso de uso: Seguimiento individual

1. **Owner descarga reporte general** → Ver todas las hojas de todos los vendedores
2. **Owner hace clic en un vendedor** → Descargar reporte individual de ese vendedor
3. **Facilita:** Auditoría, seguimiento, incentivos por vendedor
