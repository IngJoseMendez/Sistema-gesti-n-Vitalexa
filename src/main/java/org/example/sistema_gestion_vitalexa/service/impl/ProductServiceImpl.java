package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.Product;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.ProductMapper;
import org.example.sistema_gestion_vitalexa.repository.ProductRepository;
import org.example.sistema_gestion_vitalexa.service.NotificationService;
import org.example.sistema_gestion_vitalexa.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final NotificationService notificationService;

    @Override
    public ProductResponse create(CreateProductRequest request) {
        Product product = mapper.toEntity(request);
        Product saved = repository.save(product);

        // 🔔 NOTIFICAR CREACIÓN
        notificationService.sendInventoryUpdate(saved.getId().toString(), "PRODUCT_CREATED");

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {

        Product product = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));

        if (!product.isActive()) {
            throw new BusinessExeption("No se puede editar un producto eliminado");
        }

        // Actualizar solo los campos que no son null
        if (request.nombre() != null) {
            product.setNombre(request.nombre());
        }
        if (request.descripcion() != null) {
            product.setDescripcion(request.descripcion());
        }
        if (request.precio() != null) {
            product.setPrecio(request.precio());
        }
        if (request.stock() != null) {
            product.setStock(request.stock());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }

        Product updated = repository.save(product);

        // ✅ VERIFICAR NIVELES DE STOCK
        checkStockLevels(updated);

        // 🔔 NOTIFICAR ACTUALIZACIÓN
        notificationService.sendInventoryUpdate(updated.getId().toString(), "PRODUCT_UPDATED");

        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));

        log.info("Soft deleting producto ID: {}", id);
        product.setActive(false);
        repository.save(product);

        // 🔔 NOTIFICAR ELIMINACIÓN
        notificationService.sendInventoryUpdate(id.toString(), "PRODUCT_DELETED");

        log.info("Producto eliminado (soft delete) correctamente: {}", id);
    }

    @Override
    @Transactional
    public void hardDelete(UUID id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));
        log.info("Hard deleting producto ID: {}", id);
        repository.deleteById(id);

        // 🔔 NOTIFICAR ELIMINACIÓN
        notificationService.sendInventoryUpdate(id.toString(), "PRODUCT_DELETED");

        log.info("Producto eliminado físicamente: {}", id);
    }

    @Override
    public List<ProductResponse> findAllActive() {
        return repository.findByActiveTrue()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public Product findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));
    }

    @Override
    public ProductResponse findById(UUID id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Override
    @Transactional
    public void changeStatus(UUID productId, boolean activo) {
        Product product = repository.findById(productId)
                .orElseThrow(() -> new BusinessExeption("Producto no encontrado"));

        product.setActive(activo);
        repository.save(product);

        // 🔔 NOTIFICAR CAMBIO DE ESTADO
        notificationService.sendInventoryUpdate(productId.toString(), "PRODUCT_STATUS_CHANGED");
    }

    @Override
    public List<ProductResponse> findAllAdmin() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> findLowStock(int threshold) {
        return repository.findByStockLessThanAndActiveTrue(threshold)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Método auxiliar para verificar stock
    private void checkStockLevels(Product product) {
        if (product.getReorderPoint() != null && product.getReorderPoint() > 0) {
            if (product.getStock() == 0) {
                notificationService.sendOutOfStockAlert(
                        product.getId().toString(),
                        product.getNombre()
                );
            } else if (product.getStock() <= product.getReorderPoint()) {
                notificationService.sendLowStockAlert(
                        product.getId().toString(),
                        product.getNombre(),
                        product.getStock(),
                        product.getReorderPoint()
                );
            }
        }
    }
}
