package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import java.util.List;

public interface ProductAuditService {
    /**
     * Genera un PDF de auditoría (Huella Contable) para un lote de productos.
     * 
     * @param products      Lista de productos afectados.
     * @param username      Nombre del usuario que realizó la acción.
     * @param operationType Tipo de operación (CREACIÓN, ACTUALIZACIÓN,
     *                      ELIMINACIÓN).
     * @return Array de bytes del PDF generado.
     */
    byte[] generateProductAudit(List<ProductResponse> products, String username, String operationType);
}
