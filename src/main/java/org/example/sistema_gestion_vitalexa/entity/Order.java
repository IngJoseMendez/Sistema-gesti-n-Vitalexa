package org.example.sistema_gestion_vitalexa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sistema_gestion_vitalexa.enums.OrdenStatus;
import org.example.sistema_gestion_vitalexa.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrdenStatus estado;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "invoice_number", unique = true)
    private Long invoiceNumber;

    @Column(name = "include_freight")
    @Builder.Default
    private Boolean includeFreight = false;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "is_freight_bonified")
    @Builder.Default
    private Boolean isFreightBonified = false;

    @Column(name = "freight_custom_text")
    private String freightCustomText;

    @Column(name = "freight_quantity")
    @Builder.Default
    private Integer freightQuantity = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private User vendedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id") // ← QUITAR nullable = false para permitir sin cliente
    private Client cliente;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Porcentaje de descuento aplicado
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    // Total con descuento aplicado
    @Column(name = "discounted_total", precision = 12, scale = 2)
    private BigDecimal discountedTotal;

    // Estado de pago
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    // Relación con pagos
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    // Relación con descuentos (auditoría)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderDiscount> discounts = new ArrayList<>();

    public Order(User vendedor, Client cliente) {
        this.vendedor = vendedor;
        this.cliente = cliente;
        this.estado = OrdenStatus.PENDIENTE;
        this.paymentStatus = PaymentStatus.PENDING; // Corrección: inicializar explícitamente
        this.fecha = LocalDateTime.now();
        this.total = BigDecimal.ZERO;
        this.items = new ArrayList<>();
    }

    // Agregar item y recalcular
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotal(); // ← Corregido el typo
    }

    // Remover item y recalcular
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        recalculateTotal();
    }

    // Recalcular total respetando precios fijos de promociones
    public void recalculateTotal() {
        java.util.Set<UUID> processedPromoInstances = new java.util.HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItem item : items) {
            // ✅ CRÍTICO: Si es item de promoción con instancia ID y precio fijo,
            // solo agregarlo una vez (evitar duplicación de precios)
            if (Boolean.TRUE.equals(item.getIsPromotionItem()) &&
                    item.getPromotionInstanceId() != null &&
                    item.getPromotionPackPrice() != null) {

                // Si ya procesamos esta instancia de promoción, no agregar de nuevo
                if (!processedPromoInstances.contains(item.getPromotionInstanceId())) {
                    total = total.add(item.getPromotionPackPrice());
                    processedPromoInstances.add(item.getPromotionInstanceId());
                }
            } else {
                // Items normales o items de regalo sin precio fijo: suma normal
                total = total.add(item.getSubTotal());
            }
        }

        this.total = total;
    }

    // Limpiar items (para edición)
    public void clearItems() {
        items.forEach(item -> item.setOrder(null));
        items.clear();
        this.total = BigDecimal.ZERO;
    }

    // Verificar si la orden tiene productos sin stock
    public boolean hasOutOfStockItems() {
        return items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getOutOfStock()));
    }
}
