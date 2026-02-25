package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.*;

import java.util.List;
import java.util.UUID;

public interface PayrollService {

    // ─── Configuración de nómina por vendedor ──────────────────────────────────

    /** Crear o actualizar la configuración de nómina de un vendedor */
    VendorPayrollConfigResponse saveConfig(VendorPayrollConfigRequest request);

    /** Obtener configuración de nómina de un vendedor */
    VendorPayrollConfigResponse getConfig(UUID vendedorId);

    /** Listar configuraciones de todos los vendedores */
    List<VendorPayrollConfigResponse> getAllConfigs();

    // ─── Cálculo de nómina ─────────────────────────────────────────────────────

    /**
     * Calcular (o recalcular) la nómina de un vendedor para un mes/año dado.
     * Si ya existe una nómina para ese mes/año, la sobreescribe.
     */
    PayrollResponse calculatePayroll(CalculatePayrollRequest request, UUID calculatedBy);

    /**
     * Calcular nóminas de TODOS los vendedores para un mes/año.
     */
    List<PayrollResponse> calculateAllPayrolls(int month, int year, UUID calculatedBy);

    // ─── Consultas ─────────────────────────────────────────────────────────────

    /** Obtener nómina de un vendedor en un mes/año específico */
    PayrollResponse findByVendedorAndMonthYear(UUID vendedorId, int month, int year);

    /** Historial de nóminas de un vendedor */
    List<PayrollResponse> findHistoryByVendedor(UUID vendedorId);

    /** Nóminas de todos los vendedores en un mes/año */
    List<PayrollResponse> findByMonthAndYear(int month, int year);

    /** Nómina propia del vendedor autenticado */
    PayrollResponse findMyPayroll(String username, int month, int year);

    /** Historial propio del vendedor autenticado */
    List<PayrollResponse> findMyPayrollHistory(String username);
}

