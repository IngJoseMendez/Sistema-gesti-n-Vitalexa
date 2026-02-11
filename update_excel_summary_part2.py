import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Buscar y reemplazar la sección de la tabla de resumen
old_summary_section = '''            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                    summaryStartRow, summaryStartRow, summaryCol, summaryCol + 1));

            // Encabezados del resumen
            int summaryHeaderRow = summaryStartRow + 1;
            Row clientSummaryHeaderRow = sheet.getRow(summaryHeaderRow);
            if (clientSummaryHeaderRow == null) {
                clientSummaryHeaderRow = sheet.createRow(summaryHeaderRow);
            }

            Cell clientHeaderCell = clientSummaryHeaderRow.createCell(summaryCol);
            clientHeaderCell.setCellValue("Cliente");
            clientHeaderCell.setCellStyle(headerStyle);

            Cell debtHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 1);
            debtHeaderCell.setCellValue("Total Debe");
            debtHeaderCell.setCellStyle(headerStyle);

            // Datos del resumen: Listar clientes con deuda > 0
            int summaryDataRow = summaryHeaderRow + 1;
            for (java.util.Map.Entry<String, BigDecimal> entry : clientPendingMap.entrySet()) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    Row clientRow = sheet.getRow(summaryDataRow);
                    if (clientRow == null) {
                        clientRow = sheet.createRow(summaryDataRow);
                    }

                    Cell clientNameCell = clientRow.createCell(summaryCol);
                    clientNameCell.setCellValue(entry.getKey());
                    clientNameCell.setCellStyle(dataStyle);

                    Cell clientDebtCell = clientRow.createCell(summaryCol + 1);
                    clientDebtCell.setCellValue(entry.getValue().doubleValue());
                    clientDebtCell.setCellStyle(currencyStyle);

                    summaryDataRow++;
                }
            }

            // Total del resumen
            Row summaryTotalRow = sheet.getRow(summaryDataRow);
            if (summaryTotalRow == null) {
                summaryTotalRow = sheet.createRow(summaryDataRow);
            }
            Cell summaryTotalLabel = summaryTotalRow.createCell(summaryCol);
            summaryTotalLabel.setCellValue("TOTAL:");
            summaryTotalLabel.setCellStyle(headerStyle);

            Cell summaryTotalValue = summaryTotalRow.createCell(summaryCol + 1);
            summaryTotalValue.setCellValue(totalPendingPeriod.doubleValue());
            summaryTotalValue.setCellStyle(currencyStyle);

            // Ajustar ancho de columnas del resumen
            sheet.autoSizeColumn(summaryCol);
            sheet.autoSizeColumn(summaryCol + 1);'''

new_summary_section = '''            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                    summaryStartRow, summaryStartRow, summaryCol, summaryCol + 3));

            // Encabezados del resumen (ahora 4 columnas)
            int summaryHeaderRow = summaryStartRow + 1;
            Row clientSummaryHeaderRow = sheet.getRow(summaryHeaderRow);
            if (clientSummaryHeaderRow == null) {
                clientSummaryHeaderRow = sheet.createRow(summaryHeaderRow);
            }

            Cell clientHeaderCell = clientSummaryHeaderRow.createCell(summaryCol);
            clientHeaderCell.setCellValue("Cliente");
            clientHeaderCell.setCellStyle(headerStyle);

            Cell invoiceHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 1);
            invoiceHeaderCell.setCellValue("# Factura");
            invoiceHeaderCell.setCellStyle(headerStyle);

            Cell dateHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 2);
            dateHeaderCell.setCellValue("Fecha");
            dateHeaderCell.setCellStyle(headerStyle);

            Cell debtHeaderCell = clientSummaryHeaderRow.createCell(summaryCol + 3);
            debtHeaderCell.setCellValue("Debe");
            debtHeaderCell.setCellStyle(headerStyle);

            // Datos del resumen: Listar facturas con deuda > 0, agrupadas por cliente
            int summaryDataRow = summaryHeaderRow + 1;
            
            for (java.util.Map.Entry<String, java.util.List<Object[]>> entry : clientPendingInvoices.entrySet()) {
                String clientName = entry.getKey();
                java.util.List<Object[]> clientInvoices = entry.getValue();
                
                // Ordenar por fecha (índice 1 del array)
                clientInvoices.sort((a, b) -> ((LocalDate)a[1]).compareTo((LocalDate)b[1]));
                
                for (int i = 0; i < clientInvoices.size(); i++) {
                    Object[] inv = clientInvoices.get(i);
                    Row clientRow = sheet.getRow(summaryDataRow);
                    if (clientRow == null) {
                        clientRow = sheet.createRow(summaryDataRow);
                    }

                    // Mostrar nombre del cliente solo en la primera fila
                    Cell clientNameCell = clientRow.createCell(summaryCol);
                    if (i == 0) {
                        clientNameCell.setCellValue(clientName);
                    }
                    clientNameCell.setCellStyle(dataStyle);

                    // # Factura
                    Cell invoiceNumCell = clientRow.createCell(summaryCol + 1);
                    invoiceNumCell.setCellValue((String)inv[0]);
                    invoiceNumCell.setCellStyle(dataStyle);

                    // Fecha
                    Cell dateCell = clientRow.createCell(summaryCol + 2);
                    dateCell.setCellValue(((LocalDate)inv[1]).format(DATE_FORMATTER));
                    dateCell.setCellStyle(dataStyle);

                    // Debe
                    Cell debtCell = clientRow.createCell(summaryCol + 3);
                    debtCell.setCellValue(((BigDecimal)inv[2]).doubleValue());
                    debtCell.setCellStyle(currencyStyle);

                    summaryDataRow++;
                }
                
                // Línea de subtotal por cliente si tiene más de una factura
                if (clientInvoices.size() > 1) {
                    Row subtotalRow = sheet.getRow(summaryDataRow);
                    if (subtotalRow == null) {
                        subtotalRow = sheet.createRow(summaryDataRow);
                    }
                    
                    Cell subtotalLabelCell = subtotalRow.createCell(summaryCol + 2);
                    subtotalLabelCell.setCellValue("Subtotal:");
                    subtotalLabelCell.setCellStyle(headerStyle);
                    
                    BigDecimal clientTotal = clientInvoices.stream()
                        .map(a -> (BigDecimal)a[2])
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    Cell subtotalValueCell = subtotalRow.createCell(summaryCol + 3);
                    subtotalValueCell.setCellValue(clientTotal.doubleValue());
                    subtotalValueCell.setCellStyle(currencyStyle);
                    
                    summaryDataRow++;
                }
            }

            // Total del resumen
            Row summaryTotalRow = sheet.getRow(summaryDataRow);
            if (summaryTotalRow == null) {
                summaryTotalRow = sheet.createRow(summaryDataRow);
            }
            Cell summaryTotalLabel = summaryTotalRow.createCell(summaryCol + 2);
            summaryTotalLabel.setCellValue("TOTAL:");
            summaryTotalLabel.setCellStyle(headerStyle);

            Cell summaryTotalValue = summaryTotalRow.createCell(summaryCol + 3);
            summaryTotalValue.setCellValue(totalPendingPeriod.doubleValue());
            summaryTotalValue.setCellStyle(currencyStyle);

            // Ajustar ancho de columnas del resumen
            sheet.autoSizeColumn(summaryCol);
            sheet.autoSizeColumn(summaryCol + 1);
            sheet.autoSizeColumn(summaryCol + 2);
            sheet.autoSizeColumn(summaryCol + 3);'''

content = content.replace(old_summary_section, new_summary_section)

# Guardar el archivo modificado
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("Summary table section updated successfully!")
