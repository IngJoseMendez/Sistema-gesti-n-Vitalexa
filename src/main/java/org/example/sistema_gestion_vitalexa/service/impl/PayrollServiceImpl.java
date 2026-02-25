package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.*;
import org.example.sistema_gestion_vitalexa.entity.*;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.repository.*;
import org.example.sistema_gestion_vitalexa.service.PayrollService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PayrollServiceImpl implements PayrollService {

    private final PayrollRepository payrollRepository;
    private final VendorPayrollConfigRepository configRepository;
    private final UserRepository userRepository;
    private final OrdenRepository ordenRepository;
    private final PaymentRepository paymentRepository;
    private final SaleGoalRepository saleGoalRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIGURACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public VendorPayrollConfigResponse saveConfig(VendorPayrollConfigRequest request) {
        User vendedor = findVendedor(request.vendedorId());

        VendorPayrollConfig config = configRepository.findByVendedor(vendedor)
                .orElse(VendorPayrollConfig.builder().vendedor(vendedor).build());

        if (request.baseSalary() != null)               config.setBaseSalary(request.baseSalary());
        if (request.salesCommissionPct() != null)       config.setSalesCommissionPct(request.salesCommissionPct());
        if (request.collectionCommissionPct() != null)  config.setCollectionCommissionPct(request.collectionCommissionPct());
        if (request.collectionThresholdPct() != null)   config.setCollectionThresholdPct(request.collectionThresholdPct());
        if (request.generalCommissionEnabled() != null) config.setGeneralCommissionEnabled(request.generalCommissionEnabled());
        if (request.generalCommissionPct() != null)     config.setGeneralCommissionPct(request.generalCommissionPct());

        VendorPayrollConfig saved = configRepository.save(config);
        log.info("Configuración de nómina guardada para vendedor {}", vendedor.getUsername());
        return toConfigResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorPayrollConfigResponse getConfig(UUID vendedorId) {
        User vendedor = findVendedor(vendedorId);
        VendorPayrollConfig config = configRepository.findByVendedor(vendedor)
                .orElse(defaultConfig(vendedor));
        return toConfigResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorPayrollConfigResponse> getAllConfigs() {
        List<User> vendedores = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.VENDEDOR && u.isActive())
                // Yicela comparte nómina con Nina — no mostrar config duplicada
                .filter(u -> !u.getUsername().equals(UserUnificationUtil.YICELA_SANDOVAL))
                .toList();

        return vendedores.stream().map(v -> {
            VendorPayrollConfig config = configRepository.findByVendedor(v)
                    .orElse(defaultConfig(v));
            return toConfigResponse(config);
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CÁLCULO DE NÓMINA
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public PayrollResponse calculatePayroll(CalculatePayrollRequest request, UUID calculatedBy) {
        User vendedor = findVendedor(request.vendedorId());
        VendorPayrollConfig config = configRepository.findByVendedor(vendedor)
                .orElse(defaultConfig(vendedor));

        int month = request.month();
        int year  = request.year();

        // ── 1. Ventas del mes actual ──────────────────────────────────────────
        BigDecimal totalSold = calculateTotalSold(vendedor, month, year);

        // ── 2. Meta de ventas ─────────────────────────────────────────────────
        BigDecimal salesGoalTarget = saleGoalRepository
                .findByVendedorAndMonthAndYear(vendedor, month, year)
                .map(SaleGoal::getTargetAmount)
                .orElse(BigDecimal.ZERO);

        boolean salesGoalMet = salesGoalTarget.compareTo(BigDecimal.ZERO) > 0
                && totalSold.compareTo(salesGoalTarget) >= 0;

        BigDecimal salesCommissionPct    = config.getSalesCommissionPct();
        BigDecimal salesCommissionAmount = salesGoalMet
                ? totalSold.multiply(salesCommissionPct).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── 3. Recaudo del mes anterior ───────────────────────────────────────
        // prevMonth/prevYear = mes cuyas FACTURAS se deben cobrar ahora
        YearMonth prevYearMonth = YearMonth.of(year, month).minusMonths(1);
        int prevMonth = prevYearMonth.getMonthValue();
        int prevYear  = prevYearMonth.getYear();

        // Total vendido el mes ANTERIOR (base para calcular el % de recaudo)
        BigDecimal prevMonthTotalSold = calculateTotalSold(vendedor, prevMonth, prevYear);

        // Total recaudado ESTE MES (month/year) de facturas del MES ANTERIOR (prevMonth/prevYear)
        BigDecimal totalCollected = calculateTotalCollected(vendedor, month, year, prevMonth, prevYear);

        BigDecimal collectionPct = BigDecimal.ZERO;
        if (prevMonthTotalSold.compareTo(BigDecimal.ZERO) > 0) {
            collectionPct = totalCollected
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevMonthTotalSold, 4, RoundingMode.HALF_UP);
        }

        // El umbral de recaudo se guarda como fracción (0.80), lo convertimos a %
        BigDecimal thresholdAsPercent = config.getCollectionThresholdPct()
                .multiply(BigDecimal.valueOf(100));

        boolean collectionGoalMet = prevMonthTotalSold.compareTo(BigDecimal.ZERO) > 0
                && collectionPct.compareTo(thresholdAsPercent) >= 0;

        BigDecimal collectionCommissionPct    = config.getCollectionCommissionPct();
        BigDecimal collectionCommissionAmount = collectionGoalMet
                ? totalCollected.multiply(collectionCommissionPct).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── 4. Comisión general por metas globales ────────────────────────────
        boolean generalEnabled = Boolean.TRUE.equals(config.getGeneralCommissionEnabled());
        BigDecimal totalGlobalGoals = BigDecimal.ZERO;
        BigDecimal generalCommissionPct    = config.getGeneralCommissionPct();
        BigDecimal generalCommissionAmount = BigDecimal.ZERO;

        if (generalEnabled) {
            totalGlobalGoals = saleGoalRepository.findByMonthAndYear(month, year).stream()
                    .map(SaleGoal::getTargetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            generalCommissionAmount = totalGlobalGoals
                    .multiply(generalCommissionPct)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // ── 5. Totales ────────────────────────────────────────────────────────
        BigDecimal totalCommissions = salesCommissionAmount
                .add(collectionCommissionAmount)
                .add(generalCommissionAmount);
        BigDecimal totalPayout = config.getBaseSalary().add(totalCommissions);

        // ── 6. Persistir ──────────────────────────────────────────────────────
        Payroll payroll = payrollRepository
                .findByVendedorAndMonthAndYear(vendedor, month, year)
                .orElse(Payroll.builder().vendedor(vendedor).month(month).year(year).build());

        payroll.setBaseSalary(config.getBaseSalary());
        payroll.setSalesGoalTarget(salesGoalTarget);
        payroll.setTotalSold(totalSold);
        payroll.setSalesGoalMet(salesGoalMet);
        payroll.setSalesCommissionPct(salesCommissionPct);
        payroll.setSalesCommissionAmount(salesCommissionAmount);
        payroll.setPrevMonthTotalSold(prevMonthTotalSold);
        payroll.setTotalCollected(totalCollected);
        payroll.setCollectionPct(collectionPct);
        payroll.setCollectionGoalMet(collectionGoalMet);
        payroll.setCollectionCommissionPct(collectionCommissionPct);
        payroll.setCollectionCommissionAmount(collectionCommissionAmount);
        payroll.setGeneralCommissionEnabled(generalEnabled);
        payroll.setTotalGlobalGoals(totalGlobalGoals);
        payroll.setGeneralCommissionPct(generalCommissionPct);
        payroll.setGeneralCommissionAmount(generalCommissionAmount);
        payroll.setTotalCommissions(totalCommissions);
        payroll.setTotalPayout(totalPayout);
        payroll.setNotes(request.notes());
        payroll.setCalculatedBy(calculatedBy);

        Payroll saved = payrollRepository.save(payroll);
        log.info("Nómina calculada para {} {}/{}: base={}, ventas={}, recaudo={}, general={}, TOTAL={}",
                vendedor.getUsername(), month, year,
                config.getBaseSalary(), salesCommissionAmount,
                collectionCommissionAmount, generalCommissionAmount, totalPayout);

        return toResponse(saved);
    }

    @Override
    public List<PayrollResponse> calculateAllPayrolls(int month, int year, UUID calculatedBy) {
        List<User> vendedores = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.VENDEDOR && u.isActive())
                // Para usuarios compartidos (Nina/Yicela), solo calcular UNA vez
                // usando el usuario canónico (NinaTorres). Yicela se omite.
                .filter(u -> !u.getUsername().equals(UserUnificationUtil.YICELA_SANDOVAL))
                .toList();

        return vendedores.stream().map(v ->
                calculatePayroll(new CalculatePayrollRequest(v.getId(), month, year, null), calculatedBy)
        ).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PayrollResponse findByVendedorAndMonthYear(UUID vendedorId, int month, int year) {
        User vendedor = findVendedor(vendedorId);
        Payroll payroll = payrollRepository.findByVendedorAndMonthAndYear(vendedor, month, year)
                .orElseThrow(() -> new BusinessExeption(
                        "No existe nómina para este vendedor en " + month + "/" + year));
        return toResponse(payroll);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollResponse> findHistoryByVendedor(UUID vendedorId) {
        return payrollRepository.findByVendedorIdOrderByYearDescMonthDesc(vendedorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollResponse> findByMonthAndYear(int month, int year) {
        return payrollRepository.findByMonthAndYear(month, year)
                .stream()
                // Nunca habrá nómina de Yicela (se calcula bajo Nina),
                // pero por si acaso filtrar para no mostrar duplicado
                .filter(p -> !p.getVendedor().getUsername().equals(UserUnificationUtil.YICELA_SANDOVAL))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollResponse findMyPayroll(String username, int month, int year) {
        User vendedor = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
        // Yicela ve la misma nómina que Nina
        vendedor = resolveCanonicalVendedor(vendedor);
        Payroll payroll = payrollRepository.findByVendedorAndMonthAndYear(vendedor, month, year)
                .orElseThrow(() -> new BusinessExeption(
                        "Aún no tienes nómina calculada para " + month + "/" + year));
        return toResponse(payroll);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollResponse> findMyPayrollHistory(String username) {
        User vendedor = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));
        // Yicela ve el mismo historial que Nina
        vendedor = resolveCanonicalVendedor(vendedor);
        return payrollRepository.findByVendedorOrderByYearDescMonthDesc(vendedor)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS INTERNOS
    // ─────────────────────────────────────────────────────────────────────────

    private User findVendedor(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));
        if (user.getRole() != Role.VENDEDOR) {
            throw new BusinessExeption("El usuario debe tener rol VENDEDOR");
        }
        return resolveCanonicalVendedor(user);
    }

    /**
     * Para usuarios compartidos (NinaTorres / YicelaSandoval), siempre
     * retorna el usuario CANÓNICO (NinaTorres) como titular de la nómina.
     * Así una sola nómina representa a ambas, sin duplicados.
     */
    private User resolveCanonicalVendedor(User user) {
        if (UserUnificationUtil.isSharedUser(user.getUsername())
                && user.getUsername().equals(UserUnificationUtil.YICELA_SANDOVAL)) {
            return userRepository.findByUsername(UserUnificationUtil.NINA_TORRES)
                    .orElse(user); // fallback: si no existe Nina, usar Yicela
        }
        return user;
    }

    private VendorPayrollConfig defaultConfig(User vendedor) {
        return VendorPayrollConfig.builder().vendedor(vendedor).build();
    }

    /**
     * Calcula el total vendido por el vendedor en un mes/año dado usando rango
     * de fechas calendario exacto (1ro al último día del mes).
     * Cuenta todas las órdenes NO ANULADAS cuya fecha (o.fecha) cae en ese mes.
     */
    private BigDecimal calculateTotalSold(User vendedor, int month, int year) {
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime end   = start.plusMonths(1); // exclusivo: primer día del mes siguiente

        if (UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
            List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendedor.getUsername());
            List<UUID> sharedIds = userRepository.findAll().stream()
                    .filter(u -> sharedUsernames.contains(u.getUsername()))
                    .map(User::getId)
                    .collect(Collectors.toList());
            return ordenRepository.sumTotalSoldByVendedorIdsBetween(sharedIds, start, end);
        }
        return ordenRepository.sumTotalSoldByVendedorBetween(vendedor.getId(), start, end);
    }

    /**
     * Calcula el total recaudado DURANTE el mes de pago (paymentMonth/paymentYear)
     * proveniente ÚNICAMENTE de órdenes cuya fecha cae en el mes anterior
     * (orderMonth/orderYear).
     *
     * Usa rangos de fecha exactos para respetar el mes calendario:
     *   pago:  [1-feb-2026 00:00:00 , 1-mar-2026 00:00:00)
     *   orden: [1-ene-2026 00:00:00 , 1-feb-2026 00:00:00)
     */
    private BigDecimal calculateTotalCollected(User vendedor,
                                               int paymentMonth, int paymentYear,
                                               int orderMonth,   int orderYear) {
        LocalDateTime payStart  = LocalDateTime.of(paymentYear, paymentMonth, 1, 0, 0, 0);
        LocalDateTime payEnd    = payStart.plusMonths(1);          // exclusivo
        LocalDateTime orderStart = LocalDateTime.of(orderYear, orderMonth, 1, 0, 0, 0);
        LocalDateTime orderEnd   = orderStart.plusMonths(1);       // exclusivo

        if (UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
            List<String> sharedUsernames = UserUnificationUtil.getSharedUsernames(vendedor.getUsername());
            List<UUID> sharedIds = userRepository.findAll().stream()
                    .filter(u -> sharedUsernames.contains(u.getUsername()))
                    .map(User::getId)
                    .collect(Collectors.toList());
            return paymentRepository.sumCollectedByVendedorIdsBetween(
                    sharedIds, payStart, payEnd, orderStart, orderEnd);
        }
        return paymentRepository.sumCollectedByVendedorBetween(
                vendedor.getId(), payStart, payEnd, orderStart, orderEnd);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPPERS
    // ─────────────────────────────────────────────────────────────────────────

    private PayrollResponse toResponse(Payroll p) {
        return new PayrollResponse(
                p.getId(),
                p.getVendedor().getId(),
                p.getVendedor().getUsername(),
                p.getMonth(),
                p.getYear(),
                p.getBaseSalary(),
                p.getSalesGoalTarget(),
                p.getTotalSold(),
                Boolean.TRUE.equals(p.getSalesGoalMet()),
                p.getSalesCommissionPct(),
                p.getSalesCommissionAmount(),
                p.getPrevMonthTotalSold(),
                p.getTotalCollected(),
                p.getCollectionPct(),
                Boolean.TRUE.equals(p.getCollectionGoalMet()),
                p.getCollectionCommissionPct(),
                p.getCollectionCommissionAmount(),
                Boolean.TRUE.equals(p.getGeneralCommissionEnabled()),
                p.getTotalGlobalGoals(),
                p.getGeneralCommissionPct(),
                p.getGeneralCommissionAmount(),
                p.getTotalCommissions(),
                p.getTotalPayout(),
                p.getNotes(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private VendorPayrollConfigResponse toConfigResponse(VendorPayrollConfig c) {
        return new VendorPayrollConfigResponse(
                c.getId(),
                c.getVendedor().getId(),
                c.getVendedor().getUsername(),
                c.getBaseSalary(),
                c.getSalesCommissionPct(),
                c.getCollectionCommissionPct(),
                c.getCollectionThresholdPct(),
                Boolean.TRUE.equals(c.getGeneralCommissionEnabled()),
                c.getGeneralCommissionPct()
        );
    }
}

