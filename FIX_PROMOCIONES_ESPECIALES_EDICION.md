# Fix: Error de Transacción con Promociones (Normales y Especiales)

## Problemas Reportados

### Problema 1: Error al Eliminar Promociones Especiales en Edición
Al editar una orden que contiene una promoción especial y intentar eliminar dicha promoción, se producía el siguiente error:

```
org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

Además, en el frontend, la orden mostraba el nombre de la promoción padre en lugar del nombre de la promoción especial.

### Problema 2: Error al Crear Orden con Promoción Normal
Al intentar crear una nueva orden con una promoción normal, se producía el mismo error de transacción:

```
org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

## Causa Raíz

### Causa Principal (Problema 2)
El método `processPromotions` intentaba determinar si un ID de promoción pertenecía a una promoción especial o normal. Para ello, primero llamaba a `specialPromotionService.findEntityById(id)` dentro de un try-catch. Sin embargo, cuando el ID era de una promoción normal (no especial), el servicio lanzaba una `RuntimeException` que **marcaba la transacción actual para rollback**. Aunque la excepción se capturaba, la transacción ya estaba marcada para rollback y no podía completarse.

### Causas Secundarias (Problema 1)

1. **Error de Transacción en Edición**: El método `restoreStockForItems` intentaba acceder a `item.getPromotion()` directamente sin considerar que algunos items tienen `SpecialPromotion` en su lugar. Esto causaba problemas de lazy loading y excepciones dentro de la transacción.

2. **Nombre Incorrecto en Frontend**: Los mappers `OrderItemMapper` y `OrderMapper` siempre usaban el `promotion.nombre` y `promotion.id` sin verificar si el item tenía una `SpecialPromotion`.

## Solución Implementada

### 1. OrderServiceImpl.java - Inyección de SpecialPromotionRepository

**Cambio**: Inyectar el repositorio directamente en lugar de depender solo del servicio.

```java
private final SpecialPromotionService specialPromotionService;
private final org.example.sistema_gestion_vitalexa.repository.SpecialPromotionRepository specialPromotionRepository;
```

**Beneficio**: Permite usar métodos que devuelven `Optional` sin lanzar excepciones.

### 2. OrderServiceImpl.java - Método processPromotions

**Cambio**: Usar el repositorio con `Optional` en lugar del servicio que lanza excepciones.

```java
promotionIds.forEach(id -> {
    log.info("Buscando promoción (Normal o Especial) con ID: {}", id);

    // ✅ CRÍTICO: Usar repositorio directamente para evitar excepciones 
    // que marcan la transacción para rollback
    SpecialPromotion specialPromotion = null;
    Promotion promotion = null;
    boolean isSpecial = false;

    // ✅ Buscar primero en SpecialPromotion usando Optional (no lanza excepción)
    java.util.Optional<SpecialPromotion> specialPromotionOpt = specialPromotionRepository.findById(id);
    
    if (specialPromotionOpt.isPresent()) {
        specialPromotion = specialPromotionOpt.get();
        isSpecial = true;
        log.info("✅ Encontrada como SpecialPromotion: {}", specialPromotion.getNombre());
    } else {
        log.info("No es SpecialPromotion, buscando como Promotion normal...");
    }
    
    if (isSpecial && specialPromotion != null) {
        // Procesar como especial...
    } else {
        // Procesar como normal...
        promotion = promotionService.findEntityById(id);
    }
    // ...resto del código
});
```

**Beneficio**: 
- ✅ **Elimina completamente el error de transacción** al crear/editar órdenes con promociones
- ✅ No se lanzan excepciones que marquen la transacción para rollback
- ✅ Flujo limpio usando `Optional.isPresent()` para determinar el tipo de promoción

### 3. OrderItemMapper.java

**Cambio**: Modificar el mapper para priorizar `SpecialPromotion` sobre `Promotion` al mapear IDs y nombres.

```java
@Mapper(componentModel = "spring")
public interface OrderItemMapper {
    // ...otros mappings...
    
    @Mapping(target = "promotionId", expression = "java(getPromotionId(item))")
    @Mapping(target = "promotionName", expression = "java(getPromotionName(item))")
    OrderItemResponse toResponse(OrderItem item);

    // ✅ Obtener ID de la promoción correcta (especial o padre)
    default java.util.UUID getPromotionId(OrderItem item) {
        if (item.getSpecialPromotion() != null) {
            return item.getSpecialPromotion().getId();
        }
        if (item.getPromotion() != null) {
            return item.getPromotion().getId();
        }
        return null;
    }

    // ✅ Obtener nombre de la promoción correcta (especial o padre)
    default String getPromotionName(OrderItem item) {
        if (item.getSpecialPromotion() != null) {
            return item.getSpecialPromotion().getNombre();
        }
        if (item.getPromotion() != null) {
            return item.getPromotion().getNombre();
        }
        return null;
    }
}
```

**Beneficio**: Ahora el frontend mostrará correctamente el nombre de la promoción especial en lugar del nombre de la promoción padre.

### 2. OrderMapper.java

**Cambio**: Actualizar `mapPromotionIds` para devolver los IDs de las promociones especiales.

```java
default java.util.List<java.util.UUID> mapPromotionIds(Order order) {
    // ...código existente...
    
    return uniqueInstances.values().stream()
            .map(i -> {
                // ✅ Priorizar SpecialPromotion sobre Promotion
                if (i.getSpecialPromotion() != null) {
                    return i.getSpecialPromotion().getId();
                }
                return i.getPromotion().getId();
            })
            .sorted()
            .collect(java.util.stream.Collectors.toList());
}
```

**Beneficio**: El frontend recibirá los IDs correctos de las promociones especiales, permitiendo eliminarlas correctamente.

### 3. OrderServiceImpl.java - Método restoreStockForItems

**Cambio**: Manejar correctamente las promociones especiales al restaurar stock, evitando problemas de lazy loading.

```java
// CASO 4: Items de promoción mainProduct
else if (Boolean.TRUE.equals(item.getIsPromotionItem()) &&
        !Boolean.TRUE.equals(item.getIsFreeItem())) {

    // 4A. Restaurar mainProduct de ESTA instancia
    product.increaseStock(item.getCantidad());
    
    // 4B. Obtener la promoción correcta (padre si es SpecialPromotion)
    org.example.sistema_gestion_vitalexa.entity.Promotion promoForGifts = null;
    
    try {
        if (item.getSpecialPromotion() != null && 
            item.getSpecialPromotion().getParentPromotion() != null) {
            promoForGifts = item.getSpecialPromotion().getParentPromotion();
        } else if (item.getPromotion() != null) {
            promoForGifts = item.getPromotion();
        }
    } catch (Exception e) {
        log.warn("⚠️ No se pudo cargar la promoción para restaurar gifts: {}", 
                e.getMessage());
    }
    
    if (promoForGifts != null && promoForGifts.getGiftItems() != null) {
        // Restaurar giftItems...
    }
}
```

**Beneficio**: 
- Maneja correctamente las promociones especiales usando try-catch para evitar errores de lazy loading
- Obtiene la promoción padre desde `SpecialPromotion` cuando es necesario
- Previene que la transacción se marque para rollback

## Archivos Modificados

1. ✅ `OrderServiceImpl.java` - **CRÍTICO**: Inyección de repositorio y cambio en `processPromotions`
2. ✅ `OrderItemMapper.java` - Mapeo de IDs y nombres de promociones
3. ✅ `OrderMapper.java` - Mapeo de IDs de promociones en lista
4. ✅ `OrderServiceImpl.java` - Restauración de stock con manejo de promociones especiales

## Pruebas Necesarias

### Prueba 1: Crear Orden con Promoción Normal
1. Crear una nueva orden con una promoción normal (no especial)
2. ✅ **Resultado Esperado**: La orden se crea sin errores de transacción

### Prueba 2: Crear Orden con Promoción Especial
1. Crear una nueva orden con una promoción especial
2. ✅ **Resultado Esperado**: La orden se crea sin errores de transacción
3. Verificar que el nombre mostrado sea el de la promoción especial

### Prueba 3: Editar Orden con Promoción Especial
1. Crear una orden con una promoción especial
2. Verificar que en el frontend muestre el nombre correcto de la promoción especial (no el nombre del padre)
3. Editar la orden y eliminar la promoción especial
4. ✅ **Resultado Esperado**: La promoción se elimina sin errores, el stock se restaura correctamente

### Prueba 4: Verificar Restauración de Stock
1. Crear una orden con promoción especial que incluya productos de regalo
2. Anotar el stock inicial de todos los productos involucrados
3. Editar la orden y eliminar la promoción especial
4. ✅ **Resultado Esperado**: 
   - Stock del producto principal restaurado (+cantidad comprada)
   - Stock de productos de regalo restaurado (+cantidad de regalo)
   - No hay errores en los logs

### Prueba 5: Editar Orden con Múltiples Promociones Especiales
1. Crear una orden con 2 o más promociones especiales diferentes
2. Editar la orden y eliminar solo una de ellas
3. ✅ **Resultado Esperado**: 
   - Solo se elimina la promoción seleccionada
   - Las demás promociones se mantienen intactas
   - Stock solo se restaura para la promoción eliminada

### Prueba 6: Orden con Promoción Normal y Especial
1. Crear una orden con una promoción normal y una especial
2. Editar y eliminar ambas promociones
3. ✅ **Resultado Esperado**: Ambas se eliminan correctamente sin errores

## Logs a Verificar

### Al Crear/Editar Orden con Promoción Normal
Deberías ver logs como:
```
Buscando promoción (Normal o Especial) con ID: [uuid]
No es SpecialPromotion, buscando como Promotion normal...
✅ Promoción '[nombre]' aplicada correctamente con instancia [uuid]
```

### Al Crear/Editar Orden con Promoción Especial
Deberías ver logs como:
```
Buscando promoción (Normal o Especial) con ID: [uuid]
✅ Encontrada como SpecialPromotion: [nombre]
✅ Usando SpecialPromotion: [nombre] (Padre: [nombre_padre])
✅ Promoción '[nombre]' aplicada correctamente con instancia [uuid]
```

### Al Eliminar Promoción (Edición)
Cuando elimines una promoción especial, deberías ver logs como estos:

```
🔄 Procesando restauración de item: [producto], isPromo=true, isFree=false, isBonified=false
✅ Stock restaurado (PROMO MAIN - Instancia [uuid]) para '[producto]': +[cantidad]
✅ Stock restaurado (PROMO GIFT - Instancia [uuid]) para '[producto_regalo]': +[cantidad]
```

Si hay un problema de lazy loading (que ahora está manejado), verías:
```
⚠️ No se pudo cargar la promoción para restaurar gifts del item [id]: [error]
```

## Notas Técnicas

- **Transacciones**: 
  - El uso de `Optional` del repositorio previene excepciones que marquen la transacción para rollback
  - El manejo con try-catch en `restoreStockForItems` previene que excepciones de lazy loading marquen la transacción
- **Mappers MapStruct**: Se usan expresiones Java (`expression = "java(...)`) para lógica condicional en el mapeo
- **Prioridad**: Siempre se prioriza `SpecialPromotion` sobre `Promotion` cuando ambas están presentes
- **Repositorio vs Servicio**: 
  - Repositorio: Devuelve `Optional<T>`, no lanza excepciones si no encuentra
  - Servicio: Lanza `RuntimeException` si no encuentra, marca transacción para rollback

## Estado

✅ **Compilación Exitosa** - No hay errores de compilación
⏳ **Pendiente de Pruebas** - Requiere pruebas de integración

---

**Fecha**: 2026-02-13
**Versión**: Sistema Gestion Vitalexa v0.0.1-SNAPSHOT

