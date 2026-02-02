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
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

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
                                ContentDisposition.attachment().filename(filename).build());

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
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                ReportDTO report = reportService.getCompleteReport(startDate, endDate);
                byte[] pdfBytes = exportService.exportReportToPdf(report, startDate, endDate);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_completo_" + LocalDate.now() + ".pdf",
                                MediaType.APPLICATION_PDF);

                return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        }

        @GetMapping("/complete/excel")

        public ResponseEntity<byte[]> exportCompleteReportExcel(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                ReportDTO report = reportService.getCompleteReport(startDate, endDate);
                byte[] excelBytes = exportService.exportReportToExcel(report, startDate, endDate);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_completo_" + LocalDate.now() + ".xlsx",
                                XLSX_MEDIA_TYPE);

                return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        }

        @GetMapping("/complete/csv")
        public ResponseEntity<byte[]> exportCompleteReportCsv(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                ReportDTO report = reportService.getCompleteReport(startDate, endDate);
                byte[] csvBytes = exportService.exportReportToCsv(report, startDate, endDate);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_completo_" + LocalDate.now() + ".csv",
                                MediaType.parseMediaType("text/csv;charset=UTF-8"));

                return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
        }

        // =============================================
        // EXPORTACIONES ESPECÍFICAS POR ÁREA
        // =============================================

        @GetMapping("/sales/pdf")
        public ResponseEntity<byte[]> exportSalesReportPdf(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                byte[] pdfBytes = exportService.exportSalesReportToPdf(startDate, endDate);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_ventas_" + LocalDate.now() + ".pdf",
                                MediaType.APPLICATION_PDF);

                return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        }

        @GetMapping("/products/excel")
        public ResponseEntity<byte[]> exportProductReportExcel() {
                byte[] excelBytes = exportService.exportProductReportToExcel();

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_productos_" + LocalDate.now() + ".xlsx",
                                XLSX_MEDIA_TYPE);

                return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        }

        @GetMapping("/clients/csv")
        public ResponseEntity<byte[]> exportClientReportCsv() {
                byte[] csvBytes = exportService.exportClientReportToCsv();

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_clientes_" + LocalDate.now() + ".csv",
                                MediaType.parseMediaType("text/csv;charset=UTF-8"));

                return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
        }

        // =============================================
        // DESCARGAR REPORTE POR VENDEDOR ESPECÍFICO
        // =============================================

        /**
         * Descargar Excel con ventas de un vendedor específico (solo su hoja)
         * GET /api/reports/export/vendor/{vendedorId}/excel
         */
        @GetMapping("/vendor/{vendedorId}/excel")
        public ResponseEntity<byte[]> exportVendorReportExcel(
                        @PathVariable java.util.UUID vendedorId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                // Ahora obtenemos el reporte COMPLETO pero filtrado por vendedor
                ReportDTO report = reportService.getCompleteReport(startDate, endDate, vendedorId);

                // Y usamos el método de exportación que soporta el filtro (para hojas como
                // Saldos)
                byte[] excelBytes = exportService.exportReportToExcel(report, startDate, endDate, vendedorId);

                // Obtener nombre del vendedor para el archivo (podemos sacarlo del reporte si
                // viene, o buscarlo)
                String vendorName = "Vendedor";
                try {
                        if (!report.vendorReport().topVendors().isEmpty()) {
                                vendorName = report.vendorReport().topVendors().get(0).vendorName();
                        }
                } catch (Exception e) {
                        // fallback
                }

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_completo_" + vendorName + "_" + LocalDate.now() + ".xlsx",
                                XLSX_MEDIA_TYPE);
                return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        }

        /**
         * Descargar PDF con ventas de un vendedor específico
         * GET /api/reports/export/vendor/{vendedorId}/pdf
         */
        @GetMapping("/vendor/{vendedorId}/pdf")
        public ResponseEntity<byte[]> exportVendorReportPdf(
                        @PathVariable java.util.UUID vendedorId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                java.util.List<org.example.sistema_gestion_vitalexa.dto.VendorDailySalesDTO> allVendorReports = reportService
                                .getVendorDailySalesReport(startDate, endDate);

                // Filtrar solo el vendedor solicitado
                org.example.sistema_gestion_vitalexa.dto.VendorDailySalesDTO vendorReport = allVendorReports.stream()
                                .filter(v -> v.vendedorId().equals(vendedorId.toString()))
                                .findFirst()
                                .orElseThrow(() -> new org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption(
                                                "No hay ventas para este vendedor en el período especificado"));

                byte[] pdfBytes = exportService.exportVendorReportPdf(vendorReport);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_ventas_" + vendorReport.vendedorName() + "_" + LocalDate.now() + ".pdf",
                                MediaType.APPLICATION_PDF);
                return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        }
}
