# 🚀 GUÍA RÁPIDA DE APLICACIÓN - MEJORAS CARTERA Y PAGOS

## 📋 RESUMEN EJECUTIVO

Se han implementado mejoras al sistema de cartera y pagos que incluyen:
- ✅ Registro de método de pago y fecha manual
- ✅ Anulación de pagos con auditoría (soft delete)
- ✅ Cálculo automático de días de mora
- ✅ Última fecha de pago por cliente
- ✅ Exportación de cartera a Excel con filtros
- ✅ Historial completo de pagos por factura

**Estado actual:** Backend implementado y compilando ✅  
**Pendiente:** Migración SQL + Frontend

---

## ⚡ PASOS DE APLICACIÓN

### 1️⃣ BACKUP DE BASE DE DATOS (CRÍTICO)

```bash
# PostgreSQL
pg_dump -h localhost -U usuario -d vitalexa > backup_$(date +%Y%m%d_%H%M%S).sql

# Verificar que el backup se creó correctamente
ls -lh backup_*.sql
```

### 2️⃣ EJECUTAR MIGRACIÓN SQL

```bash
# Ejecutar el script de migración
psql -h localhost -U usuario -d vitalexa < migration_mejoras_cartera_pagos.sql
```

**Verifica que se ejecutó correctamente:**
```sql
-- Verificar que las columnas existen
SELECT 
    column_name, 
    data_type, 
    is_nullable 
FROM information_schema.columns 
WHERE table_name = 'payments' 
AND column_name IN (
    'payment_method', 
    'actual_payment_date', 
    'is_cancelled', 
    'cancelled_at', 
    'cancelled_by', 
    'cancellation_reason'
);

-- Debe retornar 6 filas
```

**Verificar índices:**
```sql
SELECT indexname 
FROM pg_indexes 
WHERE tablename = 'payments' 
AND indexname LIKE 'idx_payments%';

-- Debe retornar 4 índices
```

### 3️⃣ DESPLEGAR BACKEND

#### Opción A: Desarrollo local
```bash
cd "C:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa"
.\mvnw.cmd spring-boot:run
```

#### Opción B: Generar JAR para producción
```bash
.\mvnw.cmd clean package -DskipTests
```

El archivo JAR estará en: `target/Sistema_gestion_vitalexa-0.0.1-SNAPSHOT.jar`

#### Opción C: Docker (si aplica)
```bash
docker-compose up -d --build
```

### 4️⃣ VERIFICAR ENDPOINTS

Una vez desplegado, verifica que los endpoints funcionan:

```bash
# Obtener pagos de una orden (reemplaza {orderId} con un UUID real)
curl -X GET "http://localhost:8080/api/owner/payments/order/{orderId}" \
  -H "Authorization: Bearer {token}"

# Exportar cartera a Excel
curl -X GET "http://localhost:8080/api/balances/export/excel" \
  -H "Authorization: Bearer {token}" \
  -o cartera_test.xlsx

# Verificar días de mora (reemplaza {clientId})
curl -X GET "http://localhost:8080/api/balances/client/{clientId}/days-overdue" \
  -H "Authorization: Bearer {token}"
```

### 5️⃣ IMPLEMENTAR FRONTEND

Usar el archivo `PROMPT_FRONTEND_CARTERA_PAGOS.md` como guía completa.

**Componentes prioritarios:**
1. Actualizar formulario de registro de pago
2. Actualizar tabla de cartera (agregar columnas)
3. Implementar anulación de pagos
4. Implementar exportación Excel

---

## 📊 TESTING MANUAL

### Test 1: Registrar pago con fecha manual

**Endpoint:** `POST /api/owner/payments`

**Body:**
```json
{
  "orderId": "uuid-de-orden-completada",
  "amount": 50000,
  "paymentMethod": "TRANSFERENCIA",
  "actualPaymentDate": "2026-02-10",
  "notes": "Pago realizado hace una semana"
}
```

**Resultado esperado:**
- Status: 201 Created
- Response incluye `actualPaymentDate: "2026-02-10"`
- Response incluye `paymentMethod: "TRANSFERENCIA"`
- El pago se registra con `isCancelled: false`

### Test 2: Anular un pago

**Endpoint:** `PUT /api/owner/payments/{paymentId}/cancel?reason=Pago duplicado`

**Resultado esperado:**
- Status: 200 OK
- Response muestra `isCancelled: true`
- `cancelledAt` tiene timestamp
- `cancelledByUsername` muestra el usuario
- `cancellationReason: "Pago duplicado"`
- El saldo de la orden se actualiza automáticamente

### Test 3: Restaurar un pago

**Endpoint:** `PUT /api/owner/payments/{paymentId}/restore`

**Resultado esperado:**
- Status: 200 OK
- Response muestra `isCancelled: false`
- `cancelledAt`, `cancelledBy`, `cancellationReason` vuelven a `null`
- El saldo de la orden se recalcula

### Test 4: Exportar cartera

**Endpoint:** `GET /api/balances/export/excel?onlyWithDebt=true`

**Resultado esperado:**
- Status: 200 OK
- Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
- Archivo descargado: `cartera_clientes_YYYY-MM-DD.xlsx`
- El Excel contiene 2 hojas: "Clientes que Deben" y "Clientes al Día"
- Cada hoja tiene 10 columnas
- Los datos están correctos y formateados

### Test 5: Ver días de mora

**Endpoint:** `GET /api/balances`

**Resultado esperado:**
- Status: 200 OK
- Cada `ClientBalanceDTO` incluye `daysOverdue` (número)
- Cada `ClientBalanceDTO` incluye `lastPaymentDate` (fecha o null)

---

## 🔍 TROUBLESHOOTING

### Error: "cannot find symbol: class PaymentMethod"

**Causa:** El archivo enum no se compiló correctamente

**Solución:**
```bash
# Limpiar y recompilar
.\mvnw.cmd clean compile
```

### Error: "column 'payment_method' does not exist"

**Causa:** La migración SQL no se ejecutó

**Solución:**
```bash
# Ejecutar la migración
psql -h localhost -U usuario -d vitalexa < migration_mejoras_cartera_pagos.sql
```

### Error: "Expected 15 arguments but found 9"

**Causa:** El DTO `PaymentResponse` no está actualizado

**Solución:**
- Verificar que `PaymentResponse.java` tenga todos los 15 campos
- Limpiar y recompilar

### El Excel se descarga corrupto

**Causa:** Problema con Apache POI o tamaño de respuesta

**Solución:**
1. Verificar que Apache POI esté en el classpath
2. Probar con menos datos (agregar filtro `onlyWithDebt=true`)
3. Verificar logs del servidor

---

## 📁 ARCHIVOS IMPORTANTES

### Documentación:
- `PROPUESTA_MEJORA_CARTERA_PAGOS.md` - Propuesta arquitectónica completa
- `RESUMEN_IMPLEMENTACION_CARTERA_PAGOS.md` - Resumen de lo implementado
- `PROMPT_FRONTEND_CARTERA_PAGOS.md` - Guía completa para frontend
- `GUIA_RAPIDA_APLICACION.md` - Este archivo

### Código:
- `src/main/java/org/example/sistema_gestion_vitalexa/enums/PaymentMethod.java`
- `src/main/java/org/example/sistema_gestion_vitalexa/entity/Payment.java`
- `src/main/java/org/example/sistema_gestion_vitalexa/service/impl/PaymentServiceImpl.java`
- `src/main/java/org/example/sistema_gestion_vitalexa/service/impl/ClientBalanceServiceImpl.java`
- `src/main/java/org/example/sistema_gestion_vitalexa/controller/owner/PaymentOwnerController.java`
- `src/main/java/org/example/sistema_gestion_vitalexa/controller/ClientBalanceController.java`

### Migración:
- `migration_mejoras_cartera_pagos.sql`

---

## ⚠️ PRECAUCIONES

### Antes de aplicar en producción:

1. ✅ **Backup completo** de la base de datos
2. ✅ **Probar en ambiente de desarrollo** primero
3. ✅ **Verificar** que la migración SQL se ejecute sin errores
4. ✅ **Validar** que todos los tests pasen
5. ✅ **Avisar** a los usuarios del mantenimiento
6. ✅ **Tener plan de rollback** preparado

### Durante la implementación:

- ⏰ Hacer el deployment en horario de baja actividad
- 👥 Tener soporte disponible
- 📝 Documentar cualquier problema que surja
- 🔄 Estar listo para hacer rollback si algo falla

### Después de aplicar:

- ✅ Verificar que todos los endpoints respondan correctamente
- ✅ Probar registro de pagos con fecha manual
- ✅ Probar anulación de pagos
- ✅ Verificar exportación de Excel
- ✅ Validar cálculo de días de mora
- ✅ Capacitar a los usuarios en las nuevas funcionalidades

---

## 🔙 PLAN DE ROLLBACK

Si algo sale mal, seguir estos pasos:

### 1. Restaurar backup de base de datos
```bash
# Detener la aplicación
docker-compose down
# o
kill <proceso-java>

# Restaurar backup
psql -h localhost -U usuario -d vitalexa < backup_YYYYMMDD_HHMMSS.sql
```

### 2. Revertir cambios en el código
```bash
git checkout HEAD~1  # O el commit anterior estable
.\mvnw.cmd clean package -DskipTests
```

### 3. Reiniciar aplicación
```bash
.\mvnw.cmd spring-boot:run
# o
docker-compose up -d
```

---

## 📞 SOPORTE

Si encuentras problemas:

1. **Revisar logs del servidor:**
   ```bash
   tail -f logs/spring.log
   ```

2. **Revisar logs de PostgreSQL:**
   ```bash
   tail -f /var/log/postgresql/postgresql-XX-main.log
   ```

3. **Consultar la documentación:**
   - `PROPUESTA_MEJORA_CARTERA_PAGOS.md` - Arquitectura completa
   - `RESUMEN_IMPLEMENTACION_CARTERA_PAGOS.md` - Detalles técnicos

---

## ✅ CHECKLIST FINAL

Antes de dar por terminada la implementación:

### Backend:
- [ ] Migración SQL ejecutada sin errores
- [ ] Aplicación compilando correctamente
- [ ] Todos los endpoints respondiendo
- [ ] Tests manuales pasando
- [ ] Logs sin errores críticos

### Base de Datos:
- [ ] Backup realizado
- [ ] Columnas nuevas creadas
- [ ] Índices creados
- [ ] Foreign keys funcionando
- [ ] Datos migrados correctamente

### Funcionalidades:
- [ ] Se puede registrar pago con fecha manual
- [ ] Se puede seleccionar método de pago
- [ ] Se puede anular un pago
- [ ] Se puede restaurar un pago anulado
- [ ] Se calculan días de mora correctamente
- [ ] Se muestra última fecha de pago
- [ ] Se exporta cartera a Excel
- [ ] Los filtros de Excel funcionan

### Frontend (pendiente):
- [ ] Formulario de pago actualizado
- [ ] Tabla de cartera con nuevas columnas
- [ ] Modal de anulación implementado
- [ ] Exportación Excel implementada
- [ ] Tests E2E pasando

---

## 🎯 PRÓXIMOS PASOS

1. ✅ **Aplicar migración SQL** - ¡Hazlo ahora!
2. 🔄 **Desplegar backend** - Ya está listo
3. 📱 **Actualizar frontend** - Usa el prompt proporcionado
4. 🧪 **Testing exhaustivo** - Probar todos los casos de uso
5. 📚 **Capacitar usuarios** - Explicar nuevas funcionalidades
6. 📊 **Monitorear** - Observar logs y rendimiento

---

**Fecha:** 2026-02-17  
**Estado:** ✅ Backend completado - Listo para aplicar  
**Tiempo estimado de aplicación:** 30-60 minutos  
**Riesgo:** Bajo (con backup y rollback plan)

¡Éxito con el deployment! 🚀

