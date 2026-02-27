package org.example.sistema_gestion_vitalexa.dto;

import java.util.List;

/**
 * DTO de inicio para vendedoras.
 * Agrupa productos, promociones y promociones especiales en UNA SOLA respuesta.
 *
 * OPTIMIZACIÓN DE RED: En lugar de hacer 3 peticiones HTTP separadas al abrir
 * la página (que fallan o tardan mucho con internet lento), se hace UNA SOLA
 * petición que trae todo lo necesario para empezar a tomar pedidos.
 *
 * Antes: 3 peticiones × latencia = muy lento con internet malo
 * Ahora: 1 petición = funciona incluso con señal débil
 */
public record VendedorInitDTO(
        List<ProductResponse> productos,
        List<PromotionResponse> promociones,
        List<SpecialPromotionResponse> promocionesEspeciales
) {
}

