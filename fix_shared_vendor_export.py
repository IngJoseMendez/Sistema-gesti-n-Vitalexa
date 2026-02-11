import re

# Leer el archivo
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Buscar y reemplazar la sección problemática
old_section = '''            if (vendorId != null) {
                // Get vendor to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                        .orElseThrow(() -> new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                                "Vendedor no encontrado"));

                // For shared users, use the canonical username for matching
                String matchUsername;
                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                    matchUsername = UserUnificationUtil.getSharedUsernames(vendor.getUsername()).get(0);
                } else {
                    matchUsername = vendor.getUsername();
                }

                // Filter by vendorName (username) instead of UUID
                VendorDailySalesDTO vendorDaily = reportService.getVendorDailySalesReport(startDate, endDate).stream()
                        .filter(v -> v.vendedorName().equals(matchUsername))
                        .findFirst()
                        .orElseThrow(() -> new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                                "No se encontraron ventas para este vendedor"));
                vendorSalesReports = List.of(vendorDaily);'''

new_section = '''            if (vendorId != null) {
                // Get vendor to check if it's a shared user
                User vendor = userRepository.findById(vendorId)
                        .orElseThrow(() -> new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                                "Vendedor no encontrado"));

                // For shared users, get both usernames
                List<String> matchUsernames;
                if (UserUnificationUtil.isSharedUser(vendor.getUsername())) {
                    matchUsernames = UserUnificationUtil.getSharedUsernames(vendor.getUsername());
                } else {
                    matchUsernames = List.of(vendor.getUsername());
                }

                // Filter by any of the shared usernames
                List<VendorDailySalesDTO> matchingReports = reportService.getVendorDailySalesReport(startDate, endDate).stream()
                        .filter(v -> matchUsernames.contains(v.vendedorName()))
                        .toList();

                if (matchingReports.isEmpty()) {
                    throw new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                            "No se encontraron ventas para este vendedor");
                }

                // If there are multiple reports (shared users), merge them
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
                    vendorSalesReports = List.of(unifiedReport);
                } else {
                    vendorSalesReports = matchingReports;
                }'''

content = content.replace(old_section, new_section)

# Guardar
with open(r'c:\Users\Jose Pc\IdeaProjects\Sistema_gestion_vitalexa\src\main\java\org\example\sistema_gestion_vitalexa\service\impl\ReportExportServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Shared user vendor filtering fixed!")
