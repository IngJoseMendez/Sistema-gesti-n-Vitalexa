package org.example.sistema_gestion_vitalexa.service;

import org.example.sistema_gestion_vitalexa.dto.PayrollResponse;

import java.util.List;

public interface PayrollExportService {

    /** Excel con nóminas de TODOS los vendedores en un mes/año */
    byte[] exportAllPayrollsToExcel(int month, int year);

    /** Excel con la nómina de un vendedor específico en un mes/año */
    byte[] exportVendorPayrollToExcel(java.util.UUID vendedorId, int month, int year);

    /** PDF con nóminas de TODOS los vendedores en un mes/año */
    byte[] exportAllPayrollsToPdf(int month, int year);

    /** PDF con la nómina de un vendedor específico en un mes/año */
    byte[] exportVendorPayrollToPdf(java.util.UUID vendedorId, int month, int year);
}

