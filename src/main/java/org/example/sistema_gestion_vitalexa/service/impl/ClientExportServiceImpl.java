package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.sistema_gestion_vitalexa.entity.Client;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.ClientRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.ClientExportService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientExportServiceImpl implements ClientExportService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    @Override
    public byte[] exportClientsByVendedor(UUID vendedorId) {
        // Get vendor to check if it's a shared user
        User vendedor = userRepository.findById(vendedorId)
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

        List<Client> clients;
        if (UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
            // Get clients for both shared usernames
            List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendedor.getUsername());
            clients = clientRepository.findByVendedorAsignadoUsernameIn(sharedUsernames);
        } else {
            clients = clientRepository.findByVendedorAsignadoId(vendedorId);
        }

        if (clients.isEmpty()) {
            throw new BusinessExeption("No se encontraron clientes para esta vendedora");
        }

        return generateExcel(clients, "Clientes por Vendedora");
    }

    @Override
    public byte[] exportClientsByAddress(String addressKeyword) {
        List<Client> clients = clientRepository.findByDireccionContainingIgnoreCase(addressKeyword);

        if (clients.isEmpty()) {
            throw new BusinessExeption("No se encontraron clientes con esa dirección");
        }

        return generateExcel(clients, "Clientes por Ruta");
    }

    @Override
    public byte[] exportAllClients() {
        List<Client> clients = clientRepository.findAll();

        if (clients.isEmpty()) {
            throw new BusinessExeption("No hay clientes registrados en el sistema");
        }

        return generateExcel(clients, "Todos los Clientes");
    }

    private byte[] generateExcel(List<Client> clients, String sheetName) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] columns = {
                    "NIT",
                    "Nombre Establecimiento",
                    "Email",
                    "Teléfono",
                    "Dirección",
                    "Vendedora Asignada",
                    "Administrador",
                    "Representante Legal"
            };

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (Client client : clients) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(client.getNit() != null ? client.getNit() : "");
                row.createCell(1).setCellValue(client.getNombre() != null ? client.getNombre() : "");
                row.createCell(2).setCellValue(client.getEmail() != null ? client.getEmail() : "");
                row.createCell(3).setCellValue(client.getTelefono() != null ? client.getTelefono() : "");
                row.createCell(4).setCellValue(client.getDireccion() != null ? client.getDireccion() : "");
                row.createCell(5).setCellValue(
                        client.getVendedorAsignado() != null ? client.getVendedorAsignado().getUsername() : "");
                row.createCell(6).setCellValue(client.getAdministrador() != null ? client.getAdministrador() : "");
                row.createCell(7)
                        .setCellValue(client.getRepresentanteLegal() != null ? client.getRepresentanteLegal() : "");
            }

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (IOException e) {
            throw new BusinessExeption("Error generando archivo Excel: " + e.getMessage());
        }
    }
}
