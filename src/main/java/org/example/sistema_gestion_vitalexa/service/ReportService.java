package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.*;
import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    ReportDTO getCompleteReport(LocalDate startDate, LocalDate endDate);

    ReportDTO getCompleteReport(LocalDate startDate, LocalDate endDate, java.util.UUID vendorId);

    SalesReportDTO getSalesReport(LocalDate startDate, LocalDate endDate);

    SalesReportDTO getSalesReport(LocalDate startDate, LocalDate endDate, java.util.UUID vendorId);

    ProductReportDTO getProductReport();

    ProductReportDTO getProductReport(java.util.UUID vendorId);

    ProductReportDTO getProductReport(LocalDate startDate, LocalDate endDate);

    ProductReportDTO getProductReport(LocalDate startDate, LocalDate endDate, java.util.UUID vendorId);

    VendorReportDTO getVendorReport(LocalDate startDate, LocalDate endDate);

    ClientReportDTO getClientReport();

    ClientReportDTO getClientReport(java.util.UUID vendorId);

    ClientReportDTO getClientReport(LocalDate startDate, LocalDate endDate);

    ClientReportDTO getClientReport(LocalDate startDate, LocalDate endDate, java.util.UUID vendorId);

    List<VendorDailySalesDTO> getVendorDailySalesReport(LocalDate startDate, LocalDate endDate);
}
