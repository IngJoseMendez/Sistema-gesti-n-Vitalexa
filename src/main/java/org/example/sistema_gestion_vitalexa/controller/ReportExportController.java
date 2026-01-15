package org.example.sistema_gestion_vitalexa.controller;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ReportDTO;
import org.example.sistema_gestion_vitalexa.service.ReportExportService;
import org.example.sistema_gestion_vitalexa.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/export")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('OWNER')")
public class ReportExportController {

    private final ReportService reportService;
    private final ReportExportService exportService;

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private LocalDate defaultStart(LocalDate startDate) {
        return (startDate != null) ? startDate : LocalDate.now().minusMonths(1);
    }

    private LocalDate defaultEnd(LocalDate endDate) {
        return (endDate != null) ? endDate : LocalDate.now();
    }

    private HttpHeaders baseDownloadHeaders(String filename, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);

        // fuerza descarga con nombre correcto
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build()
        );

        // evita cache raro en algunos navegadores
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.add(HttpHeaders.PRAGMA, "no-cache");

        return headers;
    }

    // =============================================
    // EXPORTACIONES DE REPORTES GENERALES
    // =============================================

    @GetMapping("/complete/pdf")
    public ResponseEntity<byte[]> exportCompleteReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        startDate = defaultStart(startDate);
        endDate = defaultEnd(endDate);

        ReportDTO report = reportService.getCompleteReport(startDate, endDate);
        byte[] pdfBytes = exportService.exportReportToPdf(report, startDate, endDate);

        HttpHeaders headers = baseDownloadHeaders(
                "reporte_completo_" + LocalDate.now() + ".pdf",
                MediaType.APPLICATION_PDF
        );

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/complete/excel")

    public ResponseEntity<byte[]> exportCompleteReportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        startDate = defaultStart(startDate);
        endDate = defaultEnd(endDate);

        ReportDTO report = reportService.getCompleteReport(startDate, endDate);
        byte[] excelBytes = exportService.exportReportToExcel(report, startDate, endDate);

        HttpHeaders headers = baseDownloadHeaders(
                "reporte_completo_" + LocalDate.now() + ".xlsx",
                XLSX_MEDIA_TYPE
        );

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/complete/csv")
    public ResponseEntity<byte[]> exportCompleteReportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        startDate = defaultStart(startDate);
        endDate = defaultEnd(endDate);

        ReportDTO report = reportService.getCompleteReport(startDate, endDate);
        byte[] csvBytes = exportService.exportReportToCsv(report, startDate, endDate);

        HttpHeaders headers = baseDownloadHeaders(
                "reporte_completo_" + LocalDate.now() + ".csv",
                MediaType.parseMediaType("text/csv;charset=UTF-8")
        );

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    // =============================================
    // EXPORTACIONES ESPECÍFICAS POR ÁREA
    // =============================================

    @GetMapping("/sales/pdf")
    public ResponseEntity<byte[]> exportSalesReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        startDate = defaultStart(startDate);
        endDate = defaultEnd(endDate);

        byte[] pdfBytes = exportService.exportSalesReportToPdf(startDate, endDate);

        HttpHeaders headers = baseDownloadHeaders(
                "reporte_ventas_" + LocalDate.now() + ".pdf",
                MediaType.APPLICATION_PDF
        );

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/products/excel")
    public ResponseEntity<byte[]> exportProductReportExcel() {
        byte[] excelBytes = exportService.exportProductReportToExcel();

        HttpHeaders headers = baseDownloadHeaders(
                "reporte_productos_" + LocalDate.now() + ".xlsx",
                XLSX_MEDIA_TYPE
        );

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/clients/csv")
    public ResponseEntity<byte[]> exportClientReportCsv() {
        byte[] csvBytes = exportService.exportClientReportToCsv();

        HttpHeaders headers = baseDownloadHeaders(
                "reporte_clientes_" + LocalDate.now() + ".csv",
                MediaType.parseMediaType("text/csv;charset=UTF-8")
        );

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }
}
