# 🔍 Diagnóstico de Duplicación - Con Logs Detallados

## Cambios Aplicados

Agregué **logs detallados** en el código para rastrear exactamente qué está pasando:

1. ✅ Si la orden es detectada como promoción
2. ✅ Cuántos items envía el frontend  
3. ✅ Cuántos items normales vs flete se detectan
4. ✅ Si se bloquean items cuando es orden promo

---

## 🔧 URGENTE: Rebuild en IntelliJ

**Antes de probar, debes recompilar:**

1. En IntelliJ: **`Build`** → **`Rebuild Project`**
2. **Detener** la app (⏹️)
3. **Ejecutar** de nuevo (▶️)

---

## 🧪 Cómo Probar

###  Paso 1: Crear Orden de Promoción

1. Ve al panel de vendedor
2. Crea una orden promocional (ej: 40 + 10)
3. **NO agregues flete todavía**
4. Guarda la orden

### Paso 2: Editar y Agregar Flete

1. Abre la orden que creaste
2. Haz clic en **"Editar"**
3. Habilita **"Incluir Flete"**
4. **NO cambies nada más**
5. Guarda cambios

### Paso 3: Revisar Logs en IntelliJ

**Busca en la consola de IntelliJ estos logs:**

```
📝 Orden {uuid}: Notas='... [Promoción]', esPromocion=true
📦 Request tiene X items totales
📦 Items filtrados: Y normales, Z flete
```

---

## 🔎 Qué Buscamos en los Logs

### Escenario A: El Frontend SÍ Envía Items Normales (Problema)

```bash
📝 Orden abc-123: Notas='Cliente X [Promoción]', esPromocion=true
📦 Request tiene 3 items totales       # <- Frontend envía items!
📦 Items filtrados: 2 normales, 1 flete  # <- 2 items normales + 1 flete

# Deberías ver estos bloqueos:
⚠️ BLOQUEADO: Item normal ignorado... productId=xxx, cantidad=40
⚠️ BLOQUEADO: Item normal ignorado... productId=yyy, cantidad=10
```

**Interpretación:**
- ✅ El código **SÍ está bloqueando** los items correctamente
- ❌ El **frontend** está enviando items que no debería
- 🔧 **Solución**: Arreglar el frontend para que NO envíe items normales en órdenes promo

### Escenario B: El Frontend NO Envía Items (Correcto)

```bash
📝 Orden abc-123: Notas='Cliente X [Promoción]', esPromocion=true
📦 Request tiene 1 items totales       # <- Solo flete
📦 Items filtrados: 0 normales, 1 flete  # <- Solo flete
```

**Interpretación:**
- ✅ El frontend está funcionando bien
- ❌ El problema está en **otro lado del backend**
- 🔧 **Solución**: Investigar otra parte del código

### Escenario C: La Orden NO se Detecta como Promoción (Bug Grave)

```bash
📝 Orden abc-123: Notas='Cliente X', esPromocion=false  # <- ¡NO detectó [Promoción]!
📦 Request tiene 3 items totales
📦 Items filtrados: 2 normales, 1 flete
# NO hay bloqueos porque cree que es orden normal
```

**Interpretación:**
- ❌ El campo `notas` NO tiene el sufijo `[Promoción]`
- 🔧 **Solución**: Verificar por qué las notas no tienen el sufijo

---

## 📊 Ejemplo Real de Logs

Cuando edites la orden y agregues flete, deberías ver algo así:

```
INFO: 📝 Orden 12345-abc: Notas='Pedido de prueba [Promoción]', esPromocion=true
INFO: 📦 Request tiene 3 items totales
INFO: 📦 Items filtrados: 2 normales, 1 flete
INFO: ⚠️ BLOQUEADO: Item normal ignorado en edición de orden promo: product-id-1 (cantidad: 40)
INFO: ⚠️ BLOQUEADO: Item normal ignorado en edición de orden promo: product-id-2 (cantidad: 10)
INFO: Items de flete procesados en edición de orden 12345-abc: 1 items
INFO: Promociones sin cambios en edición de orden 12345-abc: [promo-uuid] - Items preservados
```

---

## 🎯 Qué Hacer con los Resultados

### Si ves los bloqueos (⚠️ BLOQUEADO):

**El backend está funcionando bien**. El problema es que el frontend envía items que no debería.

**Solución:**
- Localizar el código del frontend que edita órdenes
- Modificar para que cuando `order.hasPromotion` o similar sea `true`, **NO incluya** `items` normales en el request
- Solo debe enviar `freightItems` si se agrega flete

### Si NO ves los bloqueos:

Significa que el frontend NO está enviando items normales, entonces el problema está en otra parte.

**Posibilidades:**
1. Los items se están duplicando en `processPromotions()` (poco probable con el nuevo fix)
2. Hay algún código adicional que agrega items después
3. El problema es en la capa de persistencia (JPA)

---

## 📋 Checklist

Después de hacer Rebuild + Reiniciar + Probar:

1. [ ] ¿Ves el log `📝 Orden {uuid}: Notas=...`?
2. [ ] ¿El log muestra `esPromocion=true`?
3. [ ] ¿Ves el log `📦 Request tiene X items`?
4. [ ] ¿Cuántos items tiene el request?
5. [ ] ¿Ves logs `⚠️ BLOQUEADO`?
6. [ ] ¿Los items siguen duplicándose en la factura?

---

##  Próximos Pasos

**Copia los logs completos** que aparecen cuando editas y me los compartes. Con esos logs podré determinar:

1. Si el frontend está enviando items de más
2. Si la detección de orden promo está fallando  
3. Si hay algún otro punto donde se duplican

---

## 🚨 Importante

**NO cierres la consola de IntelliJ** mientras pruebas. Necesitamos ver todos los logs para diagnosticar correctamente.

Si IntelliJ corta los logs, puedes buscarlos en:
```
~/Library/Logs/vigencia-logs/
```

O configurar un archivo de logs en `application.properties`:
```properties
logging.file.name=vitalexa-debug.log
logging.level.org.example.sistema_gestion_vitalexa=DEBUG
```
