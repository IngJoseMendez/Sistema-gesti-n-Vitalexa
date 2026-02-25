package org.example.sistema_gestion_vitalexa.controller;

import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.ReportDTO;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ReportExportService;
import org.example.sistema_gestion_vitalexa.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports/export")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'VENDEDOR')")
public class ReportExportController {

        private final ReportService reportService;
        private final ReportExportService exportService;
        private final UserRepository userRepository;

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
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        Authentication authentication) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                User currentUser = userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                ReportDTO report;
                if (currentUser.getRole() == Role.VENDEDOR) {
                        report = reportService.getCompleteReport(startDate, endDate, currentUser.getId());
                } else {
                        report = reportService.getCompleteReport(startDate, endDate);
                }

                byte[] pdfBytes = exportService.exportReportToPdf(report, startDate, endDate);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_completo_" + LocalDate.now() + ".pdf",
                                MediaType.APPLICATION_PDF);

                return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        }

        @GetMapping("/complete/excel")
        public ResponseEntity<byte[]> exportCompleteReportExcel(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        Authentication authentication) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                User currentUser = userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                ReportDTO report;
                byte[] excelBytes;
                String filename;

                if (currentUser.getRole() == Role.VENDEDOR) {
                        // La vendedora solo ve su propio reporte
                        report = reportService.getCompleteReport(startDate, endDate, currentUser.getId());
                        excelBytes = exportService.exportReportToExcel(report, startDate, endDate, currentUser.getId());
                        filename = "reporte_" + currentUser.getUsername() + "_" + LocalDate.now() + ".xlsx";
                } else {
                        report = reportService.getCompleteReport(startDate, endDate);
                        excelBytes = exportService.exportReportToExcel(report, startDate, endDate);
                        filename = "reporte_completo_" + LocalDate.now() + ".xlsx";
                }

                HttpHeaders headers = baseDownloadHeaders(filename, XLSX_MEDIA_TYPE);
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
         * - Admin/Owner: pueden descargar el reporte de cualquier vendedor
         * - Vendedor: solo puede descargar su propio reporte
         */
        @GetMapping("/vendor/{vendedorId}/excel")
        public ResponseEntity<byte[]> exportVendorReportExcel(
                        @PathVariable UUID vendedorId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        Authentication authentication) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                User currentUser = userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                // Si es VENDEDOR, solo puede descargar su propio reporte
                if (currentUser.getRole() == Role.VENDEDOR && !currentUser.getId().equals(vendedorId)) {
                        throw new BusinessExeption("No tienes permiso para descargar el reporte de otro vendedor");
                }

                // Obtenemos el reporte COMPLETO pero filtrado por vendedor
                ReportDTO report = reportService.getCompleteReport(startDate, endDate, vendedorId);

                // Usamos el método de exportación que soporta el filtro (para hojas como Saldos)
                byte[] excelBytes = exportService.exportReportToExcel(report, startDate, endDate, vendedorId);

                // Obtener nombre del vendedor para el archivo
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
         * - Admin/Owner: pueden descargar el reporte de cualquier vendedor
         * - Vendedor: solo puede descargar su propio reporte
         */
        @GetMapping("/vendor/{vendedorId}/pdf")
        public ResponseEntity<byte[]> exportVendorReportPdf(
                        @PathVariable UUID vendedorId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        Authentication authentication) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                User currentUser = userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                // Si es VENDEDOR, solo puede descargar su propio reporte
                if (currentUser.getRole() == Role.VENDEDOR && !currentUser.getId().equals(vendedorId)) {
                        throw new BusinessExeption("No tienes permiso para descargar el reporte de otro vendedor");
                }

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

        // =============================================
        // VENDEDOR - DESCARGA DE SU PROPIO REPORTE
        // =============================================

        /**
         * Descargar el Excel del propio reporte (para vendedoras autenticadas)
         * GET /api/reports/export/my/excel
         */
        @GetMapping("/my/excel")
        @PreAuthorize("hasAnyRole('VENDEDOR', 'ADMIN', 'OWNER')")
        public ResponseEntity<byte[]> exportMyReportExcel(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        Authentication authentication) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                User currentUser = userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                ReportDTO report = reportService.getCompleteReport(startDate, endDate, currentUser.getId());
                byte[] excelBytes = exportService.exportReportToExcel(report, startDate, endDate, currentUser.getId());

                String filename = "reporte_" + currentUser.getUsername() + "_" + LocalDate.now() + ".xlsx";
                HttpHeaders headers = baseDownloadHeaders(filename, XLSX_MEDIA_TYPE);
                return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        }

        /**
         * Descargar el PDF del propio reporte (para vendedoras autenticadas)
         * GET /api/reports/export/my/pdf
         */
        @GetMapping("/my/pdf")
        @PreAuthorize("hasAnyRole('VENDEDOR', 'ADMIN', 'OWNER')")
        public ResponseEntity<byte[]> exportMyReportPdf(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        Authentication authentication) {
                startDate = defaultStart(startDate);
                endDate = defaultEnd(endDate);

                User currentUser = userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                java.util.List<org.example.sistema_gestion_vitalexa.dto.VendorDailySalesDTO> allVendorReports = reportService
                                .getVendorDailySalesReport(startDate, endDate);

                org.example.sistema_gestion_vitalexa.dto.VendorDailySalesDTO vendorReport = allVendorReports.stream()
                                .filter(v -> v.vendedorId().equals(currentUser.getId().toString()))
                                .findFirst()
                                .orElseThrow(() -> new BusinessExeption(
                                                "No hay ventas en el período especificado"));

                byte[] pdfBytes = exportService.exportVendorReportPdf(vendorReport);

                HttpHeaders headers = baseDownloadHeaders(
                                "reporte_ventas_" + currentUser.getUsername() + "_" + LocalDate.now() + ".pdf",
                                MediaType.APPLICATION_PDF);
                return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        }
}
