package org.example.sistema_gestion_vitalexa.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;
import org.example.sistema_gestion_vitalexa.dto.ReportDTO;
import org.example.sistema_gestion_vitalexa.dto.VendorDailySalesDTO;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface ReportExportService {

    byte[] exportReportToPdf(ReportDTO report, LocalDate startDate, LocalDate endDate);

    byte[] exportReportToExcel(ReportDTO report, LocalDate startDate, LocalDate endDate);

    byte[] exportReportToExcel(ReportDTO report, LocalDate startDate, LocalDate endDate, java.util.UUID vendorId);

    byte[] exportReportToCsv(ReportDTO report, LocalDate startDate, LocalDate endDate);

    // Exportaciones específicas
    byte[] exportSalesReportToPdf(LocalDate startDate, LocalDate endDate);

    byte[] exportProductReportToExcel();

    byte[] exportClientReportToCsv();

    void createVendorDailySalesSheets(Workbook workbook, List<VendorDailySalesDTO> vendorSalesReports,
            CellStyle headerStyle, CellStyle dataStyle, CellStyle currencyStyle);

    // Exportaciones por vendedor específico
    byte[] exportVendorReportExcel(VendorDailySalesDTO vendorReport);

    byte[] exportVendorReportPdf(VendorDailySalesDTO vendorReport);
}
