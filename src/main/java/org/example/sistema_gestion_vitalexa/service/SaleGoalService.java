package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.CreateSaleGoalRequest;
import org.example.sistema_gestion_vitalexa.dto.SaleGoalResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSaleGoalRequest;
import org.example.sistema_gestion_vitalexa.dto.VendedorWithGoalResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SaleGoalService {

    // Admin/Owner
    SaleGoalResponse createGoal(CreateSaleGoalRequest request);

    SaleGoalResponse updateGoal(UUID id, UpdateSaleGoalRequest request);

    void deleteGoal(UUID id);

    List<SaleGoalResponse> findAll();

    List<SaleGoalResponse> findByMonthAndYear(int month, int year);

    SaleGoalResponse findById(UUID id);

    List<VendedorWithGoalResponse> findAllVendedoresWithCurrentGoal();

    // Vendedor
    SaleGoalResponse findMyCurrentGoal(String username);

    List<SaleGoalResponse> findMyGoalHistory(String username);

    // Sistema interno
    void updateGoalProgress(UUID vendedorId, BigDecimal saleAmount, int month, int year);

    /**
     * Recalcular completamente el progreso de una meta desde cero
     * Útil cuando se editan facturas históricas o hay inconsistencias
     */
    void recalculateGoalForVendorMonth(UUID vendedorId, int month, int year);
}
