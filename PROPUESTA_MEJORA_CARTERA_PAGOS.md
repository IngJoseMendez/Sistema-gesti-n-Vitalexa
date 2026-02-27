# 🎯 PROPUESTA ARQUITECTÓNICA: MEJORA SISTEMA DE CARTERA Y PAGOS

## 📋 ANÁLISIS DE LA SITUACIÓN ACTUAL

### ✅ Lo que YA tenemos implementado:

1. **Tabla `payments`** - Registra todos los pagos con:
   - `id` (UUID)
   - `order_id` (relación con orden/factura)
   - `amount` (monto del pago)
   - `payment_date` (fecha del pago)
   - `within_deadline` (si fue a tiempo)
   - `discount_applied` (descuento aplicado)
   - `registered_by` (usuario que registró)
   - `created_at` (timestamp de creación del registro)
   - `notes` (notas del pago)

2. **Servicios implementados**:
   - `PaymentService.registerPayment()` - Registra pagos
   - `PaymentService.getPaymentsByOrderId()` - Obtiene historial de pagos
   - `PaymentService.deletePayment()` - Anula un pago
   - `ClientBalanceService.getClientBalance()` - Obtiene saldo de cliente
   - `ClientBalanceService.getAllClientBalances()` - Obtiene todos los saldos

3. **DTOs existentes**:
   - `PaymentResponse` - Incluye toda la información del pago
   - `ClientBalanceDTO` - Incluye saldo y lista de facturas pendientes
   - `OrderPendingDTO` - Incluye lista de pagos por factura

### ❌ Lo que FALTA implementar:

1. **Fecha manual del pago** - Actualmente `paymentDate` siempre es `LocalDateTime.now()`
2. **Método de pago** - No se registra cómo pagó el cliente
3. **Anulación con auditoría** - Los pagos se eliminan físicamente
4. **Exportación Excel de cartera**
5. **Filtros avanzados** - Por rango de fechas, vendedor, etc.
6. **Días de mora** - No se calcula
7. **Última fecha de pago** - No está en el DTO principal

---

## 🏗️ PROPUESTA DE SOLUCIÓN

### 1️⃣ MEJORAS EN LA TABLA `payments`

**Agregar nuevas columnas:**

```sql
ALTER TABLE payments ADD COLUMN payment_method VARCHAR(50);
ALTER TABLE payments ADD COLUMN actual_payment_date DATE;
ALTER TABLE payments ADD COLUMN is_cancelled BOOLEAN DEFAULT FALSE;
ALTER TABLE payments ADD COLUMN cancelled_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN cancelled_by VARCHAR(255);
ALTER TABLE payments ADD COLUMN cancellation_reason TEXT;
```

**Explicación:**
- `payment_method`: Efectivo, Transferencia, Cheque, etc.
- `actual_payment_date`: Fecha REAL del pago (la que define el dueño)
- `payment_date`: Se mantiene como timestamp de registro automático
- `is_cancelled`: Bandera de anulación (soft delete)
- Campos de auditoría de anulación

### 2️⃣ NUEVAS ENTIDADES

#### A. Enum `PaymentMethod`

```java
package org.example.sistema_gestion_vitalexa.enums;

public enum PaymentMethod {
    EFECTIVO("Efectivo"),
    TRANSFERENCIA("Transferencia Bancaria"),
    CHEQUE("Cheque"),
    TARJETA("Tarjeta de Crédito/Débito"),
    CREDITO("Crédito"),
    OTRO("Otro");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

### 3️⃣ ACTUALIZACIÓN DE ENTIDADES EXISTENTES

#### Actualizar `Payment.java`:

```java
// Agregar a la clase Payment:

@Enumerated(EnumType.STRING)
@Column(name = "payment_method", length = 50)
@Builder.Default
private PaymentMethod paymentMethod = PaymentMethod.EFECTIVO;

@Column(name = "actual_payment_date")
private LocalDate actualPaymentDate;

@Column(name = "is_cancelled")
@Builder.Default
private Boolean isCancelled = false;

@Column(name = "cancelled_at")
private LocalDateTime cancelledAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "cancelled_by")
private User cancelledBy;

@Column(name = "cancellation_reason", columnDefinition = "TEXT")
private String cancellationReason;
```

### 4️⃣ ACTUALIZACIÓN DE DTOs

#### A. Actualizar `CreatePaymentRequest`:

```java
public record CreatePaymentRequest(
    @NotNull(message = "El ID de la orden es obligatorio") 
    UUID orderId,

    @NotNull(message = "El monto es obligatorio") 
    @Positive(message = "El monto debe ser positivo") 
    BigDecimal amount,

    @NotNull(message = "El método de pago es obligatorio")
    PaymentMethod paymentMethod,

    LocalDate actualPaymentDate, // Fecha real del pago (opcional, si no se envía usa hoy)

    Boolean withinDeadline,

    BigDecimal discountApplied,

    String notes
) {}
```

#### B. Actualizar `PaymentResponse`:

```java
public record PaymentResponse(
    UUID id,
    UUID orderId,
    BigDecimal amount,
    LocalDateTime paymentDate, // Timestamp de registro
    LocalDate actualPaymentDate, // Fecha real del pago
    PaymentMethod paymentMethod,
    Boolean withinDeadline,
    BigDecimal discountApplied,
    String registeredByUsername,
    LocalDateTime createdAt,
    String notes,
    Boolean isCancelled,
    LocalDateTime cancelledAt,
    String cancelledByUsername,
    String cancellationReason
) {}
```

#### C. Actualizar `ClientBalanceDTO`:

```java
public record ClientBalanceDTO(
    UUID clientId,
    String clientName,
    String clientPhone,
    String clientRepresentative,
    String vendedorAsignadoName,
    BigDecimal creditLimit,
    BigDecimal initialBalance,
    BigDecimal totalOrders,
    BigDecimal totalPaid,
    BigDecimal pendingBalance,
    BigDecimal balanceFavor,
    Integer pendingOrdersCount,
    List<OrderPendingDTO> pendingOrders,
    LocalDate lastPaymentDate, // 🆕 Última fecha de pago
    Integer daysOverdue // 🆕 Días de mora
) {}
```

#### D. Nuevo DTO `ClientBalanceExportDTO`:

```java
public record ClientBalanceExportDTO(
    UUID clientId,
    String clientName,
    String clientPhone,
    String vendedorName,
    BigDecimal totalOrders,
    BigDecimal totalPaid,
    BigDecimal pendingBalance,
    LocalDate lastPaymentDate,
    Integer daysOverdue,
    String status, // "DEBE" / "AL DÍA"
    Integer pendingInvoicesCount
) {}
```

### 5️⃣ NUEVOS ENDPOINTS

#### A. Controller `PaymentOwnerController` (actualizar):

```java
/**
 * Anular un pago (soft delete con auditoría)
 */
@PutMapping("/{paymentId}/cancel")
public ResponseEntity<PaymentResponse> cancelPayment(
        @PathVariable UUID paymentId,
        @RequestParam(required = false) String reason,
        Authentication auth) {
    PaymentResponse response = paymentService.cancelPayment(paymentId, reason, auth.getName());
    return ResponseEntity.ok(response);
}

/**
 * Restaurar un pago anulado
 */
@PutMapping("/{paymentId}/restore")
@PreAuthorize("hasRole('OWNER')")
public ResponseEntity<PaymentResponse> restorePayment(
        @PathVariable UUID paymentId,
        Authentication auth) {
    PaymentResponse response = paymentService.restorePayment(paymentId, auth.getName());
    return ResponseEntity.ok(response);
}

/**
 * Obtener historial completo de un pago (incluye anulaciones)
 */
@GetMapping("/{paymentId}/audit")
public ResponseEntity<PaymentResponse> getPaymentAudit(@PathVariable UUID paymentId) {
    return ResponseEntity.ok(paymentService.getPaymentById(paymentId));
}
```

#### B. Controller `ClientBalanceController` (actualizar):

```java
/**
 * Exportar cartera por vendedor a Excel
 */
@GetMapping("/export/excel")
public ResponseEntity<byte[]> exportBalanceToExcel(
        @RequestParam(required = false) UUID vendedorId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) Boolean onlyWithDebt,
        Authentication auth) throws IOException {
    
    byte[] excelBytes = clientBalanceService.exportBalanceToExcel(
        vendedorId, startDate, endDate, onlyWithDebt, auth.getName()
    );
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    headers.setContentDispositionFormData("attachment", "cartera_clientes_" + LocalDate.now() + ".xlsx");
    
    return ResponseEntity.ok()
        .headers(headers)
        .body(excelBytes);
}

/**
 * Obtener detalle de facturas pendientes de un cliente con filtros
 */
@GetMapping("/client/{clientId}/pending-invoices")
public ResponseEntity<List<OrderPendingDTO>> getPendingInvoices(
        @PathVariable UUID clientId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(clientBalanceService.getPendingInvoicesByClient(clientId, startDate, endDate));
}
```

### 6️⃣ SERVICIOS A ACTUALIZAR

#### A. `PaymentService` (interface):

```java
public interface PaymentService {
    
    PaymentResponse registerPayment(CreatePaymentRequest request, String ownerUsername);
    
    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);
    
    List<PaymentResponse> getActivePaymentsByOrderId(UUID orderId); // Solo no anulados
    
    PaymentResponse getPaymentById(UUID paymentId);
    
    BigDecimal getTotalPaidForOrder(UUID orderId);
    
    BigDecimal getPendingBalanceForOrder(UUID orderId);
    
    // 🆕 Cambiar de delete físico a anulación
    PaymentResponse cancelPayment(UUID paymentId, String reason, String ownerUsername);
    
    // 🆕 Restaurar pago anulado
    PaymentResponse restorePayment(UUID paymentId, String ownerUsername);
}
```

#### B. `ClientBalanceService` (interface):

```java
public interface ClientBalanceService {
    
    // ... métodos existentes ...
    
    // 🆕 Exportar a Excel
    byte[] exportBalanceToExcel(
        UUID vendedorId, 
        LocalDate startDate, 
        LocalDate endDate, 
        Boolean onlyWithDebt,
        String requestingUsername
    ) throws IOException;
    
    // 🆕 Obtener facturas pendientes con filtros
    List<OrderPendingDTO> getPendingInvoicesByClient(
        UUID clientId, 
        LocalDate startDate, 
        LocalDate endDate
    );
    
    // 🆕 Calcular días de mora
    Integer calculateDaysOverdue(UUID clientId);
    
    // 🆕 Obtener última fecha de pago
    LocalDate getLastPaymentDate(UUID clientId);
}
```

### 7️⃣ LÓGICA DE NEGOCIO

#### A. `PaymentServiceImpl.registerPayment()` - Actualizado:

```java
@Override
public PaymentResponse registerPayment(CreatePaymentRequest request, String ownerUsername) {
    User owner = userRepository.findByUsername(ownerUsername)
            .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

    Order order = ordenRepository.findById(request.orderId())
            .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

    if (order.getEstado() != OrdenStatus.COMPLETADO) {
        throw new BusinessExeption("Solo se pueden registrar pagos en órdenes completadas");
    }

    BigDecimal pendingBalance = getPendingBalanceForOrder(order.getId());
    if (request.amount().compareTo(pendingBalance) > 0) {
        throw new BusinessExeption("El monto del pago ($" + request.amount() +
                ") excede el saldo pendiente ($" + pendingBalance + ")");
    }

    // Si no se especifica fecha real, usar hoy
    LocalDate actualDate = request.actualPaymentDate() != null 
        ? request.actualPaymentDate() 
        : LocalDate.now();

    // Crear el pago
    Payment payment = Payment.builder()
            .order(order)
            .amount(request.amount())
            .paymentDate(LocalDateTime.now()) // Timestamp de registro
            .actualPaymentDate(actualDate) // Fecha real del pago
            .paymentMethod(request.paymentMethod())
            .withinDeadline(request.withinDeadline() != null ? request.withinDeadline() : false)
            .discountApplied(request.discountApplied() != null ? request.discountApplied() : BigDecimal.ZERO)
            .registeredBy(owner)
            .notes(request.notes())
            .isCancelled(false)
            .build();

    Payment savedPayment = paymentRepository.save(payment);

    // Actualizar estado de pago de la orden
    updateOrderPaymentStatus(order);

    log.info("Pago registrado: ${} para orden {} por {} (fecha real: {})",
            request.amount(), order.getId(), ownerUsername, actualDate);

    return toPaymentResponse(savedPayment);
}
```

#### B. `PaymentServiceImpl.cancelPayment()` - Nuevo:

```java
@Override
@Transactional
public PaymentResponse cancelPayment(UUID paymentId, String reason, String ownerUsername) {
    User owner = userRepository.findByUsername(ownerUsername)
            .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
    
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessExeption("Pago no encontrado"));
    
    if (Boolean.TRUE.equals(payment.getIsCancelled())) {
        throw new BusinessExeption("El pago ya está anulado");
    }
    
    Order order = payment.getOrder();
    
    // Marcar como anulado (soft delete)
    payment.setIsCancelled(true);
    payment.setCancelledAt(LocalDateTime.now());
    payment.setCancelledBy(owner);
    payment.setCancellationReason(reason != null ? reason : "Sin razón especificada");
    
    Payment updated = paymentRepository.save(payment);
    
    // Actualizar estado de pago de la orden
    updateOrderPaymentStatus(order);
    
    log.info("Pago {} anulado por {} - Razón: {}", paymentId, ownerUsername, reason);
    
    return toPaymentResponse(updated);
}
```

#### C. `PaymentServiceImpl.restorePayment()` - Nuevo:

```java
@Override
@Transactional
public PaymentResponse restorePayment(UUID paymentId, String ownerUsername) {
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessExeption("Pago no encontrado"));
    
    if (Boolean.FALSE.equals(payment.getIsCancelled())) {
        throw new BusinessExeption("El pago no está anulado");
    }
    
    Order order = payment.getOrder();
    
    // Restaurar
    payment.setIsCancelled(false);
    payment.setCancelledAt(null);
    payment.setCancelledBy(null);
    payment.setCancellationReason(null);
    
    Payment updated = paymentRepository.save(payment);
    
    // Actualizar estado de pago de la orden
    updateOrderPaymentStatus(order);
    
    log.info("Pago {} restaurado por {}", paymentId, ownerUsername);
    
    return toPaymentResponse(updated);
}
```

#### D. Actualizar `PaymentRepository`:

```java
// Agregar consultas para ignorar pagos anulados

@Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.id = :orderId AND p.isCancelled = false")
BigDecimal sumPaymentsByOrderId(@Param("orderId") UUID orderId);

@Query("SELECT p FROM Payment p WHERE p.order.id = :orderId AND p.isCancelled = false ORDER BY p.actualPaymentDate DESC")
List<Payment> findActiveByOrderIdOrderByPaymentDateDesc(@Param("orderId") UUID orderId);

@Query("SELECT p FROM Payment p WHERE p.order.client.id = :clientId AND p.isCancelled = false ORDER BY p.actualPaymentDate DESC")
List<Payment> findByClientIdAndNotCancelled(@Param("clientId") UUID clientId);
```

#### E. `ClientBalanceServiceImpl` - Calcular días de mora:

```java
@Override
public Integer calculateDaysOverdue(UUID clientId) {
    Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new BusinessExeption("Cliente no encontrado"));
    
    List<Order> pendingOrders = ordenRepository.findByCliente(client).stream()
            .filter(o -> o.getEstado() == OrdenStatus.COMPLETADO)
            .filter(o -> o.getPaymentStatus() != PaymentStatus.PAID)
            .toList();
    
    if (pendingOrders.isEmpty()) {
        return 0;
    }
    
    // Obtener la factura más antigua pendiente
    LocalDate oldestInvoiceDate = pendingOrders.stream()
            .map(o -> o.getFecha().toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now());
    
    long daysBetween = ChronoUnit.DAYS.between(oldestInvoiceDate, LocalDate.now());
    
    return (int) Math.max(0, daysBetween);
}

@Override
public LocalDate getLastPaymentDate(UUID clientId) {
    List<Payment> payments = paymentRepository.findByClientIdAndNotCancelled(clientId);
    
    return payments.stream()
            .map(Payment::getActualPaymentDate)
            .max(LocalDate::compareTo)
            .orElse(null);
}
```

#### F. `ClientBalanceServiceImpl.exportBalanceToExcel()` - Nuevo:

```java
@Override
public byte[] exportBalanceToExcel(
        UUID vendedorId, 
        LocalDate startDate, 
        LocalDate endDate, 
        Boolean onlyWithDebt,
        String requestingUsername) throws IOException {
    
    // Validar permisos
    User user = userRepository.findByUsername(requestingUsername)
            .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
    
    // Obtener datos según rol
    List<ClientBalanceDTO> balances;
    if (user.getRole() == Role.OWNER || user.getRole() == Role.ADMIN) {
        if (vendedorId != null) {
            balances = getClientBalancesByVendedor(vendedorId);
        } else {
            balances = getAllClientBalances();
        }
    } else if (user.getRole() == Role.VENDEDOR) {
        balances = getMyClientBalances(requestingUsername);
    } else {
        throw new BusinessExeption("No tienes permisos para exportar cartera");
    }
    
    // Filtrar por fechas si se especifican
    if (startDate != null || endDate != null) {
        balances = balances.stream()
                .filter(b -> filterByDateRange(b, startDate, endDate))
                .toList();
    }
    
    // Filtrar solo con deuda si se solicita
    if (Boolean.TRUE.equals(onlyWithDebt)) {
        balances = balances.stream()
                .filter(b -> b.pendingBalance().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }
    
    // Crear Excel con Apache POI
    return createExcelReport(balances);
}

private byte[] createExcelReport(List<ClientBalanceDTO> balances) throws IOException {
    Workbook workbook = new XSSFWorkbook();
    Sheet sheetDeben = workbook.createSheet("Clientes que Deben");
    Sheet sheetNoDeben = workbook.createSheet("Clientes al Día");
    
    // Separar clientes
    List<ClientBalanceDTO> conDeuda = balances.stream()
            .filter(b -> b.pendingBalance().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(ClientBalanceDTO::vendedorAsignadoName)
                    .thenComparing(ClientBalanceDTO::clientName))
            .toList();
    
    List<ClientBalanceDTO> sinDeuda = balances.stream()
            .filter(b -> b.pendingBalance().compareTo(BigDecimal.ZERO) <= 0)
            .sorted(Comparator.comparing(ClientBalanceDTO::vendedorAsignadoName)
                    .thenComparing(ClientBalanceDTO::clientName))
            .toList();
    
    // Crear headers
    createHeaderRow(sheetDeben);
    createHeaderRow(sheetNoDeben);
    
    // Llenar datos
    fillBalanceSheet(sheetDeben, conDeuda);
    fillBalanceSheet(sheetNoDeben, sinDeuda);
    
    // Auto-size columns
    for (int i = 0; i < 10; i++) {
        sheetDeben.autoSizeColumn(i);
        sheetNoDeben.autoSizeColumn(i);
    }
    
    // Convertir a bytes
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    workbook.write(outputStream);
    workbook.close();
    
    return outputStream.toByteArray();
}

private void createHeaderRow(Sheet sheet) {
    Row headerRow = sheet.createRow(0);
    String[] headers = {
        "Cliente", "Teléfono", "Vendedor", "Total Facturado", 
        "Total Pagado", "Saldo Pendiente", "Última Fecha Pago", 
        "Días Mora", "# Facturas Pendientes", "Estado"
    };
    
    CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
    Font font = sheet.getWorkbook().createFont();
    font.setBold(true);
    headerStyle.setFont(font);
    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    
    for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
    }
}

private void fillBalanceSheet(Sheet sheet, List<ClientBalanceDTO> balances) {
    int rowNum = 1;
    
    for (ClientBalanceDTO balance : balances) {
        Row row = sheet.createRow(rowNum++);
        
        row.createCell(0).setCellValue(balance.clientName());
        row.createCell(1).setCellValue(balance.clientPhone());
        row.createCell(2).setCellValue(balance.vendedorAsignadoName());
        row.createCell(3).setCellValue(balance.totalOrders().doubleValue());
        row.createCell(4).setCellValue(balance.totalPaid().doubleValue());
        row.createCell(5).setCellValue(balance.pendingBalance().doubleValue());
        row.createCell(6).setCellValue(
            balance.lastPaymentDate() != null 
                ? balance.lastPaymentDate().toString() 
                : "Sin pagos"
        );
        row.createCell(7).setCellValue(balance.daysOverdue() != null ? balance.daysOverdue() : 0);
        row.createCell(8).setCellValue(balance.pendingOrdersCount());
        row.createCell(9).setCellValue(
            balance.pendingBalance().compareTo(BigDecimal.ZERO) > 0 
                ? "DEBE" 
                : "AL DÍA"
        );
    }
}
```

### 8️⃣ ACTUALIZACIÓN DEL MÉTODO calculateClientBalance:

```java
private ClientBalanceDTO calculateClientBalance(Client client) {
    // ... código existente ...
    
    // 🆕 Calcular última fecha de pago
    LocalDate lastPaymentDate = getLastPaymentDate(client.getId());
    
    // 🆕 Calcular días de mora
    Integer daysOverdue = calculateDaysOverdue(client.getId());
    
    return new ClientBalanceDTO(
        client.getId(),
        client.getNombre(),
        client.getTelefono(),
        client.getRepresentanteLegal(),
        client.getVendedorAsignado() != null ? client.getVendedorAsignado().getUsername() : null,
        client.getCreditLimit(),
        initialBalance,
        totalOrders,
        totalPaid,
        pendingBalance,
        client.getBalanceFavor(),
        pendingOrders.size(),
        pendingOrders,
        lastPaymentDate, // 🆕
        daysOverdue // 🆕
    );
}
```

---

## 📊 MIGRACIÓN DE BASE DE DATOS

### Script SQL para aplicar cambios:

```sql
-- 1. Agregar nuevas columnas a payments
ALTER TABLE payments 
ADD COLUMN payment_method VARCHAR(50) DEFAULT 'EFECTIVO',
ADD COLUMN actual_payment_date DATE,
ADD COLUMN is_cancelled BOOLEAN DEFAULT FALSE,
ADD COLUMN cancelled_at TIMESTAMP,
ADD COLUMN cancelled_by UUID,
ADD COLUMN cancellation_reason TEXT;

-- 2. Crear constraint para cancelled_by
ALTER TABLE payments 
ADD CONSTRAINT fk_payment_cancelled_by 
FOREIGN KEY (cancelled_by) REFERENCES users(id);

-- 3. Migrar datos existentes
UPDATE payments 
SET actual_payment_date = DATE(payment_date)
WHERE actual_payment_date IS NULL;

-- 4. Crear índices para mejorar rendimiento
CREATE INDEX idx_payments_actual_date ON payments(actual_payment_date);
CREATE INDEX idx_payments_is_cancelled ON payments(is_cancelled);
CREATE INDEX idx_payments_order_not_cancelled ON payments(order_id, is_cancelled);

-- 5. Verificar integridad
SELECT COUNT(*) as total_payments,
       SUM(CASE WHEN is_cancelled THEN 1 ELSE 0 END) as cancelled_payments,
       SUM(CASE WHEN is_cancelled = FALSE THEN 1 ELSE 0 END) as active_payments
FROM payments;
```

---

## 🎨 PROMPT PARA FRONTEND

```markdown
# 🎯 ACTUALIZACIÓN FRONTEND - SISTEMA DE CARTERA Y PAGOS

## 📝 CONTEXTO
Se han implementado mejoras en el backend para el sistema de cartera y pagos. Ahora necesitamos actualizar el frontend para aprovechar estas nuevas funcionalidades.

## 🆕 NUEVAS FUNCIONALIDADES DEL BACKEND

### 1. Registro de Pagos Mejorado
- Ahora se puede especificar **fecha manual del pago** (para pagos realizados días antes)
- Se debe seleccionar **método de pago**: Efectivo, Transferencia, Cheque, Tarjeta, Crédito, Otro
- El sistema registra automáticamente el timestamp de registro del pago

**Endpoint:** `POST /api/owner/payments`
**Request actualizado:**
```json
{
  "orderId": "uuid",
  "amount": 50000.00,
  "paymentMethod": "TRANSFERENCIA",
  "actualPaymentDate": "2026-02-10",
  "withinDeadline": true,
  "discountApplied": 0,
  "notes": "Transferencia Bancolombia"
}
```

### 2. Historial de Pagos con Auditoría
- Cada pago ahora muestra:
  - Fecha de registro (timestamp automático)
  - Fecha real del pago (definida por el owner)
  - Método de pago
  - Usuario que registró
  - Estado (activo/anulado)

**Response actualizado:**
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "amount": 50000.00,
  "paymentDate": "2026-02-15T10:30:00",
  "actualPaymentDate": "2026-02-10",
  "paymentMethod": "TRANSFERENCIA",
  "withinDeadline": true,
  "discountApplied": 0,
  "registeredByUsername": "owner",
  "createdAt": "2026-02-15T10:30:00",
  "notes": "Transferencia Bancolombia",
  "isCancelled": false,
  "cancelledAt": null,
  "cancelledByUsername": null,
  "cancellationReason": null
}
```

### 3. Anulación de Pagos con Auditoría
- Los pagos NO se eliminan, se anulan (soft delete)
- Se registra quién anuló, cuándo y por qué
- Se pueden restaurar si fue un error

**Nuevo endpoint:** `PUT /api/owner/payments/{paymentId}/cancel?reason=Pago duplicado`

### 4. Panel de Saldos Mejorado
Ahora incluye:
- **Última fecha de pago**
- **Días de mora**
- **Lista de facturas pendientes por cliente**
- **Historial de pagos por factura**

**Response actualizado de `/api/balance`:**
```json
{
  "clientId": "uuid",
  "clientName": "Cliente X",
  "clientPhone": "3001234567",
  "vendedorAsignadoName": "vendedor1",
  "totalOrders": 500000.00,
  "totalPaid": 300000.00,
  "pendingBalance": 200000.00,
  "lastPaymentDate": "2026-02-10",
  "daysOverdue": 15,
  "pendingOrdersCount": 3,
  "pendingOrders": [
    {
      "orderId": "uuid",
      "invoiceNumber": 1001,
      "fecha": "2026-01-15T10:00:00",
      "total": 150000.00,
      "paidAmount": 50000.00,
      "pendingAmount": 100000.00,
      "paymentStatus": "PARTIAL",
      "payments": [
        {
          "id": "uuid",
          "amount": 50000.00,
          "actualPaymentDate": "2026-01-20",
          "paymentMethod": "EFECTIVO",
          "registeredByUsername": "owner",
          "isCancelled": false
        }
      ]
    }
  ]
}
```

### 5. Exportación a Excel
**Nuevo endpoint:** `GET /api/balance/export/excel`

**Parámetros:**
- `vendedorId` (opcional): Filtrar por vendedor
- `startDate` (opcional): Fecha inicial (formato: YYYY-MM-DD)
- `endDate` (opcional): Fecha final
- `onlyWithDebt` (opcional): true/false - Solo clientes que deben

**Response:** Archivo Excel con dos hojas:
1. "Clientes que Deben"
2. "Clientes al Día"

Columnas: Cliente, Teléfono, Vendedor, Total Facturado, Total Pagado, Saldo Pendiente, Última Fecha Pago, Días Mora, # Facturas Pendientes, Estado

### 6. Detalle de Facturas Pendientes con Filtros
**Nuevo endpoint:** `GET /api/balance/client/{clientId}/pending-invoices?startDate=2026-01-01&endDate=2026-02-15`

## 🎨 COMPONENTES A CREAR/ACTUALIZAR

### 1. Formulario de Registro de Pago
**Ubicación:** `components/payments/PaymentForm.tsx`

**Campos:**
- Order ID (autocompletado)
- Monto
- **Método de pago** (dropdown): Efectivo, Transferencia, Cheque, Tarjeta, Crédito, Otro
- **Fecha del pago** (datepicker) - Por defecto hoy, pero editable
- Descuento aplicado
- ¿Dentro del plazo? (checkbox)
- Notas

**Validaciones:**
- Monto debe ser positivo
- Monto no puede exceder saldo pendiente
- Método de pago es obligatorio

### 2. Modal de Historial de Pagos por Factura
**Ubicación:** `components/payments/PaymentHistoryModal.tsx`

**Muestra:**
- Timeline de pagos ordenados por fecha
- Para cada pago:
  - Badge con estado (Activo/Anulado)
  - Monto
  - Fecha real del pago (destacada)
  - Método de pago con ícono
  - Usuario que registró
  - Fecha de registro (en texto pequeño)
  - Notas
  - Botón "Anular" (solo owner, solo si está activo)
  - Si está anulado: razón de anulación

### 3. Panel de Saldos Mejorado
**Ubicación:** `pages/Cartera.tsx`

**Vista Principal (tabla):**
- Cliente
- Teléfono
- Vendedor
- Total Facturado
- Total Pagado
- Saldo Pendiente
- **Última Fecha Pago** 🆕
- **Días de Mora** 🆕 (con badge de color: verde <15, amarillo 15-30, rojo >30)
- # Facturas Pendientes
- Estado (Badge: "DEBE" rojo / "AL DÍA" verde)
- Acción: Ver Detalle

**Filtros:**
- Por vendedor (dropdown)
- Rango de fechas
- Solo con deuda (checkbox)
- Botón "Exportar Excel"

### 4. Modal de Detalle de Cliente
**Ubicación:** `components/balance/ClientBalanceDetailModal.tsx`

**Contenido:**
- Información del cliente (nombre, teléfono, vendedor)
- Resumen de saldo (tarjetas con totales)
- **Lista de facturas pendientes** (expandible)
  - Para cada factura:
    - # Factura
    - Fecha
    - Total
    - Pagado
    - Pendiente
    - Estado de pago (badge)
    - Botón "Ver Pagos" → Abre el Modal de Historial de Pagos

### 5. Botón de Anulación de Pago
**Ubicación:** Dentro de `PaymentHistoryModal`

**Comportamiento:**
- Al hacer clic: Abre un modal pequeño pidiendo razón
- Campos:
  - Razón de anulación (textarea, obligatorio)
  - Botones: Cancelar / Confirmar Anulación
- Al confirmar:
  - Llama a `PUT /api/owner/payments/{paymentId}/cancel`
  - Actualiza el historial automáticamente
  - Muestra toast de éxito

### 6. Exportación Excel
**Ubicación:** Botón en `pages/Cartera.tsx`

**Comportamiento:**
- Al hacer clic: Descarga automáticamente el Excel
- Aplica los filtros activos en la vista
- Muestra toast mientras descarga

## 🎨 DISEÑO SUGERIDO

### Colores para Días de Mora:
- 0-14 días: Verde (#10b981)
- 15-30 días: Amarillo (#f59e0b)
- >30 días: Rojo (#ef4444)

### Íconos para Métodos de Pago:
- Efectivo: 💵
- Transferencia: 🏦
- Cheque: 📝
- Tarjeta: 💳
- Crédito: 📊
- Otro: 🔖

### Timeline de Pagos:
- Usar componente de línea de tiempo vertical
- Pagos activos: círculo verde
- Pagos anulados: círculo rojo tachado
- Conectados con línea gris

## 📱 RESPONSIVE
- Tabla de cartera: Scroll horizontal en móvil
- Modales: Full screen en móvil, centrados en desktop
- Formularios: Stack vertical en móvil

## 🔒 PERMISOS
- **Owner**: Puede registrar pagos, anularlos, ver todo, exportar
- **Admin**: Puede ver todo, exportar (NO puede registrar/anular pagos)
- **Vendedor**: Solo ve sus clientes, puede exportar sus clientes

## 🧪 TESTING
Probar:
1. Registro de pago con fecha pasada
2. Anulación de pago y verificar que no afecte saldo
3. Restauración de pago anulado
4. Exportación Excel con filtros
5. Vista de historial con pagos anulados
6. Cálculo de días de mora
7. Badge de estado según saldo

## 🚀 PRIORIDAD DE IMPLEMENTACIÓN
1. Actualizar formulario de registro de pago (método y fecha)
2. Actualizar historial de pagos (mostrar nueva info)
3. Implementar anulación de pagos
4. Agregar columnas "Última fecha" y "Días mora" a tabla
5. Implementar exportación Excel
6. Mejorar modal de detalle de cliente
```

---

## ✅ CHECKLIST DE IMPLEMENTACIÓN

### Backend:
- [ ] Crear enum `PaymentMethod`
- [ ] Actualizar entidad `Payment` con nuevos campos
- [ ] Ejecutar script de migración SQL
- [ ] Actualizar `CreatePaymentRequest` DTO
- [ ] Actualizar `PaymentResponse` DTO
- [ ] Actualizar `ClientBalanceDTO` DTO
- [ ] Crear `ClientBalanceExportDTO`
- [ ] Implementar `cancelPayment()` en `PaymentService`
- [ ] Implementar `restorePayment()` en `PaymentService`
- [ ] Actualizar `registerPayment()` para soportar fecha manual
- [ ] Implementar `calculateDaysOverdue()`
- [ ] Implementar `getLastPaymentDate()`
- [ ] Implementar `exportBalanceToExcel()`
- [ ] Actualizar queries de `PaymentRepository` (ignorar anulados)
- [ ] Agregar nuevos endpoints en controllers
- [ ] Actualizar tests unitarios
- [ ] Probar en Postman/Insomnia

### Frontend:
- [ ] Actualizar formulario de registro de pago
- [ ] Crear selector de método de pago
- [ ] Agregar datepicker para fecha manual
- [ ] Actualizar componente de historial de pagos
- [ ] Implementar modal de anulación
- [ ] Agregar columnas "Última fecha" y "Días mora"
- [ ] Implementar exportación Excel
- [ ] Crear filtros de fecha
- [ ] Mejorar modal de detalle de cliente
- [ ] Implementar badges de estado
- [ ] Testing en diferentes roles

---

## 📈 BENEFICIOS DE ESTA IMPLEMENTACIÓN

✅ **Trazabilidad completa** - No se pierde historial de pagos
✅ **Auditoría robusta** - Se sabe quién, cuándo y por qué anuló un pago
✅ **Flexibilidad** - Se pueden registrar pagos con fecha pasada
✅ **Información completa** - Método de pago, días de mora, última fecha
✅ **Exportación profesional** - Excel con datos agrupados y formateados
✅ **Sin afectación de lógica existente** - Todo es extensión, no modificación
✅ **Fácil de usar** - UI intuitiva para el owner

---

## 🎯 CONCLUSIÓN

Esta propuesta:
1. ✅ Mantiene toda la estructura actual
2. ✅ Agrega auditoría completa sin eliminar datos
3. ✅ Permite fecha manual de pago
4. ✅ Registra método de pago
5. ✅ Calcula días de mora automáticamente
6. ✅ Permite exportar a Excel con filtros avanzados
7. ✅ Mejora la UI con información relevante
8. ✅ No rompe la lógica de stock ni anulaciones

**¿Procedemos con la implementación? 🚀**

