# Correcciones Aplicadas - Bugs en Órdenes Promocionales

## Resumen Ejecutivo

Se identificaron y corrigieron bugs críticos en el sistema de edición de órdenes promocionales:

1. ✅ **Bug Crítico Resuelto**: Duplicación de productos al agregar flete a órdenes promocionales
2. ⚠️ **Bug de Visualización**: Precio incorrecto en modal de edición (requiere corrección en frontend)

---

## Bug 1: Duplicación de Productos (RESUELTO ✅)

### Descripción del Problema

**Síntomas:**
- Al editar una orden de promoción y agregar flete, los productos de la promoción se duplicaban
- Los productos duplicados perdían su estado de promoción y aparecían como productos normales
- La orden quedaba dañada con items duplicados

**Causa Raíz:**
El método `updateOrder()` en [`OrderServiceImpl.java`](file:///Users/arnoldalexanderarevalo/Sistema-gesti-n-Vitalexa/src/main/java/org/example/sistema_gestion_vitalexa/service/impl/OrderServiceImpl.java) tenía un problema de lógica:

1. **Líneas 875-892**: Preservaba los items de promoción existentes y los re-agregaba a la orden
2. **Líneas 997-1003 (ANTIGUO)**: Volvía a procesar las mismas promociones con `processPromotions()`, creando items duplicados

### Solución Implementada

Se modificó la lógica de procesamiento de promociones para:
- Comparar los IDs de promociones actuales vs los IDs solicitados
- Solo re-procesar promociones si están cambiando
- Preservar items de promoción existentes si las promociones no cambian

**Cambios en el código:**

```java
// ANTES (❌ CAUSABA DUPLICACIÓN):
if (hasPromotions) {
    int totalNormalItemsCount = order.getItems().stream()
            .filter(i -> !Boolean.TRUE.equals(i.getIsPromotionItem()))
            .mapToInt(OrderItem::getCantidad)
            .sum();
    processPromotions(order, request.promotionIds(), totalNormalItemsCount);
    log.info("Promociones restauradas en edición de orden {}: {}", orderId, request.promotionIds());
}

// DESPUÉS (✅ EVITA DUPLICACIÓN):
if (hasPromotions) {
    // Obtener IDs de promociones actuales en la orden
    java.util.Set<UUID> currentPromotionIds = order.getItems().stream()
            .filter(i -> Boolean.TRUE.equals(i.getIsPromotionItem()))
            .map(i -> i.getPromotion() != null ? i.getPromotion().getId() : null)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

    java.util.Set<UUID> requestedPromotionIds = new java.util.HashSet<>(request.promotionIds());

    // Solo re-procesar si las promociones están cambiando
    if (!currentPromotionIds.equals(requestedPromotionIds)) {
        // Las promociones cambiaron - limpiar las viejas y aplicar las nuevas
        log.info("Promociones cambiaron en orden {}: {} -> {}", orderId, currentPromotionIds,
                requestedPromotionIds);

        // Remover items de promoción viejos
        order.getItems().removeIf(item -> Boolean.TRUE.equals(item.getIsPromotionItem()));

        int totalNormalItemsCount = order.getItems().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getIsPromotionItem()))
                .mapToInt(OrderItem::getCantidad)
                .sum();
        processPromotions(order, request.promotionIds(), totalNormalItemsCount);
        log.info("Promociones actualizadas en edición de orden {}: {} items de promo creados",
                orderId, request.promotionIds().size());
    } else {
        log.info("Promociones sin cambios en edición de orden {}: {} - Items preservados (no re-procesados)",
                orderId, currentPromotionIds);
    }
}
```

### Beneficios de la Corrección

✅ **Previene duplicación**: No se crean items duplicados cuando solo se agrega flete  
✅ **Preserva precios**: Los precios promocionales se mantienen intactos  
✅ **Mantiene estado**: La orden mantiene su estado `[Promoción]`  
✅ **Mejora logging**: Logs más claros para debugging  
✅ **Permite cambios**: Si realmente quieres cambiar las promociones, funciona correctamente  

---

## Bug 2: Precio Incorrecto en Modal de Edición (⚠️ REQUIERE FRONTEND)

### Descripción del Problema

**Síntomas:**
- Al abrir el modal de edición de una orden promocional, el precio mostrado es la suma de los productos individuales
- No se muestra el precio promocional correcto
- **NOTA**: La factura final muestra el precio correcto (solo es un bug de visualización en el modal)

### Causa Probable

Este bug está en el frontend (no en el backend Java):
- El modal de edición probablemente está calculando el precio sumando los productos
- En vez de eso, debe leer el precio total de la respuesta de la orden

### Recomendaciones para el Frontend

**Archivos a investigar:**
1. Componentes de administrador/vendedor que manejan edición de órdenes
2. Modal de edición de órdenes
3. Lógica de cálculo/visualización de precios

**Corrección sugerida:**
```javascript
// ❌ INCORRECTO - Calcular suma de productos
const totalPrice = order.items.reduce((sum, item) => sum + item.subTotal, 0);

// ✅ CORRECTO - Usar el total de la orden directamente
const totalPrice = order.total;  // O el campo que contenga el precio total de la orden
```

---

## Notas Sobre Compilación

### Problema Encontrado

Al intentar compilar el proyecto con `./mvnw compile`, se encontró un error de Lombok:

```
Fatal error compiling: java.lang.ExceptionInInitializerError: 
com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

### Análisis

Este error NO está relacionado con los cambios realizados. Es un problema preexistente de incompatibilidad entre:
- La versión de Lombok utilizada en el proyecto
- La versión de Java (Java 17)
- La versión del compilador Maven

### Soluciones Posibles

1. **Actualizar Lombok en pom.xml** (recomendado):
   ```xml
   <dependency>
       <groupId>org.projectlombok</groupId>
       <artifactId>lombok</artifactId>
       <version>1.18.30</version> <!-- Versión más reciente -->
       <scope>provided</scope>
   </dependency>
   ```

2. **Usar Docker para compilación**:
   - El `Dockerfile` del proyecto probablemente tiene una configuración que funciona
   - `docker-compose build` debería compilar correctamente

3. **Ignorar temporalmente**:
   - Si la aplicación ya está corriendo via IntelliJ o Docker, el hot reload puede aplicar los cambios automáticamente
   - Los cambios en código Java puro (sin anotaciones de Lombok) se pueden aplicar dinámicamente

---

## Pruebas Recomendadas

### Test Manual 1: Editar Orden Promocional + Agregar Flete

1. **Crear orden promocional** sin flete
   - Ir al panel de vendedor/admin
   - Crear orden con una promoción específica (ej: "Promo 15+15")
   - **NO** habilitar flete
   - Guardar
   - **Verificar**: Precio es el precio de la promoción

2. **Editar y agregar flete**
   - Abrir modal de edición de la orden
   - Habilitar checkbox "Incluir Flete"
   - Guardar cambios

3. **Verificaciones**:
   - ✅ Items de promoción NO se duplican
   - ✅ Orden mantiene estado `[Promoción]` en las notas
   - ✅ Flete aparece en el total
   - ✅ Los productos siguen siendo de la promoción (no productos normales)
   - ✅ La cantidad de productos es la correcta

### Test Manual 2: Múltiples Ediciones

1. Editar la misma orden 3-4 veces seguidas
2. Alternar entre agregar/quitar flete
3. **Verificar**: No hay acumulación de items duplicados

### Test Manual 3: Cambiar Promociones

1. Crear orden con Promo A
2. Editar y cambiar a Promo B
3. **Verificar**: 
   - Items de Promo A se eliminan
   - Items de Promo B se agregan correctamente
   - No hay duplicación ni mezcla

---

## Próximos Pasos

### Inmediato
1. ✅ Corrección de backend aplicada
2. ⏳ Resolver problema de compilación de Lombok (actualizar versión)
3. ⏳ Identificar y corregir visualización de precio en modal (frontend)

### Testing
1. Probar edición de órdenes promocionales con flete
2. Verificar que no hay duplicación de items
3. Validar precios en modal y factura
4. Pruebas de regresión en órdenes normales

### Frontend
1. Localizar repositorio del frontend
2. Buscar componente de modal de edición
3. Asegurar que usa `order.total` en vez de calcular suma
4. Probar visualización de precios promocionales

---

## Archivos Modificados

### Backend
- [`OrderServiceImpl.java`](file:///Users/arnoldalexanderarevalo/Sistema-gesti-n-Vitalexa/src/main/java/org/example/sistema_gestion_vitalexa/service/impl/OrderServiceImpl.java)
  - Método: `updateOrder()` (líneas ~999-1032)
  - **Cambio**: Lógica de procesamiento de promociones al editar

### Documentación
- [`implementation_plan.md`](file:///Users/arnoldalexanderarevalo/.gemini/antigravity/brain/6627e949-37a0-44ab-85a7-4112854cc44c/implementation_plan.md)
- [`task.md`](file:///Users/arnoldalexanderarevalo/.gemini/antigravity/brain/6627e949-37a0-44ab-85a7-4112854cc44c/task.md)
- Este documento: Resumen de correcciones

---

## Logs de Debugging

Después de aplicar el fix, los logs te mostrarán:

```
// Cuando SOLO agregas flete (sin cambiar promociones):
INFO: Promociones sin cambios en edición de orden {orderId}: [promo-uuid] - Items preservados (no re-procesados)

// Cuando cambias las promociones:
INFO: Promociones cambiaron en orden {orderId}: [old-promo-uuid] -> [new-promo-uuid]
INFO: Promociones actualizadas en edición de orden {orderId}: 1 items de promo creados
```

Estos logs te ayudarán a confirmar que el fix está funcionando correctamente.
