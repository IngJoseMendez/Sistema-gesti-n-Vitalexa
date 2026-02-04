package org.example.sistema_gestion_vitalexa.service;

import java.util.UUID;

/**
 * Service for exporting client data to Excel format
 */
public interface ClientExportService {

    /**
     * Export all clients for a specific vendedor to Excel
     * 
     * @param vendedorId ID of the vendedor
     * @return Excel file as byte array
     */
    byte[] exportClientsByVendedor(UUID vendedorId);

    /**
     * Export clients filtered by address keyword to Excel
     * 
     * @param addressKeyword Keyword to search in address (case-insensitive)
     * @return Excel file as byte array
     */
    byte[] exportClientsByAddress(String addressKeyword);

    /**
     * Export ALL clients in the system to Excel
     * 
     * @return Excel file as byte array
     */
    byte[] exportAllClients();
}
