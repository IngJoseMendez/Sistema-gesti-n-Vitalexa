import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Eliminar comentario duplicado y espacio antes del punto
old_bad_code = '''            // Mapa para acumular deuda por cliente (para el resumen lateral)
            // Mapa para facturas pendientes por cliente: Cliente -> List de [InvoiceNum, Fecha, Amount]
            java.util.Map<String, java.util.List<Object[]>> clientPendingInvoices = new java.util.LinkedHashMap<>();

            // Datos: iterar por cada día
            for (VendorDailyGroupDTO dailyGroup : vendor.dailyGroups()) {
                int clientIndex = 0;

                // Por cada cliente del día
                for (ClientDailyGroupDTO clientGroup : dailyGroup.clientGroups()) {
                    int facturaIndex = 0;
                    BigDecimal clientTotalPending = BigDecimal.ZERO;

                    // Calcular pendiente del cliente para este día
                    for (VendorInvoiceRowDTO inv : clientGroup.facturas()) {
                        clientTotalPending = clientTotalPending.add(inv.pendingAmount());
                        totalPendingPeriod = totalPendingPeriod.add(inv.pendingAmount());
                        totalPaidPeriod = totalPaidPeriod.add(inv.paidAmount());

                        // Agregar factura siel tiene saldo pendiente
                        if (inv.pendingAmount().compareTo(BigDecimal.ZERO) > 0) {
                            String clientKey = inv.numeroCliente();
                            clientPendingInvoices. computeIfAbsent(clientKey, k -> new java.util.ArrayList<>())
                                .add(new Object[]{inv.numeroFactura(), inv.fecha(), inv.pendingAmount()});
                        }
                    }'''

new_fixed_code = '''            // Mapa para facturas pendientes por cliente: Cliente -> List de [InvoiceNum, Fecha, Amount]
            java.util.Map<String, java.util.List<Object[]>> clientPendingInvoices = new java.util.LinkedHashMap<>();

            // Datos: iterar por cada día
            for (VendorDailyGroupDTO dailyGroup : vendor.dailyGroups()) {
                int clientIndex = 0;

                // Por cada cliente del día
                for (ClientDailyGroupDTO clientGroup : dailyGroup.clientGroups()) {
                    int facturaIndex = 0;
                    BigDecimal clientTotalPending = BigDecimal.ZERO;

                    // Calcular pendiente del cliente para este día
                    for (VendorInvoiceRowDTO inv : clientGroup.facturas()) {
                        clientTotalPending = clientTotalPending.add(inv.pendingAmount());
                        totalPendingPeriod = totalPendingPeriod.add(inv.pendingAmount());
                        totalPaidPeriod = totalPaidPeriod.add(inv.paidAmount());

                        // Agregar factura si tiene saldo pendiente
                        if (inv.pendingAmount().compareTo(BigDecimal.ZERO) > 0) {
                            String clientKey = inv.numeroCliente();
                            clientPendingInvoices.computeIfAbsent(clientKey, k -> new java.util.ArrayList<>())
                                .add(new Object[]{inv.numeroFactura(), inv.fecha(), inv.pendingAmount()});
                        }
                    }'''

content = content.replace(old_bad_code, new_fixed_code)

# Guardar
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Syntax errors fixed!")
