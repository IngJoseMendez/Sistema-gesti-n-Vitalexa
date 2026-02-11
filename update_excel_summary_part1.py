import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Cambio 1: Reemplazar la declaración del mapa
old_map = '            java.util.Map<String, BigDecimal> clientPendingMap = new java.util.LinkedHashMap<>();'
new_map = '''            // Mapa para facturas pendientes por cliente: Cliente -> List de [InvoiceNum, Fecha, Amount]
            java.util.Map<String, java.util.List<Object[]>> clientPendingInvoices = new java.util.LinkedHashMap<>();'''

content = content.replace(old_map, new_map)

# Cambio 2: Reemplazar el merge
old_merge = '''                        // Acumular deuda por cliente para el resumen
                        String clientKey = inv.numeroCliente();
                        clientPendingMap.merge(clientKey, inv.pendingAmount(), BigDecimal::add);'''

new_merge = '''                        // Agregar factura siel tiene saldo pendiente
                        if (inv.pendingAmount().compareTo(BigDecimal.ZERO) > 0) {
                            String clientKey = inv.numeroCliente();
                            clientPendingInvoices. computeIfAbsent(clientKey, k -> new java.util.ArrayList<>())
                                .add(new Object[]{inv.numeroFactura(), inv.fecha(), inv.pendingAmount()});
                        }'''

content = content.replace(old_merge, new_merge)

# Guardar el archivo modificado
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("File updated successfully!")
