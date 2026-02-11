import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix variable name conflict
old_loop = '''                    // Merge all daily groups from both users
                    List<VendorDailyGroupDTO> mergedDailyGroups = new java.util.ArrayList<>();
                    BigDecimal totalPeriod = BigDecimal.ZERO;
                    for (VendorDailySalesDTO report : matchingReports) {
                        mergedDailyGroups.addAll(report.dailyGroups());
                        totalPeriod = totalPeriod.add(report.totalPeriod());
                    }'''

new_loop = '''                    // Merge all daily groups from both users
                    List<VendorDailyGroupDTO> mergedDailyGroups = new java.util.ArrayList<>();
                    BigDecimal totalPeriod = BigDecimal.ZERO;
                    for (VendorDailySalesDTO vendorReport : matchingReports) {
                        mergedDailyGroups.addAll(vendorReport.dailyGroups());
                        totalPeriod = totalPeriod.add(vendorReport.totalPeriod());
                    }'''

content = content.replace(old_loop, new_loop)

# Guardar
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Variable name conflict fixed!")
