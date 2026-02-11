import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Buscar y reemplazar la sección del constructor
old_constructor = '''                // If there are multiple reports (shared users), merge them
                if (matchingReports.size() > 1) {
                    // Merge all daily groups from both users
                    List<VendorDailyGroupDTO> mergedDailyGroups = new java.util.ArrayList<>();
                    for (VendorDailySalesDTO report : matchingReports) {
                        mergedDailyGroups.addAll(report.dailyGroups());
                    }
                    
                    // Create unified report with canonical name (first shared username - Nina)
                    VendorDailySalesDTO unifiedReport = new VendorDailySalesDTO(
                        matchUsernames.get(0),  // Use canonical name (NinaTorres)
                        mergedDailyGroups
                    );
                    vendorSalesReports = List.of(unifiedReport);'''

new_constructor = '''                // If there are multiple reports (shared users), merge them
                if (matchingReports.size() > 1) {
                    // Merge all daily groups from both users
                    List<VendorDailyGroupDTO> mergedDailyGroups = new java.util.ArrayList<>();
                    BigDecimal totalPeriod = BigDecimal.ZERO;
                    for (VendorDailySalesDTO report : matchingReports) {
                        mergedDailyGroups.addAll(report.dailyGroups());
                        totalPeriod = totalPeriod.add(report.totalPeriod());
                    }
                    
                    // Create unified report with canonical name (first shared username - Nina)
                    // Use the first report's vendorId, startDate, endDate
                    VendorDailySalesDTO firstReport = matchingReports.get(0);
                    VendorDailySalesDTO unifiedReport = new VendorDailySalesDTO(
                        firstReport.vendedorId(),
                        matchUsernames.get(0),  // Use canonical name (NinaTorres)
                        firstReport.startDate(),
                        firstReport.endDate(),
                        mergedDailyGroups,
                        totalPeriod
                    );
                    vendorSalesReports = List.of(unifiedReport);'''

content = content.replace(old_constructor, new_constructor)

# Guardar
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ VendorDailySalesDTO constructor fixed!")
