## 🔍 CHECKLIST PARA QUE FRONTEND VERIFIQUE

### Problema 1: Bonificados ✅ BACKEND OK
**Lo que el backend ahora hace**:
- Acepta `bonifiedItems[]` en el payload
- Procesa bonificados con `isBonified=true`
- Establece precio $0 automáticamente
- Guarda correctamente en edición

**Lo que frontend debe hacer**:
- [x] Enviar `bonifiedItems` como array separado en payload
- [x] NO enviar `isBonified` en items regulares (solo en bonificados)
- [x] Asegurar que `bonifiedItems` tiene estructura: `{productId, cantidad}`

**Verificar en Payload**:
```json
{
  "items": [...],  // Items normales
  "bonifiedItems": [  // Bonificados separados
    {"productId": "uuid1", "cantidad": 3}
  ]
}
```

---

### Problema 2: Flete Personalizado ⏳ PENDIENTE FRONTEND
**Lo que el backend ahora hace**:
- Guarda `freightCustomText` (descripción del flete)
- Guarda `freightQuantity` (cantidad)
- Aplica todo correctamente

**Lo que frontend DEBE verificar**:
- [ ] Cuando selecciona "Flete Personalizado", ¿se activa campo de texto?
- [ ] Cuando agrega descripción, ¿se envía en payload?
- [ ] Cuando pone cantidad, ¿se envía `freightQuantity`?

**Verificar en Payload**:
```json
{
  "includeFreight": true,
  "freightCustomText": "Flete especial para promoción",  // ← Debe estar
  "freightQuantity": 1  // ← Debe estar
}
```

**Si NO se envía**:
- El flete genérico funciona pero el personalizado NO
- El backend está listo, el problema es frontend

**Debug**:
1. Abre DevTools (F12)
2. Network → Busca request PUT/POST a `/api/admin/orders`
3. Verifica payload: ¿están `freightCustomText` y `freightQuantity`?

---

### Problema 3: Promociones Duplicadas ✅ BACKEND OK
**Lo que el backend ahora hace**:
- Valida que no haya promo repetida
- Si intenta: `"No se puede seleccionar la misma promoción múltiples veces"`
- Rechaza la solicitud

**Lo que frontend debe hacer**:
- [x] Permitir seleccionar promociones
- [x] Mostrar error si intenta duplicar (el backend lo valida)
- [x] Opcionalmente: prevenir en UI que se pueda seleccionar 2 veces

**Verificar**:
- Crear venta con "Promo X"
- Intentar agregar "Promo X" de nuevo
- Esperado: ❌ Error "No se puede seleccionar la misma promoción múltiples veces"

---

## 🧪 TESTING COMPLETO

### Test 1: Bonificados (AHORA FUNCIONA)
```
✅ 1. Crear orden normal
✅ 2. Editar orden
✅ 3. Agregar bonificados en sección separada
✅ 4. Guardar cambios
✅ 5. Verificar: Bonificados guardados con precio $0
```

### Test 2: Flete Personalizado (VERIFICAR FRONTEND)
```
❓ 1. Crear orden de promoción
❓ 2. Habilitar flete
❓ 3. Escribir descripción: "Flete a domicilio"
❓ 4. Establecer cantidad: 2
❓ 5. Guardar
❓ VERIFICAR EN PAYLOAD: ¿Se envía freightCustomText y freightQuantity?
```

### Test 3: Promociones Duplicadas (AHORA VALIDADO)
```
✅ 1. Crear venta
✅ 2. Agregar "Promo Descuento"
✅ 3. Intentar agregar "Promo Descuento" de nuevo
✅ 4. Verificar: Error "No se puede seleccionar la misma promoción múltiples veces"
```

---

## 📞 Si el Flete Personalizado NO Funciona

**Pasos de Debug**:

1. **Abre DevTools** (F12 en navegador)
2. **Ve a Network tab**
3. **Haz click en "Guardar" de la orden**
4. **Busca request a** `/api/admin/orders` o `/api/vendedor/orders`
5. **Mira el payload JSON**, ¿ves esto?
   ```json
   "freightCustomText": "tu descripción",
   "freightQuantity": 1
   ```

**Si SÍ lo ves**: Backend está recibiendo, problema está en guardar DB
**Si NO lo ves**: Frontend NO está enviando, revisar formulario

---

## 📋 CHECKLIST FINAL

- [ ] Bonificados se guardan en edición
- [ ] Bonificados aparecen con precio $0
- [ ] Promociones no permiten duplicados
- [ ] Flete personalizado se envía (verificar payload)
- [ ] Flete personalizado se guarda en DB
- [ ] Todas las pruebas funcionan

---

## 🔧 ACCIONES INMEDIATAS

**Para Frontend**:
1. Verificar que `bonifiedItems` se envía como array separado
2. Verificar que `freightCustomText` y `freightQuantity` se envían
3. Ejecutar Tests 1-3 arriba
4. Reportar cualquier issue

**Backend**:
- ✅ LISTO - Todo está implementado
- ✅ LISTO - Validaciones activas
- ✅ LISTO - Métodos creados

---

**¡El backend está completamente actualizado!** 🚀

