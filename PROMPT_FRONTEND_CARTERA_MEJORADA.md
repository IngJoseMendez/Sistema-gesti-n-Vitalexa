# 🎯 PROMPT COMPLETO PARA FRONTEND - SISTEMA DE CARTERA MEJORADO

## 📋 RESUMEN DE FUNCIONALIDADES IMPLEMENTADAS

✅ **Panel de saldos muestra TODAS las facturas** (pagadas y pendientes) con historial completo de pagos
✅ **Historial detallado de pagos por factura** con fecha real del pago, método, usuario y auditoría completa
✅ **Fechas editables al registrar pagos** - el dueño puede establecer la fecha real del pago
✅ **Múltiples abonos parciales por factura** con trazabilidad completa
✅ **Exportación Excel por vendedor** con filtros avanzados
✅ **Días de mora y última fecha de pago** calculados automáticamente

---

## 🔗 NUEVOS ENDPOINTS DISPONIBLES

### 📊 Panel de Saldos Mejorado
```
GET /api/balances
- Ahora retorna TODAS las facturas (pagadas y pendientes) en el campo 'pendingOrders'
- Incluye historial completo de pagos de cada factura
- Campos agregados: lastPaymentDate, daysOverdue
```

### 🧾 Facturas de Cliente Específico
```
GET /api/balances/client/{clientId}/invoices/all
- Obtiene TODAS las facturas del cliente con historial completo de pagos
- Parámetros: ?startDate=2024-01-01&endDate=2024-12-31
- Ordenadas por fecha (más recientes primero)

GET /api/balances/client/{clientId}/invoices/pending  
- Solo facturas pendientes (funcionalidad anterior)
```

### 📈 Información Adicional del Cliente
```
GET /api/balances/client/{clientId}/days-overdue
- Retorna días de mora del cliente

GET /api/balances/client/{clientId}/last-payment-date
- Retorna última fecha de pago del cliente
```

### 📊 Exportación Excel MEJORADA
```
GET /api/balances/export/excel
- Parámetros: ?vendedorId=uuid&startDate=2024-01-01&endDate=2024-12-31&onlyWithDebt=false
- NUEVA FUNCIONALIDAD: Ahora retorna un Excel DETALLADO con:
  * Cada factura individual en una fila separada
  * Fecha de creación de la orden
  * Fecha de despacho (cuando se completó)
  * Fecha del último pago de cada factura específica
  * Días transcurridos desde la creación
  * Estado de pago por factura con códigos de color
  * Separación en sheets: "Facturas - Clientes que Deben" y "Facturas - Clientes al Día"
```

**📋 COLUMNAS DEL NUEVO EXCEL:**
1. **Vendedor** - Vendedor asignado
2. **Cliente** - Nombre del cliente
3. **Teléfono** - Teléfono del cliente
4. **Factura #** - Número de factura
5. **Fecha Creación** - Cuándo se creó la orden
6. **Fecha Despacho** - Cuándo se completó/despachó
7. **Total Factura** - Valor total de la factura
8. **Monto Pagado** - Cuánto se ha pagado de esa factura
9. **Saldo Pendiente** - Cuánto falta por pagar de esa factura
10. **Estado Pago** - PAGADO/PARCIAL/PENDIENTE (con colores)
11. **Fecha Último Pago** - Cuándo se hizo el último pago de esa factura específica
12. **Días desde Creación** - Días transcurridos desde que se creó

**🎨 CÓDIGOS DE COLOR:**
- 🟢 Verde: Facturas pagadas completamente
- 🟡 Amarillo: Facturas con pago parcial o facturas pendientes de menos de 15 días
- 🔴 Rojo: Facturas pendientes con más de 30 días

### 💰 Registro de Pagos Mejorado
```
POST /api/owner/payments
- Ahora permite establecer la fecha real del pago (actualPaymentDate)
- Requiere método de pago obligatorio
- Ejemplo del body:
{
  "orderId": "uuid-factura",
  "amount": 150000,
  "paymentMethod": "EFECTIVO", // EFECTIVO, TRANSFERENCIA, CHEQUE, etc.
  "actualPaymentDate": "2024-12-15", // OPCIONAL - fecha real del pago
  "withinDeadline": true,
  "discountApplied": 0,
  "notes": "Pago parcial - efectivo"
}
```

### 📋 Historial de Pagos
```
GET /api/owner/payments/order/{orderId}
- Retorna historial COMPLETO de pagos (incluyendo anulados)

GET /api/owner/payments/order/{orderId}/active  
- Solo pagos activos (excluye anulados)
```

---

## 🎨 CAMBIOS EN LA ESTRUCTURA DE DATOS

### ClientBalanceDTO - ACTUALIZADO
```typescript
interface ClientBalanceDTO {
  clientId: string;
  clientName: string;
  clientPhone: string;
  clientRepresentative: string;
  vendedorAsignadoName: string;
  creditLimit: number;
  initialBalance: number;
  totalOrders: number;
  totalPaid: number;
  pendingBalance: number;
  balanceFavor: number;
  pendingOrdersCount: number;
  pendingOrders: OrderPendingDTO[]; // ⚠️ AHORA CONTIENE TODAS LAS FACTURAS!
  lastPaymentDate: string | null;   // 🆕 Nueva propiedad
  daysOverdue: number;              // 🆕 Nueva propiedad
}
```

### OrderPendingDTO - SIN CAMBIOS
```typescript
interface OrderPendingDTO {
  orderId: string;
  invoiceNumber: number;
  fecha: string; // DateTime
  total: number;
  discountedTotal: number;
  paidAmount: number;
  pendingAmount: number;
  paymentStatus: 'PENDING' | 'PARTIAL' | 'PAID';
  payments: PaymentResponse[]; // ⭐ Historial completo de pagos
}
```

### PaymentResponse - ACTUALIZADO
```typescript
interface PaymentResponse {
  id: string;
  orderId: string;
  amount: number;
  paymentDate: string; // Timestamp de registro
  actualPaymentDate: string; // 🆕 Fecha real del pago
  paymentMethod: 'EFECTIVO' | 'TRANSFERENCIA' | 'CHEQUE' | 'TARJETA'; // 🆕
  withinDeadline: boolean;
  discountApplied: number;
  registeredByUsername: string;
  createdAt: string;
  notes: string;
  isCancelled: boolean;        // 🆕 Para auditoría
  cancelledAt: string | null;  // 🆕
  cancelledByUsername: string | null; // 🆕
  cancellationReason: string | null;  // 🆕
}
```

### CreatePaymentRequest - ACTUALIZADO
```typescript
interface CreatePaymentRequest {
  orderId: string;
  amount: number;
  paymentMethod: 'EFECTIVO' | 'TRANSFERENCIA' | 'CHEQUE' | 'TARJETA'; // 🆕 OBLIGATORIO
  actualPaymentDate?: string; // 🆕 OPCIONAL - fecha real del pago (formato YYYY-MM-DD)
  withinDeadline?: boolean;
  discountApplied?: number;
  notes?: string;
}
```

---

## 🎯 IMPLEMENTACIÓN RECOMENDADA EN FRONTEND

### 1. Panel de Saldos - Vista Principal
```jsx
// El componente principal ahora debe mostrar TODAS las facturas
const ClientBalancePanel = () => {
  const [balances, setBalances] = useState([]);
  
  // El endpoint retorna todas las facturas en 'pendingOrders' (mal nombre, pero contiene todas)
  const fetchBalances = async () => {
    const response = await api.get('/api/balances');
    setBalances(response.data);
  };
  
  return (
    <div>
      {balances.map(client => (
        <ClientCard key={client.clientId} client={client}>
          {/* Mostrar información general */}
          <div>
            <p>Saldo Pendiente: ${client.pendingBalance}</p>
            <p>Última Fecha Pago: {client.lastPaymentDate || 'Sin pagos'}</p>
            <p>Días de Mora: {client.daysOverdue}</p>
          </div>
          
          {/* Lista de TODAS las facturas */}
          <InvoicesList invoices={client.pendingOrders} />
        </ClientCard>
      ))}
    </div>
  );
};
```

### 2. Lista de Facturas con Historial de Pagos
```jsx
const InvoicesList = ({ invoices }) => {
  const [expandedInvoice, setExpandedInvoice] = useState(null);
  
  return (
    <div>
      {invoices.map(invoice => (
        <div key={invoice.orderId} className="invoice-card">
          <div 
            className="invoice-header" 
            onClick={() => setExpandedInvoice(
              expandedInvoice === invoice.orderId ? null : invoice.orderId
            )}
          >
            <span>Factura #{invoice.invoiceNumber}</span>
            <span>Fecha: {formatDate(invoice.fecha)}</span>
            <span>Total: ${invoice.total}</span>
            <span>Pagado: ${invoice.paidAmount}</span>
            <span>Pendiente: ${invoice.pendingAmount}</span>
            <span className={`status ${invoice.paymentStatus.toLowerCase()}`}>
              {getPaymentStatusText(invoice.paymentStatus)}
            </span>
            <span>{formatDate(getLastPaymentDate(invoice.payments))}</span>
          </div>
          
          {/* Historial de pagos expandible */}
          {expandedInvoice === invoice.orderId && (
            <PaymentHistory payments={invoice.payments} />
          )}
        </div>
      ))}
    </div>
  );
};
```

### 3. Historial de Pagos de una Factura
```jsx
const PaymentHistory = ({ payments }) => {
  return (
    <div className="payment-history">
      <h4>Historial de Pagos</h4>
      {payments.length === 0 ? (
        <p>No hay pagos registrados</p>
      ) : (
        payments.map(payment => (
          <div 
            key={payment.id} 
            className={`payment-item ${payment.isCancelled ? 'cancelled' : ''}`}
          >
            <div className="payment-info">
              <span>Fecha: {payment.actualPaymentDate}</span>
              <span>Monto: ${payment.amount}</span>
              <span>Método: {payment.paymentMethod}</span>
              <span>Registrado por: {payment.registeredByUsername}</span>
              {payment.notes && <span>Notas: {payment.notes}</span>}
            </div>
            
            {payment.isCancelled && (
              <div className="cancellation-info">
                <span>❌ ANULADO - {payment.cancellationReason}</span>
                <span>Por: {payment.cancelledByUsername}</span>
                <span>Fecha: {formatDate(payment.cancelledAt)}</span>
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
};
```

### 4. Formulario de Registro de Pagos Mejorado
```jsx
const PaymentForm = ({ orderId, onPaymentRegistered }) => {
  const [formData, setFormData] = useState({
    orderId: orderId,
    amount: '',
    paymentMethod: 'EFECTIVO', // Por defecto
    actualPaymentDate: '', // Fecha real del pago (opcional)
    withinDeadline: true,
    discountApplied: 0,
    notes: ''
  });
  
  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Si no se especifica fecha real, el backend usará hoy
    const payload = {
      ...formData,
      actualPaymentDate: formData.actualPaymentDate || undefined
    };
    
    await api.post('/api/owner/payments', payload);
    onPaymentRegistered();
  };
  
  return (
    <form onSubmit={handleSubmit}>
      <div>
        <label>Monto del Pago</label>
        <input 
          type="number" 
          step="0.01"
          value={formData.amount}
          onChange={(e) => setFormData({...formData, amount: e.target.value})}
          required 
        />
      </div>
      
      <div>
        <label>Método de Pago</label>
        <select 
          value={formData.paymentMethod}
          onChange={(e) => setFormData({...formData, paymentMethod: e.target.value})}
          required
        >
          <option value="EFECTIVO">Efectivo</option>
          <option value="TRANSFERENCIA">Transferencia</option>
          <option value="CHEQUE">Cheque</option>
          <option value="TARJETA">Tarjeta</option>
        </select>
      </div>
      
      <div>
        <label>Fecha Real del Pago (opcional)</label>
        <input 
          type="date"
          value={formData.actualPaymentDate}
          onChange={(e) => setFormData({...formData, actualPaymentDate: e.target.value})}
          placeholder="Si no se especifica, se usará la fecha de hoy"
        />
        <small>💡 Dejar vacío para usar la fecha de hoy</small>
      </div>
      
      <div>
        <label>Notas (opcional)</label>
        <textarea 
          value={formData.notes}
          onChange={(e) => setFormData({...formData, notes: e.target.value})}
          placeholder="Observaciones del pago..."
        />
      </div>
      
      <button type="submit">Registrar Pago</button>
    </form>
  );
};
```

### 5. Funciones Utilitarias
```jsx
// Obtener último pago de una factura
const getLastPaymentDate = (payments) => {
  if (!payments || payments.length === 0) return null;
  
  const activePaiements = payments.filter(p => !p.isCancelled);
  if (activePaiements.length === 0) return null;
  
  return activePaiements
    .map(p => p.actualPaymentDate)
    .sort((a, b) => new Date(b) - new Date(a))[0];
};

// Formatear estado de pago
const getPaymentStatusText = (status) => {
  const statusMap = {
    'PENDING': 'Pendiente',
    'PARTIAL': 'Parcial',
    'PAID': 'Pagado'
  };
  return statusMap[status] || status;
};

// Formatear método de pago
const getPaymentMethodText = (method) => {
  const methodMap = {
    'EFECTIVO': 'Efectivo',
    'TRANSFERENCIA': 'Transferencia',
    'CHEQUE': 'Cheque',
    'TARJETA': 'Tarjeta'
  };
  return methodMap[method] || method;
};
```

### 6. Exportación Excel MEJORADA con Desglose por Facturas
```jsx
const ExportButton = () => {
  const [filters, setFilters] = useState({
    vendedorId: '',
    startDate: '',
    endDate: '',
    onlyWithDebt: false
  });
  const [isExporting, setIsExporting] = useState(false);
  
  const handleExport = async () => {
    setIsExporting(true);
    try {
      const params = new URLSearchParams();
      if (filters.vendedorId) params.append('vendedorId', filters.vendedorId);
      if (filters.startDate) params.append('startDate', filters.startDate);
      if (filters.endDate) params.append('endDate', filters.endDate);
      if (filters.onlyWithDebt) params.append('onlyWithDebt', 'true');
      
      const response = await api.get(`/api/balances/export/excel?${params}`, {
        responseType: 'blob'
      });
      
      // Descargar archivo
      const blob = new Blob([response.data]);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `cartera-detallada-${new Date().getTime()}.xlsx`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      
      // Mostrar mensaje de éxito
      alert('📊 Excel exportado exitosamente!\n\n' +
            '✅ Incluye cada factura individual con:\n' +
            '• Fecha de creación y despacho\n' +
            '• Fecha específica del último pago por factura\n' +
            '• Estado de pago con colores\n' +
            '• Días transcurridos desde creación\n\n' +
            '📋 Separado en hojas:\n' +
            '• "Facturas - Clientes que Deben"\n' +
            '• "Facturas - Clientes al Día"');
            
    } catch (error) {
      console.error('Error exportando Excel:', error);
      alert('❌ Error al exportar Excel. Intenta nuevamente.');
    } finally {
      setIsExporting(false);
    }
  };
  
  return (
    <div className="export-section">
      <h3>📊 Exportar Cartera Detallada por Facturas</h3>
      <div className="export-description">
        <p>✨ <strong>Nueva funcionalidad mejorada:</strong></p>
        <ul>
          <li>🔍 <strong>Desglose por factura individual</strong> (no acumulado)</li>
          <li>📅 <strong>Fecha específica</strong> del último pago de cada factura</li>
          <li>🚀 <strong>Fecha de creación y despacho</strong> de cada orden</li>
          <li>📊 <strong>Días transcurridos</strong> desde la creación</li>
          <li>🎨 <strong>Códigos de color</strong> por estado de pago</li>
        </ul>
      </div>
      
      {/* Filtros */}
      <div className="filters-grid">
        <div className="filter-group">
          <label>🗓️ Fecha inicio</label>
          <input 
            type="date" 
            value={filters.startDate}
            onChange={(e) => setFilters({...filters, startDate: e.target.value})}
          />
        </div>
        
        <div className="filter-group">
          <label>🗓️ Fecha fin</label>
          <input 
            type="date" 
            value={filters.endDate}
            onChange={(e) => setFilters({...filters, endDate: e.target.value})}
          />
        </div>
        
        <div className="filter-group">
          <label>👤 Vendedor específico</label>
          <VendorSelect 
            value={filters.vendedorId}
            onChange={(vendedorId) => setFilters({...filters, vendedorId})}
            placeholder="Todos los vendedores"
          />
        </div>
        
        <div className="filter-group checkbox-group">
          <label>
            <input 
              type="checkbox"
              checked={filters.onlyWithDebt}
              onChange={(e) => setFilters({...filters, onlyWithDebt: e.target.checked})}
            />
            💰 Solo clientes con deuda
          </label>
        </div>
      </div>
      
      <button 
        onClick={handleExport}
        disabled={isExporting}
        className="export-btn btn-primary"
      >
        {isExporting ? (
          <>⏳ Generando Excel...</>
        ) : (
          <>📊 Exportar Excel Detallado</>
        )}
      </button>
      
      <div className="export-legend">
        <h4>🎨 Leyenda de colores en el Excel:</h4>
        <div className="color-legend">
          <span className="legend-item green">🟢 Facturas pagadas completamente</span>
          <span className="legend-item yellow">🟡 Facturas parciales o pendientes (&lt;15 días)</span>
          <span className="legend-item red">🔴 Facturas pendientes (&gt;30 días)</span>
        </div>
      </div>
    </div>
  );
};
```

---

## ⚡ PUNTOS IMPORTANTES PARA IMPLEMENTAR

### 🔴 CAMBIOS CRÍTICOS:
1. **El campo `pendingOrders` ahora contiene TODAS las facturas**, no solo pendientes
2. **Cada factura tiene historial completo de pagos** en el campo `payments`
3. **Al registrar pagos, el método de pago es obligatorio**
4. **La fecha real del pago es opcional** - si no se envía, usa la fecha actual

### 🟢 NUEVAS FUNCIONALIDADES:
1. **Expandir facturas para ver historial de pagos**
2. **Mostrar última fecha de pago y días de mora**
3. **Permitir al dueño establecer fecha real del pago**
4. **Distinguir entre pagos activos y anulados**
5. **Exportar cartera a Excel con filtros**

### 🟡 RECOMENDACIONES UX:
1. **Usar iconos diferentes** para facturas pagadas vs pendientes
2. **Mostrar progreso de pago** (barra de progreso por factura)
3. **Resaltar facturas en mora** con colores llamativos
4. **Agrupar por estado**: "Pagadas", "Parciales", "Pendientes"
5. **Agregar tooltips** explicativos en campos nuevos

---

## 📱 EJEMPLO DE LLAMADA COMPLETA

```javascript
// Obtener todas las facturas de un cliente
const clientInvoices = await api.get(`/api/balances/client/${clientId}/invoices/all`);

// Registrar un pago con fecha personalizada
const newPayment = await api.post('/api/owner/payments', {
  orderId: invoiceId,
  amount: 150000,
  paymentMethod: 'TRANSFERENCIA',
  actualPaymentDate: '2024-12-10', // Pago hecho hace unos días
  notes: 'Transferencia Bancolombia - Ref: 123456'
});

// Exportar cartera de un vendedor específico
const excel = await api.get('/api/balances/export/excel?vendedorId=uuid&onlyWithDebt=true', {
  responseType: 'blob'
});
```

---

## 🎯 RESUMEN FINAL

✅ **El panel de saldos ahora es completamente funcional** con historial detallado de pagos  
✅ **El dueño puede establecer fechas reales de pago** al momento de registrar  
✅ **Se puede ver el historial completo de cada factura** haciendo clic en ella  
✅ **Todas las facturas aparecen** (pagadas y pendientes) en el panel principal  
✅ **Exportación Excel implementada** con filtros por vendedor y fechas  

**¡La funcionalidad está completamente implementada y lista para usar en el frontend!** 🚀
