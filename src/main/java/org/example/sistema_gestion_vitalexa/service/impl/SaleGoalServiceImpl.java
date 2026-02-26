package org.example.sistema_gestion_vitalexa.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sistema_gestion_vitalexa.dto.CreateSaleGoalRequest;
import org.example.sistema_gestion_vitalexa.dto.SaleGoalResponse;
import org.example.sistema_gestion_vitalexa.dto.UpdateSaleGoalRequest;
import org.example.sistema_gestion_vitalexa.dto.VendedorWithGoalResponse;
import org.example.sistema_gestion_vitalexa.entity.Order;
import org.example.sistema_gestion_vitalexa.entity.SaleGoal;
import org.example.sistema_gestion_vitalexa.entity.User;
import org.example.sistema_gestion_vitalexa.enums.Role;
import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.example.sistema_gestion_vitalexa.mapper.SaleGoalMapper;
import org.example.sistema_gestion_vitalexa.repository.OrdenRepository;
import org.example.sistema_gestion_vitalexa.repository.SaleGoalRepository;
import org.example.sistema_gestion_vitalexa.repository.UserRepository;
import org.example.sistema_gestion_vitalexa.service.SaleGoalService;
import org.example.sistema_gestion_vitalexa.util.UserUnificationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SaleGoalServiceImpl implements SaleGoalService {

        private final SaleGoalRepository saleGoalRepository;
        private final UserRepository userRepository;
        private final SaleGoalMapper saleGoalMapper;
        private final OrdenRepository orderRepository;

        // =============================================
        // ADMIN/OWNER - GESTIÓN DE METAS
        // =============================================

        @Override
        public SaleGoalResponse createGoal(CreateSaleGoalRequest request) {
                // Validar que el usuario existe y es vendedor
                User vendedor = userRepository.findById(request.vendedorId())
                                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

                if (vendedor.getRole() != Role.VENDEDOR) {
                        throw new BusinessExeption("El usuario debe tener rol VENDEDOR");
                }

                // VALIDAR QUE NO SEA UN MES/AÑO DEL PASADO
                LocalDate now = LocalDate.now();
                LocalDate targetDate = LocalDate.of(request.year(), request.month(), 1);
                LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);

                if (targetDate.isBefore(firstDayOfCurrentMonth)) {
                        throw new BusinessExeption("No se pueden crear metas para meses pasados");
                }

                // Verificar que no exista ya una meta para ese vendedor/mes/año
                if (saleGoalRepository.existsByVendedorAndMonthAndYear(
                                vendedor, request.month(), request.year())) {
                        throw new BusinessExeption(
                                        "Ya existe una meta para este vendedor en " +
                                                        getMonthName(request.month()) + " " + request.year());
                }

                // CALCULAR VENTAS EXISTENTES SOLO SI ES EL MES ACTUAL
                BigDecimal existingSales = BigDecimal.ZERO;

                if (request.year() == now.getYear() && request.month() == now.getMonthValue()) {
                        // Es el mes actual, calcular ventas existentes
                        existingSales = calculateExistingSalesForMonth(
                                        vendedor.getId(),
                                        request.month(),
                                        request.year());
                        log.info("📊 Meta del mes actual - Ventas existentes del vendedor {}: ${}",
                                        vendedor.getUsername(), existingSales);
                } else {
                        // Es un mes futuro, iniciar en cero
                        log.info("📅 Meta futura creada para {}/{} - El vendedor {} inicia en $0",
                                        request.month(), request.year(), vendedor.getUsername());
                }

                // Crear la meta
                SaleGoal saleGoal = SaleGoal.builder()
                                .vendedor(vendedor)
                                .targetAmount(request.targetAmount())
                                .currentAmount(existingSales) // ✅ Inicia con ventas existentes o $0
                                .month(request.month())
                                .year(request.year())
                                .build();

                SaleGoal saved = saleGoalRepository.save(saleGoal);

                log.info("Meta creada para vendedor {} en {}/{} - Meta: ${}, Ventas actuales: ${}",
                                vendedor.getUsername(), request.month(), request.year(),
                                request.targetAmount(), existingSales);

                return saleGoalMapper.toResponse(saved);
        }

        // Calcular ventas del vendedor en un mes específico

        /**
         * ✅ PROBLEMA 1 - Calcular ventas completadas del vendedor en un mes específico
         */
        private BigDecimal calculateExistingSalesForMonth(UUID vendedorId, int month, int year) {
                try {
                        List<Order> orders;

                        // Check for shared user
                        User vendedor = userRepository.findById(vendedorId).orElse(null);
                        if (vendedor != null && UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
                                // Get all shared user IDs
                                List<String> sharedUsernames = UserUnificationUtil
                                                .getSharedUsernames(vendedor.getUsername());
                                List<UUID> sharedIds = userRepository.findAll().stream()
                                                .filter(u -> sharedUsernames.contains(u.getUsername()))
                                                .map(User::getId)
                                                .collect(Collectors.toList());

                                log.info("Calculando ventas para usuarios compartidos {}: IDs {}", sharedUsernames,
                                                sharedIds);

                                orders = orderRepository.findCompletedOrdersByVendedorIdsAndMonthYear(
                                                sharedIds, month, year);
                        } else {
                                // Standard single user calculation
                                orders = orderRepository.findCompletedOrdersByVendedorAndMonthYear(
                                                vendedorId, month, year);
                        }

                        if (orders.isEmpty()) {
                                log.debug("No hay ventas completadas para el vendedor {} en {}/{}",
                                                vendedorId, month, year);
                                return BigDecimal.ZERO;
                        }

                        // Sumar los totales usando discountedTotal si existe (igual que el Excel)
                        BigDecimal total = orders.stream()
                                        .map(o -> o.getDiscountedTotal() != null
                                                        ? o.getDiscountedTotal()
                                                        : o.getTotal())
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        log.debug("Ventas encontradas para vendedor {} en {}/{}: {} órdenes = ${}",
                                        vendedorId, month, year, orders.size(), total);

                        return total;

                } catch (Exception e) {
                        log.error("Error calculando ventas existentes para vendedor {} en {}/{}",
                                        vendedorId, month, year, e);
                        return BigDecimal.ZERO;
                }
        }

        @Override
        public SaleGoalResponse updateGoal(UUID id, UpdateSaleGoalRequest request) {
                SaleGoal saleGoal = saleGoalRepository.findById(id)
                                .orElseThrow(() -> new BusinessExeption("Meta no encontrada"));

                if (request.targetAmount() != null) {
                        saleGoal.setTargetAmount(request.targetAmount());
                }

                // Guardar cambio de meta
                saleGoalRepository.saveAndFlush(saleGoal);

                // Forzar recálculo del progreso basado en ventas reales
                // Esto corrige discrepancias si hubo facturas históricas creadas antes
                // o si la meta estaba desactualizada.
                recalculateGoalForVendorMonth(
                                saleGoal.getVendedor().getId(),
                                saleGoal.getMonth(),
                                saleGoal.getYear());

                // Recargar entidad actualizada para retornar respuesta correcta
                SaleGoal updated = saleGoalRepository.findById(id)
                                .orElseThrow(() -> new BusinessExeption("Meta no encontrada tras actualización"));

                log.info("Meta actualizada y recalculada: {}", id);

                return saleGoalMapper.toResponse(updated);
        }

        @Override
        public void deleteGoal(UUID id) {
                if (!saleGoalRepository.existsById(id)) {
                        throw new BusinessExeption("Meta no encontrada");
                }
                saleGoalRepository.deleteById(id);
                log.info("Meta eliminada: {}", id);
        }

        @Override
        public List<SaleGoalResponse> findAll() {
                return saleGoalRepository.findAllByOrderByYearDescMonthDesc()
                                .stream()
                                .peek(this::syncLive)
                                .map(saleGoalMapper::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<SaleGoalResponse> findByMonthAndYear(int month, int year) {
                return saleGoalRepository.findByMonthAndYear(month, year)
                                .stream()
                                .peek(this::syncLive)
                                .map(saleGoalMapper::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public SaleGoalResponse findById(UUID id) {
                SaleGoal saleGoal = saleGoalRepository.findById(id)
                                .orElseThrow(() -> new BusinessExeption("Meta no encontrada"));
                syncLive(saleGoal);
                return saleGoalMapper.toResponse(saleGoal);
        }

        @Override
        public List<VendedorWithGoalResponse> findAllVendedoresWithCurrentGoal() {
                LocalDate now = LocalDate.now();
                int currentMonth = now.getMonthValue();
                int currentYear = now.getYear();

                // Obtener todos los vendedores
                List<User> vendedores = userRepository.findByRole(Role.VENDEDOR);

                return vendedores.stream()
                                .map(vendedor -> {
                                        // Buscar meta del mes actual y recalcular en vivo
                                        SaleGoalResponse currentGoal = saleGoalRepository
                                                        .findByVendedorAndMonthAndYear(vendedor, currentMonth,
                                                                        currentYear)
                                                        .map(saleGoal -> {
                                                                syncLive(saleGoal);
                                                                return saleGoalMapper.toResponse(saleGoal);
                                                        })
                                                        .orElse(null);

                                        return new VendedorWithGoalResponse(
                                                        vendedor.getId(),
                                                        vendedor.getUsername(),
                                                        vendedor.getRole().name(),
                                                        vendedor.isActive(),
                                                        currentGoal);
                                })
                                .collect(Collectors.toList());
        }

        // =============================================
        // VENDEDOR - VER MIS METAS
        // =============================================

        @Override
        public SaleGoalResponse findMyCurrentGoal(String username) {
                User vendedor = userRepository.findByUsername(username)
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                LocalDate now = LocalDate.now();
                int currentMonth = now.getMonthValue();
                int currentYear = now.getYear();

                SaleGoal saleGoal = saleGoalRepository
                                .findByVendedorAndMonthAndYear(vendedor, currentMonth, currentYear)
                                .orElseThrow(() -> new BusinessExeption(
                                                "No tienes una meta asignada para este mes"));

                // Recalcular en vivo para garantizar consistencia con el Excel
                syncLive(saleGoal);

                return saleGoalMapper.toResponse(saleGoal);
        }

        @Override
        public List<SaleGoalResponse> findMyGoalHistory(String username) {
                User vendedor = userRepository.findByUsername(username)
                                .orElseThrow(() -> new BusinessExeption("Usuario no encontrado"));

                // Para el historial, recalcular en vivo cada meta antes de devolver
                return saleGoalRepository.findByVendedorOrderByYearDescMonthDesc(vendedor)
                                .stream()
                                .peek(this::syncLive)
                                .map(saleGoalMapper::toResponse)
                                .collect(Collectors.toList());
        }

        // =============================================
        // SISTEMA INTERNO - ACTUALIZAR PROGRESO
        // =============================================

        @Override
        public void updateGoalProgress(UUID vendedorId, BigDecimal saleAmount, int month, int year) {
                User vendedor = userRepository.findById(vendedorId)
                                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

                // Update the vendedor's own goal
                saleGoalRepository.findByVendedorAndMonthAndYear(vendedor, month, year)
                                .ifPresent(saleGoal -> {
                                        saleGoal.addSale(saleAmount);
                                        saleGoalRepository.save(saleGoal);
                                        log.info("Progreso de meta actualizado para {}: +${} (Total: ${}/{})",
                                                        vendedor.getUsername(), saleAmount,
                                                        saleGoal.getCurrentAmount(), saleGoal.getTargetAmount());
                                });

                // If this is a shared user (NinaTorres/YicelaSandoval), sync progress to the
                // other user's goal
                if (UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
                        List<UUID> sharedUserIds = UserUnificationUtil.getSharedUserIds(
                                        vendedorId,
                                        vendedor.getUsername(),
                                        userRepository.findByUsername(
                                                        vendedor.getUsername().equals(UserUnificationUtil.NINA_TORRES)
                                                                        ? UserUnificationUtil.YICELA_SANDOVAL
                                                                        : UserUnificationUtil.NINA_TORRES)
                                                        .map(User::getId)
                                                        .orElse(null));

                        // Update the other shared user's goal as well
                        for (UUID sharedUserId : sharedUserIds) {
                                if (!sharedUserId.equals(vendedorId)) {
                                        userRepository.findById(sharedUserId).ifPresent(otherVendedor -> {
                                                saleGoalRepository
                                                                .findByVendedorAndMonthAndYear(otherVendedor, month,
                                                                                year)
                                                                .ifPresent(otherGoal -> {
                                                                        otherGoal.addSale(saleAmount);
                                                                        saleGoalRepository.save(otherGoal);
                                                                        log.info("[Unificación] Progreso sincronizado a {}: +${} (Total: ${}/{})",
                                                                                        otherVendedor.getUsername(),
                                                                                        saleAmount,
                                                                                        otherGoal.getCurrentAmount(),
                                                                                        otherGoal.getTargetAmount());
                                                                });
                                        });
                                }
                        }
                }
        }

        @Override
        public void recalculateGoalForVendorMonth(UUID vendedorId, int month, int year) {
                User vendedor = userRepository.findById(vendedorId)
                                .orElseThrow(() -> new BusinessExeption("Vendedor no encontrado"));

                // Buscar la meta para este vendedor/mes/año
                saleGoalRepository.findByVendedorAndMonthAndYear(vendedor, month, year)
                                .ifPresent(saleGoal -> {
                                        // Recalcular desde cero
                                        BigDecimal actualSales = calculateExistingSalesForMonth(
                                                        vendedorId, month, year);

                                        saleGoal.setCurrentAmount(actualSales);
                                        saleGoalRepository.save(saleGoal);

                                        log.info("Meta recalculada para {} en {}/{}: ${}/{}",
                                                        vendedor.getUsername(), month, year,
                                                        actualSales, saleGoal.getTargetAmount());
                                });

                // If this is a shared user, also recalculate for the other shared user
                if (UserUnificationUtil.isSharedUser(vendedor.getUsername())) {
                        List<UUID> sharedUserIds = UserUnificationUtil.getSharedUserIds(
                                        vendedorId,
                                        vendedor.getUsername(),
                                        userRepository.findByUsername(
                                                        vendedor.getUsername().equals(UserUnificationUtil.NINA_TORRES)
                                                                        ? UserUnificationUtil.YICELA_SANDOVAL
                                                                        : UserUnificationUtil.NINA_TORRES)
                                                        .map(User::getId)
                                                        .orElse(null));

                        for (UUID sharedUserId : sharedUserIds) {
                                if (!sharedUserId.equals(vendedorId)) {
                                        userRepository.findById(sharedUserId).ifPresent(otherVendedor -> {
                                                saleGoalRepository
                                                                .findByVendedorAndMonthAndYear(otherVendedor, month,
                                                                                year)
                                                                .ifPresent(otherGoal -> {
                                                                        BigDecimal actualSales = calculateExistingSalesForMonth(
                                                                                        sharedUserId, month, year);

                                                                        otherGoal.setCurrentAmount(actualSales);
                                                                        saleGoalRepository.save(otherGoal);

                                                                        log.info("[Unificación] Meta recalculada para {} en {}/{}: ${}/{}",
                                                                                        otherVendedor.getUsername(),
                                                                                        month, year,
                                                                                        actualSales,
                                                                                        otherGoal.getTargetAmount());
                                                                });
                                        });
                                }
                        }
                }
        }

        // =============================================
        // UTILIDADES
        // =============================================

        /**
         * Recalcula el currentAmount de una meta en vivo y lo persiste si cambió.
         * Siempre normaliza la escala a 2 decimales para serialización consistente.
         */
        private void syncLive(SaleGoal saleGoal) {
                BigDecimal liveAmount = calculateExistingSalesForMonth(
                                saleGoal.getVendedor().getId(),
                                saleGoal.getMonth(),
                                saleGoal.getYear())
                                .setScale(2, java.math.RoundingMode.HALF_UP);

                BigDecimal stored = saleGoal.getCurrentAmount()
                                .setScale(2, java.math.RoundingMode.HALF_UP);

                if (liveAmount.compareTo(stored) != 0) {
                        log.info("[LiveSync] {} {}/{}: ${} → ${}",
                                        saleGoal.getVendedor().getUsername(),
                                        saleGoal.getMonth(), saleGoal.getYear(),
                                        stored, liveAmount);
                        saleGoal.setCurrentAmount(liveAmount);
                        saleGoalRepository.save(saleGoal);
                } else {
                        // Solo normalizar escala en memoria para serialización consistente (sin persistir)
                        saleGoal.setCurrentAmount(stored);
                }
        }

        /**
         * Utilidad para obtener nombre del mes en español
         */
        private String getMonthName(int month) {
                String[] months = {
                                "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
                };

                if (month < 1 || month > 12) {
                        return "Mes inválido";
                }

                return months[month];
        }

}
