package org.example.sistema_gestion_vitalexa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
                UUID id,
                String vendedor,
                String cliente,
                BigDecimal total,
                String estado,
                LocalDateTime fecha,
                List<OrderItemResponse> items,
                String notas,
                Long invoiceNumber,
                Boolean isSROrder,
                Boolean includeFreight,
                Boolean isFreightBonified,
                String freightCustomText,
                Integer freightQuantity,
                String representanteLegal,
                String clientePhone,
                String clienteAddress,
                String clienteNit,
                String clienteEmail

) {
}
