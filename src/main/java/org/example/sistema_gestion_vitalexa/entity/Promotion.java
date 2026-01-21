package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sistema_gestion_vitalexa.enums.PromotionType;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad para gestionar promociones.
 * Solo Admin y Owner pueden crear/modificar promociones.
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;

    // Cantidad que se debe comprar
    @Column(name = "buy_quantity", nullable = false)
    private Integer buyQuantity;

    // Cantidad de productos surtidos/gratis
    @Column(name = "free_quantity")
    private Integer freeQuantity;

    // Precio del pack completo (para tipo PACK)
    @Column(name = "pack_price", precision = 12, scale = 2)
    private BigDecimal packPrice;

    // Producto principal de la promoción
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_product_id", nullable = false)
    private Product mainProduct;

    // Producto gratis específico (para BUY_GET_FREE)
    // Null si los surtidos son variables (seleccionados por admin)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "free_product_id")
    private Product freeProduct;

    // Control de combinación con descuentos normales
    @Column(name = "allow_stack_with_discounts")
    @Builder.Default
    private Boolean allowStackWithDiscounts = false;

    // Para tipo PACK: si admin debe seleccionar los surtidos
    @Column(name = "requires_assortment_selection")
    @Builder.Default
    private Boolean requiresAssortmentSelection = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Verifica si la promoción está dentro del período de validez
     */
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = validFrom == null || now.isAfter(validFrom);
        boolean beforeEnd = validUntil == null || now.isBefore(validUntil);
        return active && afterStart && beforeEnd;
    }
}
