# 🎯 PROMPT: Implementar Sistema Completo de Pagos en Frontend

## 📋 CONTEXTO

El backend tiene un sistema completo de **pagos/abonos** y **saldos de clientes** ya implementado. El frontend tiene archivos base creados (`BalancesPage.js`, `EnhancedPaymentFormModal.js`, `PaymentHistoryModal.js`) pero **el formulario de pagos no está conectado** — el import de `EnhancedPaymentFormModal` está comentado en `BalancesPage.js` (línea 11) y no se usa en ninguna parte.

**El problema actual:** Cuando se intenta registrar un pago, el frontend NO envía `paymentMethod` (campo obligatorio) y falla con error 400. Además, el `EnhancedPaymentFormModal` no se abre desde ningún botón en la interfaz.

---

## 🔗 ENDPOINTS DEL BACKEND

### Pagos (Solo rol OWNER) — Base: `/api/owner/payments`

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/owner/payments` | Registrar un pago/abono |
| `GET` | `/api/owner/payments/order/{orderId}` | Historial completo de pagos (incluye anulados) |
| `GET` | `/api/owner/payments/order/{orderId}/active` | Solo pagos activos |
| `GET` | `/api/owner/payments/{paymentId}` | Un pago específico |
| `PUT` | `/api/owner/payments/{paymentId}/cancel?reason=texto` | Anular pago (soft delete) |
| `PUT` | `/api/owner/payments/{paymentId}/restore` | Restaurar pago anulado |

### Saldos de Clientes — Base: `/api/balances`

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/balances` | Todos los saldos (filtra por `?vendedorId=uuid`) |
| `GET` | `/api/balances/client/{clientId}` | Saldo de un cliente específico |
| `GET` | `/api/balances/client/{clientId}/invoices/all?startDate=&endDate=` | TODAS las facturas del cliente |
| `GET` | `/api/balances/client/{clientId}/invoices/pending?startDate=&endDate=` | Solo facturas pendientes |
| `GET` | `/api/balances/client/{clientId}/days-overdue` | Días de mora |
| `GET` | `/api/balances/client/{clientId}/last-payment-date` | Última fecha de pago |
| `GET` | `/api/balances/export/excel?vendedorId=&startDate=&endDate=&onlyWithDebt=` | Exportar a Excel |
| `PUT` | `/api/balances/client/{clientId}/credit-limit?amount=` | Establecer tope crédito (Owner) |
| `DELETE` | `/api/balances/client/{clientId}/credit-limit` | Eliminar tope crédito (Owner) |
| `PUT` | `/api/balances/client/{clientId}/initial-balance?amount=` | Saldo inicial (una vez, Owner) |
| `PUT` | `/api/balances/client/{clientId}/balance-favor?amount=` | Agregar saldo a favor (Owner) |

---

## 📊 ESTRUCTURAS DE DATOS

### CreatePaymentRequest (POST body)
```json
{
  "orderId": "uuid-de-la-orden",        // OBLIGATORIO
  "amount": 150000,                      // OBLIGATORIO, positivo
  "paymentMethod": "EFECTIVO",           // OBLIGATORIO - ver valores abajo
  "actualPaymentDate": "2026-02-15",     // OPCIONAL - si no se envía usa fecha de hoy
  "withinDeadline": true,                // OPCIONAL
  "discountApplied": 0,                  // OPCIONAL
  "notes": "Pago parcial en efectivo"    // OPCIONAL
}
```

### PaymentMethod (enum — valores permitidos)
```
EFECTIVO        → "Efectivo"
TRANSFERENCIA   → "Transferencia Bancaria"
CHEQUE          → "Cheque"
TARJETA         → "Tarjeta de Crédito/Débito"
CREDITO         → "Crédito"
OTRO            → "Otro"
```

### PaymentResponse (respuesta del backend)
```typescript
{
  id: string;                    // UUID del pago
  orderId: string;               // UUID de la orden
  amount: number;                // Monto del pago
  paymentDate: string;           // Timestamp de registro en el sistema
  actualPaymentDate: string;     // Fecha real del pago (puede ser anterior)
  paymentMethod: string;         // EFECTIVO, TRANSFERENCIA, etc.
  withinDeadline: boolean;
  discountApplied: number;
  registeredByUsername: string;   // Quién registró el pago
  createdAt: string;
  notes: string;
  isCancelled: boolean;          // Si fue anulado
  cancelledAt: string | null;
  cancelledByUsername: string | null;
  cancellationReason: string | null;
}
```

### ClientBalanceDTO
```typescript
{
  clientId: string;
  clientName: string;
  clientPhone: string;
  clientRepresentative: string;
  vendedorAsignadoName: string;
  creditLimit: number;
  initialBalance: number;
  totalOrders: number;           // Total de órdenes completadas
  totalPaid: number;             // Total pagado
  pendingBalance: number;        // Saldo pendiente
  balanceFavor: number;          // Saldo a favor
  pendingOrdersCount: number;
  pendingOrders: OrderPendingDTO[];  // TODAS las facturas (pagadas + pendientes)
  lastPaymentDate: string | null;
  daysOverdue: number;
}
```

### OrderPendingDTO
```typescript
{
  orderId: string;
  invoiceNumber: number;
  fecha: string;                 // DateTime de la factura
  total: number;
  discountedTotal: number;
  paidAmount: number;
  pendingAmount: number;
  paymentStatus: 'PENDING' | 'PARTIAL' | 'PAID';
  payments: PaymentResponse[];   // Lista de pagos de esta factura
}
```

---

## 🎯 QUÉ IMPLEMENTAR

### 1. Conectar `EnhancedPaymentFormModal` en `BalancesPage.js`

El modal ya existe en `src/components/modals/EnhancedPaymentFormModal.js` pero:
- El import está **comentado** en línea 11 de `BalancesPage.js`
- No hay ningún **botón "Registrar Pago"** en la interfaz que lo abra
- Necesitas **descomentar el import** y agregar un botón para abrir el modal
- El botón de "Registrar Pago" debe aparecer en cada factura del detalle del cliente (solo para rol Owner)
- Al hacer clic, debe abrir `EnhancedPaymentFormModal` pasando la orden seleccionada

### 2. Verificar que `EnhancedPaymentFormModal` envíe `paymentMethod`

El archivo `src/components/modals/EnhancedPaymentFormModal.js` ya existe (293 líneas) y parece tener el campo de método de pago. Verificar que:
- El campo `paymentMethod` se envía correctamente en el payload
- Tiene un valor por defecto (e.g., `EFECTIVO`)
- Los 6 métodos de pago están en el select: `EFECTIVO`, `TRANSFERENCIA`, `CHEQUE`, `TARJETA`, `CREDITO`, `OTRO`
- La fecha `actualPaymentDate` se envía como formato `YYYY-MM-DD` o se omite para usar fecha de hoy

### 3. Verificar `PaymentHistoryModal`

El archivo `src/components/modals/PaymentHistoryModal.js` ya existe (260 líneas). El import está activo en `BalancesPage.js` (línea 10). Verificar que:
- Se abre cuando haces clic en una factura (función `handleShowPaymentHistory` ya existe)
- Muestra todos los pagos de la factura con fecha, monto, método de pago
- Permite al Owner anular pagos con razón
- Permite al Owner restaurar pagos anulados
- Pagos anulados se muestran tachados/con estilo diferente

### 4. Mejorar la vista de facturas en `ClientDetailView`

Dentro de `BalancesPage.js`, el componente `ClientDetailView` muestra las facturas del cliente. Asegurar que:
- Cada factura muestra: número, fecha, total, pagado, pendiente, estado
- El estado se muestra con colores: 🟢 Pagado, 🟡 Pendiente, 🔴 Parcial
- Hay un botón "💰 Registrar Pago" visible para cada factura pendiente (solo Owner)
- Al hacer clic en la factura se abre el historial de pagos
- Si una factura tiene múltiples pagos, se puede ver cada uno individualmente

### 5. Agregar días de mora y última fecha de pago

El `ClientBalanceDTO` ya incluye `lastPaymentDate` y `daysOverdue`. Mostrar:
- Badge de días de mora en la tarjeta del cliente
- Última fecha de pago en los detalles del cliente
- Indicador visual cuando un cliente tiene mora alta (>30 días → rojo)

---

## 📁 ARCHIVOS A MODIFICAR

| Archivo | Acción |
|---------|--------|
| `src/pages/BalancesPage.js` | Descomentar imports, agregar botón "Registrar Pago", conectar modales |
| `src/components/modals/EnhancedPaymentFormModal.js` | Verificar/corregir que envíe paymentMethod correctamente |
| `src/components/modals/PaymentHistoryModal.js` | Verificar que muestre/anule/restaure pagos correctamente |
| `src/pages/BalancesPage.css` | Agregar estilos para botones de pago, badges de mora |
| `src/api/paymentService.js` | Ya está completo, verificar que funcione |
| `src/api/balanceService.js` | Ya está completo, verificar que funcione |

---

## ⚡ NOTAS IMPORTANTES

1. **Solo el rol OWNER puede registrar pagos.** El botón "Registrar Pago" solo debe aparecer si `userRole === 'ROLE_OWNER'`
2. **`paymentMethod` es OBLIGATORIO.** Si no se envía, el backend devuelve error 400
3. **`actualPaymentDate` es OPCIONAL.** Si no se envía, el backend usa la fecha de hoy
4. **Los pagos se anulan con soft delete**, no se borran. Tienen campos de auditoría
5. **Una factura puede tener MÚLTIPLES pagos parciales.** El historial debe mostrar TODOS
6. **`pendingOrders` contiene TODAS las facturas** (pagadas + pendientes), no solo pendientes
7. **El frontend ya usa `apiClient` (axios)** que agrega `/api` al base URL automáticamente. En `paymentService.js` las rutas son `/owner/payments` (sin `/api/`)
