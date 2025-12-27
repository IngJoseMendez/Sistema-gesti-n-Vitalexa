package org.example.sistema_gestion_vitalexa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.ReembolsoRequest;
import org.example.sistema_gestion_vitalexa.dto.ReembolsoResponse;
import org.example.sistema_gestion_vitalexa.mapper.ProductMapper;
import org.example.sistema_gestion_vitalexa.entity.*;
import org.example.sistema_gestion_vitalexa.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReembolsoService {

    private final ReembolsoRepository reembolsoRepository;
    private final ReembolsoItemRepository reembolsoItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductMapper productMapper;
    private final NotificationService notificationService; // ✅ AGREGAR

    @Transactional
    public ReembolsoResponse crearReembolso(ReembolsoRequest request, String username) {
        log.info("Creando reembolso para empacador: {}", username);

        User empacador = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("El reembolso debe tener al menos un producto");
        }

        Reembolso reembolso = Reembolso.builder()
                .empacador(empacador)
                .notas(request.getNotas())
                .estado(Reembolso.EstadoReembolso.CONFIRMADO)
                .build();

        reembolso = reembolsoRepository.save(reembolso);

        for (ReembolsoRequest.ReembolsoItemRequest itemRequest : request.getItems()) {
            Product producto = productRepository.findById(itemRequest.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + itemRequest.getProductoId()));

            if (producto.getStock() < itemRequest.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para: " + producto.getNombre() +
                        " (disponible: " + producto.getStock() + ", solicitado: " + itemRequest.getCantidad() + ")");
            }

            producto.setStock(producto.getStock() - itemRequest.getCantidad());
            productRepository.save(producto);

            // 🔔 NOTIFICAR CAMBIO DE STOCK POR REEMBOLSO
            notificationService.sendInventoryUpdate(producto.getId().toString(), "STOCK_UPDATED");

            ReembolsoItem item = ReembolsoItem.builder()
                    .reembolso(reembolso)
                    .producto(producto)
                    .cantidad(itemRequest.getCantidad())
                    .build();

            reembolsoItemRepository.save(item);
            reembolso.getItems().add(item);

            log.info("Producto descontado: {} - Cantidad: {} - Stock restante: {}",
                    producto.getNombre(), itemRequest.getCantidad(), producto.getStock());
        }

        // 🔔 NOTIFICAR NUEVO REEMBOLSO
        notificationService.sendReembolsoCreated(reembolso.getId().toString(), empacador.getUsername());

        return productMapper.toReembolsoResponse(reembolso);
    }

    public List<ReembolsoResponse> obtenerReembolsosPorEmpacador(String username) {
        User empacador = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return productMapper.toReembolsoResponseList(
                reembolsoRepository.findByEmpacadorOrderByFechaDesc(empacador)
        );
    }

    public List<ReembolsoResponse> obtenerTodosLosReembolsos() {
        return productMapper.toReembolsoResponseList(
                reembolsoRepository.findAllByOrderByFechaDesc()
        );
    }
}
