package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import java.util.List;

public interface ProductAuditService {
    /**
     * Genera un PDF de auditoría (Huella Contable) para un lote de productos.
     * 
     * @param result        Resultado de la actualización masiva (exitosos y
     *                      fallidos).
     * @param username      Nombre del usuario que realizó la acción.
     * @param operationType Tipo de operación (CREACIÓN, ACTUALIZACIÓN,
     *                      ELIMINACIÓN).
     * @return Array de bytes del PDF generado.
     */
    byte[] generateProductAudit(org.example.sistema_gestion_vitalexa.dto.BulkProductUpdateResult result,
            String username, String operationType);

    /**
     * Genera un PDF de auditoría para una lista simple de productos (compatibilidad
     * retroactiva)
     */
    byte[] generateProductAudit(List<ProductResponse> products, String username, String operationType);
}
