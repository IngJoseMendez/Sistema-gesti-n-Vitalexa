package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.ClientBalanceDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestión de saldos de clientes
 */
public interface ClientBalanceService {

    /**
     * Obtiene el saldo de un cliente específico
     */
    ClientBalanceDTO getClientBalance(UUID clientId);

    /**
     * Obtiene todos los saldos (Owner/Admin)
     */
    List<ClientBalanceDTO> getAllClientBalances();

    /**
     * Obtiene los saldos de clientes de una vendedora específica
     */
    List<ClientBalanceDTO> getClientBalancesByVendedor(UUID vendedorId);

    /**
     * Obtiene los saldos para la vendedora autenticada (solo sus clientes)
     */
    List<ClientBalanceDTO> getMyClientBalances(String vendedorUsername);

    /**
     * Establece el saldo inicial de un cliente (solo una vez, Owner)
     */
    void setInitialBalance(UUID clientId, BigDecimal initialBalance, String ownerUsername);

    /**
     * Establece el tope de crédito de un cliente (Owner)
     */
    void setCreditLimit(UUID clientId, BigDecimal creditLimit, String ownerUsername);

    /**
     * Elimina el tope de crédito de un cliente (Owner)
     */
    void removeCreditLimit(UUID clientId, String ownerUsername);
}
