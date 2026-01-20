package org.example.sistema_gestion_vitalexa.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.ApplyDiscountRequest;
import org.example.sistema_gestion_vitalexa.dto.DiscountResponse;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.OrderDiscount;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.DiscountStatus;
import org.example.sistema_gestion_vitalexa.enums.DiscountType;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.OrderDiscountRepository;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.DiscountService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DiscountServiceImpl implements DiscountService {

    private final OrderDiscountRepository discountRepository;
    private final OrdenRepository ordenRepository;
    private final UserRepository userRepository;

    @Override
    public DiscountResponse applyPresetDiscount(UUID orderId, DiscountType type, String adminUsername) {
        BigDecimal percentage = switch (type) {
            case ADMIN_10 -> new BigDecimal("10");
            case ADMIN_12 -> new BigDecimal("12");
            case ADMIN_15 -> new BigDecimal("15");
            default -> throw new BusinessExeption("Tipo de descuento inválido para preset");
        };

        return applyDiscount(orderId, percentage, type, null, adminUsername);
    }

    @Override
    public DiscountResponse applyCustomDiscount(ApplyDiscountRequest request, String adminUsername) {
        return applyDiscount(
                request.orderId(),
                request.percentage(),
                DiscountType.ADMIN_CUSTOM,
                request.reason(),
                adminUsername);
    }

    @Override
    public DiscountResponse revokeDiscount(UUID discountId, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        OrderDiscount discount = discountRepository.findById(discountId)
                .orElseThrow(() -> new BusinessExeption("Descuento no encontrado"));

        if (discount.getStatus() == DiscountStatus.REVOKED) {
            throw new BusinessExeption("El descuento ya fue revocado");
        }

        discount.revoke(owner);
        OrderDiscount saved = discountRepository.save(discount);

        // Recalcular total con descuento
        recalculateDiscountedTotal(discount.getOrder().getId());

        log.info("Descuento {} revocado por {}", discountId, ownerUsername);

        return toDiscountResponse(saved);
    }

    @Override
    public DiscountResponse applyOwnerDiscount(ApplyDiscountRequest request, String ownerUsername) {
        return applyDiscount(
                request.orderId(),
                request.percentage(),
                DiscountType.OWNER_ADDITIONAL,
                request.reason(),
                ownerUsername);
    }

    @Override
    public List<DiscountResponse> getDiscountsByOrderId(UUID orderId) {
        return discountRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::toDiscountResponse)
                .toList();
    }

    @Override
    public void recalculateDiscountedTotal(UUID orderId) {
        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        // Sumar todos los descuentos activos
        BigDecimal totalDiscountPercentage = discountRepository.sumActiveDiscountsByOrderId(orderId);

        // Limitar el descuento máximo a 100%
        if (totalDiscountPercentage.compareTo(new BigDecimal("100")) > 0) {
            totalDiscountPercentage = new BigDecimal("100");
        }

        // Calcular total con descuento
        BigDecimal originalTotal = order.getTotal();
        BigDecimal discountAmount = originalTotal
                .multiply(totalDiscountPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal discountedTotal = originalTotal.subtract(discountAmount);

        order.setDiscountPercentage(totalDiscountPercentage);
        order.setDiscountedTotal(discountedTotal);
        ordenRepository.save(order);

        log.info("Orden {} recalculada: descuento {}%, total con descuento ${}",
                orderId, totalDiscountPercentage, discountedTotal);
    }

    private DiscountResponse applyDiscount(UUID orderId, BigDecimal percentage,
            DiscountType type, String reason, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

        Order order = ordenRepository.findById(orderId)
                .orElseThrow(() -> new BusinessExeption("Orden no encontrada"));

        // Validar porcentaje
        if (percentage.compareTo(BigDecimal.ZERO) <= 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessExeption("El porcentaje debe estar entre 0 y 100");
        }

        // Crear el descuento
        OrderDiscount discount = OrderDiscount.builder()
                .order(order)
                .percentage(percentage)
                .type(type)
                .status(DiscountStatus.APPLIED)
                .appliedBy(user)
                .reason(reason)
                .build();

        OrderDiscount saved = discountRepository.save(discount);

        // Recalcular total con descuento
        recalculateDiscountedTotal(orderId);

        log.info("Descuento {}% aplicado a orden {} por {}", percentage, orderId, username);

        return toDiscountResponse(saved);
    }

    private DiscountResponse toDiscountResponse(OrderDiscount discount) {
        return new DiscountResponse(
                discount.getId(),
                discount.getOrder().getId(),
                discount.getPercentage(),
                discount.getType().name(),
                discount.getStatus().name(),
                discount.getAppliedBy() != null ? discount.getAppliedBy().getUsername() : null,
                discount.getRevokedBy() != null ? discount.getRevokedBy().getUsername() : null,
                discount.getCreatedAt(),
                discount.getRevokedAt(),
                discount.getReason());
    }
}
