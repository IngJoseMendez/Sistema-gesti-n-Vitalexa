package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.BulkProductUpdateResult;
import org.example.sistema_gestion_vitalexa.dto.BulkStockArrivalRequestDTO;
import org.example.sistema_gestion_vitalexa.dto.CreateProductRequest;
import org.example.sistema_gestion_vitalexa.dto.ProductResponse;
import org.example.sistema_gestion_vitalexa.dto.StockSummaryDTO;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductBulkRequest;
import org.example.sistema_gestion_vitalexa.dto.UpdateProductRequest;
import org.example.sistema_gestion_vitalexa.entity.InventoryMovement;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {

        ProductResponse create(CreateProductRequest request);

        /** Crea múltiples productos en una sola transacción */
        List<ProductResponse> createBulk(List<CreateProductRequest> requests);

        /** Actualiza múltiples productos en una sola transacción */
        BulkProductUpdateResult updateBulk(List<UpdateProductBulkRequest> requests);

        ProductResponse update(UUID id, UpdateProductRequest request);

        void softDelete(UUID id);

        void hardDelete(UUID id);

        List<ProductResponse> findAllAdmin();

        List<ProductResponse> findAllActive();

        Page<ProductResponse> findAllActive(Pageable pageable);

        Page<ProductResponse> findAllActiveInStock(Pageable pageable);

        Page<ProductResponse> searchActive(String q, Pageable pageable);

        Product findEntityById(UUID id);

        ProductResponse findById(UUID id);

        void changeStatus(UUID id, boolean status);

        List<ProductResponse> findLowStock(int threshold);

        Page<ProductResponse> findByTag(UUID tagId, Pageable pageable);

        Page<ProductResponse> searchByTag(String q, UUID tagId, Pageable pageable);

        Product getSystemProductSurtido();

        /** Agrega stock a un producto (Llegada de mercancía) */
        InventoryMovement addStock(UUID productId, int quantity, String reason, String username);

        /** Carga masiva de stock */
        List<InventoryMovement> addStockBulk(BulkStockArrivalRequestDTO request, String username);

        /** Exportar inventario actual a Excel */
        byte[] exportInventoryToExcel();

        /** Exportar inventario actual a PDF */
        byte[] exportInventoryToPdf();

        /**
         * Reporte completo: stock real vs stock comprometido en pedidos activos.
         * stockDisponible = stockReal - stockComprometido (puede ser negativo)
         */
        List<StockSummaryDTO> getStockReport();

        /**
         * Solo productos con stockDisponible negativo → alerta crítica.
         */
        List<StockSummaryDTO> getStockAlerts();
}

