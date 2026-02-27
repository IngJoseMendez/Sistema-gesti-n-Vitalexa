# 📋 Cambios en el Backend — Nómina, Permisos y Restricciones

## Contexto general
Se implementaron 3 grupos de cambios en el backend. A continuación se describen todos los endpoints nuevos y los comportamientos modificados para que el frontend los integre correctamente.

---

## 1️⃣ Restricciones de rol: Admin ya NO puede anular ventas ni editar facturas

### ¿Qué cambió?
- El endpoint `PATCH /api/admin/orders/{id}/status` ahora requiere rol **OWNER**. Si un usuario con rol **ADMIN** lo llama, recibirá `403 Forbidden`.
- El endpoint `PUT /api/admin/orders/{id}` ya requería OWNER — sigue igual.
- Todo el controller `POST /api/owner/invoices` y `PUT /api/owner/invoices/{id}` ahora requiere exclusivamente rol **OWNER**. El **ADMIN** ya no puede crear ni editar facturas históricas.

### Impacto en UI:
- Ocultar o deshabilitar en la vista de **Admin** el botón de anular órdenes.
- Ocultar o deshabilitar en la vista de **Admin** el formulario/botón de crear y editar facturas históricas.
- Esas acciones solo deben mostrarse cuando el rol del usuario autenticado sea `OWNER`.

---

## 2️⃣ Vendedoras ya pueden descargar su reporte Excel personalizado

No hubo cambio en el endpoint. Las vendedoras ya tenían acceso, pero se confirma el comportamiento:

- `GET /api/reports/export/complete/excel?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`  
  → Si el rol es `VENDEDOR`, el backend automáticamente filtra y devuelve **solo los datos de esa vendedora**.
- `GET /api/reports/export/vendor/{vendedorId}/excel?startDate=&endDate=`  
  → Una vendedora solo puede descargar con su **propio** `vendedorId`. Si intenta con otro ID, recibe `403`.

### Impacto en UI:
- Mostrar el botón de descarga de Excel en el dashboard/perfil de la vendedora.
- Al llamar el endpoint usar el `id` del usuario autenticado como `vendedorId`.

---

## 3️⃣ Sistema de Nómina Mensual — Nuevo módulo completo

### Modelo de datos (resumen)
Cada vendedor tiene:
- **Configuración de nómina** (`VendorPayrollConfig`): salario base y porcentajes configurables.
- **Nómina mensual** (`Payroll`): calculada por el Owner al finalizar cada mes.

### Reglas de negocio (para mostrar en UI explicativamente)
| Componente | Condición | Valor |
|---|---|---|
| Salario base | Siempre | Configurado por el Owner |
| Comisión por ventas | Solo si cumplió la meta del mes | `totalVendido × salesCommissionPct` (default 1.5%) |
| Comisión por recaudo | Solo si recaudó ≥ `collectionThresholdPct` (default 80%) de lo vendido el mes anterior | `totalRecaudado × collectionCommissionPct` (default 3%) |
| Comisión general | Solo si está habilitada para ese vendedor | `sumaDeTodosLasMetas × generalCommissionPct` (default 2%) |

---

## 🔷 Endpoints del OWNER — Configuración de Nómina

### GET `/api/owner/payroll/config`
Lista la configuración de nómina de todos los vendedores activos.

**Response (array):**
```json
[
  {
    "id": "uuid",
    "vendedorId": "uuid",
    "vendedorUsername": "string",
    "baseSalary": 1500000.00,
    "salesCommissionPct": 0.0150,
    "collectionCommissionPct": 0.0300,
    "collectionThresholdPct": 0.8000,
    "generalCommissionEnabled": false,
    "generalCommissionPct": 0.0200
  }
]
```

---

### GET `/api/owner/payroll/config/{vendedorId}`
Obtiene la configuración de nómina de un vendedor específico.

**Response:** mismo objeto del array anterior.

---

### POST `/api/owner/payroll/config`
Crea o actualiza la configuración de nómina de un vendedor.  
Todos los campos excepto `vendedorId` son opcionales — solo se actualizan los que se envíen.

**Request body:**
```json
{
  "vendedorId": "uuid",
  "baseSalary": 1500000.00,
  "salesCommissionPct": 0.0150,
  "collectionCommissionPct": 0.0300,
  "collectionThresholdPct": 0.8000,
  "generalCommissionEnabled": true,
  "generalCommissionPct": 0.0200
}
```
**Response:** el objeto `VendorPayrollConfigResponse` actualizado.

---

## 🔷 Endpoints del OWNER — Cálculo de Nómina

### POST `/api/owner/payroll/calculate`
Calcula (o recalcula) la nómina de un vendedor para un mes y año específico.  
Si ya existía una nómina para ese mes/año, la sobreescribe.

**Request body:**
```json
{
  "vendedorId": "uuid",
  "month": 1,
  "year": 2026,
  "notes": "Nómina enero 2026"
}
```

**Response:** objeto `PayrollResponse` (ver estructura abajo).

---

### POST `/api/owner/payroll/calculate-all?month=1&year=2026`
Calcula la nómina de **todos los vendedores activos** para el mes/año indicado.

**Response:** array de `PayrollResponse`.

---

### GET `/api/owner/payroll?month=1&year=2026`
Lista las nóminas calculadas de todos los vendedores para un mes/año.

**Response:** array de `PayrollResponse`.

---

### GET `/api/owner/payroll/{vendedorId}?month=1&year=2026`
Obtiene la nómina de un vendedor específico en un mes/año.

**Response:** objeto `PayrollResponse`.

---

### GET `/api/owner/payroll/{vendedorId}/history`
Historial completo de nóminas de un vendedor (ordenado del más reciente al más antiguo).

**Response:** array de `PayrollResponse`.

---

## 🔶 Endpoints del VENDEDOR — Solo lectura propia

### GET `/api/vendedor/payroll?month=1&year=2026`
La vendedora consulta su propia nómina de un mes/año específico.  
Si el Owner aún no ha calculado la nómina, devuelve `404`.

**Response:** objeto `PayrollResponse`.

---

### GET `/api/vendedor/payroll/history`
La vendedora consulta su historial completo de nóminas.

**Response:** array de `PayrollResponse`.

---

## 📦 Estructura completa de `PayrollResponse`

```json
{
  "id": "uuid",
  "vendedorId": "uuid",
  "vendedorUsername": "string",
  "month": 1,
  "year": 2026,

  "baseSalary": 1500000.00,

  "salesGoalTarget": 20000000.00,
  "totalSold": 22000000.00,
  "salesGoalMet": true,
  "salesCommissionPct": 0.0150,
  "salesCommissionAmount": 330000.00,

  "prevMonthTotalSold": 18000000.00,
  "totalCollected": 15000000.00,
  "collectionPct": 83.3333,
  "collectionGoalMet": true,
  "collectionCommissionPct": 0.0300,
  "collectionCommissionAmount": 450000.00,

  "generalCommissionEnabled": true,
  "totalGlobalGoals": 80000000.00,
  "generalCommissionPct": 0.0200,
  "generalCommissionAmount": 1600000.00,

  "totalCommissions": 2380000.00,
  "totalPayout": 3880000.00,

  "notes": "Nómina enero 2026",
  "createdAt": "2026-02-01T10:00:00",
  "updatedAt": "2026-02-01T10:00:00"
}
```

### Notas sobre los campos:
- `salesCommissionPct` y demás porcentajes están en **decimal** (0.0150 = 1.5%). Multiplicar por 100 para mostrar en UI.
- `collectionPct` está en **porcentaje** (83.33 = 83.33%). Usar directamente en UI.
- `collectionThresholdPct` en la config también es **decimal** (0.80 = 80%).
- Si `salesGoalMet = false` → `salesCommissionAmount` siempre es `0`.
- Si `collectionGoalMet = false` → `collectionCommissionAmount` siempre es `0`.
- Si `generalCommissionEnabled = false` → `generalCommissionAmount` siempre es `0`.
- `totalPayout = baseSalary + totalCommissions`.

---

## 🔐 Resumen de roles por funcionalidad

| Funcionalidad | OWNER | ADMIN | VENDEDOR |
|---|:---:|:---:|:---:|
| Anular órdenes | ✅ | ❌ | ❌ |
| Editar órdenes | ✅ | ❌ | ❌ |
| Crear/editar facturas históricas | ✅ | ❌ | ❌ |
| Descargar Excel propio | ✅ | ✅ | ✅ |
| Configurar nómina vendedores | ✅ | ❌ | ❌ |
| Calcular nóminas | ✅ | ❌ | ❌ |
| Ver nóminas de todos | ✅ | ❌ | ❌ |
| Ver propia nómina | ✅ | ❌ | ✅ |

