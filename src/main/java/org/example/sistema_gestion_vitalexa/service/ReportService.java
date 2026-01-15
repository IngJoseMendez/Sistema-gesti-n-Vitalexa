package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.*;
import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    ReportDTO getCompleteReport(LocalDate startDate, LocalDate endDate);
    SalesReportDTO getSalesReport(LocalDate startDate, LocalDate endDate);
    ProductReportDTO getProductReport();
    VendorReportDTO getVendorReport(LocalDate startDate, LocalDate endDate);
    ClientReportDTO getClientReport();
    List<VendorDailySalesDTO> getVendorDailySalesReport(LocalDate startDate, LocalDate endDate);
}
