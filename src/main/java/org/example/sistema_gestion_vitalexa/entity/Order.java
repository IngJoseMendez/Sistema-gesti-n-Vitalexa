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

    // Recalcular total
    public void recalculateTotal() {
        this.total = items.stream()
                .map(OrderItem::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
