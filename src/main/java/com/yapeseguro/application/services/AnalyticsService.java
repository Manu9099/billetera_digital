package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.CreateExpenseCategoryRequest;
import com.yapeseguro.api.dto.response.ExpenseCategoryResponse;
import com.yapeseguro.api.dto.response.MonthlyAnalyticsSummaryResponse;
import com.yapeseguro.api.dto.response.SecurityAnalyticsResponse;
import com.yapeseguro.api.dto.response.SpendingCategoryRankingResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.ExpenseAnalyticsEntity;
import com.yapeseguro.infrastructure.persistence.entities.ExpenseCategoryEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.ExpenseAnalyticsRepository;
import com.yapeseguro.infrastructure.persistence.repositories.ExpenseCategoryRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseAnalyticsRepository expenseAnalyticsRepository;

    @Transactional
    public MonthlyAnalyticsSummaryResponse refreshMonthlyAnalytics(
            String yearMonth,
            String username
    ) {
        UserEntity user = getUserByUsername(username);
        WalletEntity wallet = getPersonalWallet(user);
        YearMonth parsedYearMonth = parseYearMonth(yearMonth);

        ensureDefaultCategories(user);

        OffsetDateTime start = parsedYearMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime end = parsedYearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());

        List<TransactionEntity> transactions = transactionRepository.findOutgoingSpendingTransactions(
                wallet,
                start,
                end,
                List.of(
                        TransactionEntity.TxStatus.COMPLETED,
                        TransactionEntity.TxStatus.RELEASED,
                        TransactionEntity.TxStatus.HELD
                ),
                List.of(
                        TransactionEntity.TxType.P2P,
                        TransactionEntity.TxType.QR_PAYMENT,
                        TransactionEntity.TxType.SCHEDULED,
                        TransactionEntity.TxType.MARKETPLACE
                )
        );

        Map<ExpenseCategoryEntity, CategoryAccumulator> totals = new LinkedHashMap<>();

        for (TransactionEntity transaction : transactions) {
            ExpenseCategoryEntity category = resolveCategory(user, transaction);

            totals.computeIfAbsent(category, ignored -> new CategoryAccumulator())
                    .add(transaction.getAmount());
        }

        expenseAnalyticsRepository.deleteByWalletAndYearMonth(
                wallet,
                parsedYearMonth.toString()
        );

        for (Map.Entry<ExpenseCategoryEntity, CategoryAccumulator> entry : totals.entrySet()) {
            CategoryAccumulator accumulator = entry.getValue();

            expenseAnalyticsRepository.save(
                    ExpenseAnalyticsEntity.builder()
                            .wallet(wallet)
                            .category(entry.getKey())
                            .yearMonth(parsedYearMonth.toString())
                            .totalSpent(accumulator.total())
                            .transactionCount(accumulator.count())
                            .build()
            );
        }

        return getMonthlySummary(parsedYearMonth.toString(), username);
    }

    @Transactional(readOnly = true)
    public MonthlyAnalyticsSummaryResponse getMonthlySummary(
            String yearMonth,
            String username
    ) {
        UserEntity user = getUserByUsername(username);
        WalletEntity wallet = getPersonalWallet(user);
        YearMonth parsedYearMonth = parseYearMonth(yearMonth);

        List<ExpenseAnalyticsEntity> analytics = expenseAnalyticsRepository
                .findByWalletAndYearMonthOrderByTotalSpentDesc(wallet, parsedYearMonth.toString());

        List<SpendingCategoryRankingResponse> ranking = buildRanking(analytics);

        BigDecimal totalSpent = analytics.stream()
                .map(ExpenseAnalyticsEntity::getTotalSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int transactionCount = analytics.stream()
                .map(ExpenseAnalyticsEntity::getTransactionCount)
                .reduce(0, Integer::sum);

        SpendingCategoryRankingResponse topCategory = ranking.isEmpty() ? null : ranking.get(0);

        return MonthlyAnalyticsSummaryResponse.builder()
                .walletId(wallet.getId())
                .walletType(wallet.getWalletType().name())
                .yearMonth(parsedYearMonth.toString())
                .totalSpent(totalSpent)
                .transactionCount(transactionCount)
                .topCategoryName(topCategory != null ? topCategory.getCategoryName() : null)
                .topCategoryAmount(topCategory != null ? topCategory.getTotalSpent() : BigDecimal.ZERO)
                .ranking(ranking)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SpendingCategoryRankingResponse> getSpendingRanking(
            String yearMonth,
            String username
    ) {
        UserEntity user = getUserByUsername(username);
        WalletEntity wallet = getPersonalWallet(user);
        YearMonth parsedYearMonth = parseYearMonth(yearMonth);

        List<ExpenseAnalyticsEntity> analytics = expenseAnalyticsRepository
                .findByWalletAndYearMonthOrderByTotalSpentDesc(wallet, parsedYearMonth.toString());

        return buildRanking(analytics);
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> getMyCategories(String username) {
        UserEntity user = getUserByUsername(username);

        return expenseCategoryRepository
                .findByUserOrderByCategoryNameAsc(user)
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(
            CreateExpenseCategoryRequest request,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        String categoryName = normalizeRequired(
                request.getCategoryName(),
                "El nombre de la categoría es obligatorio"
        );

        if (expenseCategoryRepository.existsByUserAndCategoryNameIgnoreCase(user, categoryName)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre");
        }

        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder()
                .user(user)
                .categoryName(categoryName)
                .iconCode(normalize(request.getIconCode()))
                .colorHex(normalize(request.getColorHex()))
                .build();

        return toCategoryResponse(expenseCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public SecurityAnalyticsResponse getSecurityAnalytics(String username) {
        UserEntity user = getUserByUsername(username);
        WalletEntity wallet = getPersonalWallet(user);

        YearMonth currentMonth = YearMonth.now();
        OffsetDateTime start = currentMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime end = currentMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());

        long totalTransactions = transactionRepository.countWalletTransactionsBetween(wallet, start, end);
        long disputedTransactions = transactionRepository.countWalletDisputedTransactionsBetween(wallet, start, end);

        String riskLevel = resolveRiskLevel(totalTransactions, disputedTransactions);

        return SecurityAnalyticsResponse.builder()
                .yearMonth(currentMonth.toString())
                .totalTransactions(totalTransactions)
                .disputedTransactions(disputedTransactions)
                .riskLevel(riskLevel)
                .message(resolveSecurityMessage(riskLevel))
                .build();
    }

    private List<SpendingCategoryRankingResponse> buildRanking(List<ExpenseAnalyticsEntity> analytics) {
        BigDecimal totalSpent = analytics.stream()
                .map(ExpenseAnalyticsEntity::getTotalSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ExpenseAnalyticsEntity> sortedAnalytics = analytics.stream()
                .sorted(Comparator.comparing(ExpenseAnalyticsEntity::getTotalSpent).reversed())
                .toList();

        int[] rank = {1};

        return sortedAnalytics.stream()
                .map(item -> {
                    ExpenseCategoryEntity category = item.getCategory();

                    return SpendingCategoryRankingResponse.builder()
                            .rank(rank[0]++)
                            .categoryId(category != null ? category.getId() : null)
                            .categoryName(category != null ? category.getCategoryName() : "Sin categoría")
                            .iconCode(category != null ? category.getIconCode() : "circle")
                            .colorHex(category != null ? category.getColorHex() : "#6B7280")
                            .totalSpent(item.getTotalSpent())
                            .transactionCount(item.getTransactionCount())
                            .percentage(calculatePercentage(item.getTotalSpent(), totalSpent))
                            .build();
                })
                .toList();
    }

    private ExpenseCategoryEntity resolveCategory(
            UserEntity user,
            TransactionEntity transaction
    ) {
        String inferredCategoryName = inferCategoryName(transaction);

        return getOrCreateCategory(
                user,
                inferredCategoryName,
                iconForCategory(inferredCategoryName),
                colorForCategory(inferredCategoryName)
        );
    }

    private String inferCategoryName(TransactionEntity transaction) {
        if (transaction.getType() == TransactionEntity.TxType.SCHEDULED) {
            return "Pagos programados";
        }

        if (transaction.getType() == TransactionEntity.TxType.MARKETPLACE) {
            return "Marketplace";
        }

        if (transaction.getType() == TransactionEntity.TxType.P2P) {
            return "Transferencias";
        }

        if (transaction.getType() == TransactionEntity.TxType.QR_PAYMENT) {
            return inferQrPaymentCategory(transaction);
        }

        return inferByText(transaction);
    }

    private String inferQrPaymentCategory(TransactionEntity transaction) {
        return businessProfileRepository
                .findByBusinessWalletAndActiveTrue(transaction.getWalletTo())
                .map(BusinessProfileEntity::getBusinessCategory)
                .map(this::normalize)
                .filter(category -> category != null && !category.isBlank())
                .orElseGet(() -> inferByText(transaction));
    }

    private String inferByText(TransactionEntity transaction) {
        String text = (
                nullToEmpty(transaction.getConcept())
                        + " "
                        + nullToEmpty(transaction.getDescription())
                        + " "
                        + nullToEmpty(transaction.getNotes())
        ).toLowerCase();

        if (containsAny(text, "cafe", "comida", "restaurante", "bodega", "almuerzo", "cena", "desayuno")) {
            return "Comida";
        }

        if (containsAny(text, "taxi", "bus", "metro", "uber", "cabify", "transporte", "pasaje")) {
            return "Transporte";
        }

        if (containsAny(text, "consulta", "dental", "doctor", "medicina", "salud", "clinica", "farmacia")) {
            return "Salud";
        }

        if (containsAny(text, "ropa", "zapatilla", "tienda", "shopping")) {
            return "Compras";
        }

        if (containsAny(text, "luz", "agua", "internet", "telefono", "servicio")) {
            return "Servicios";
        }

        return "Otros";
    }

    private ExpenseCategoryEntity getOrCreateCategory(
            UserEntity user,
            String categoryName,
            String iconCode,
            String colorHex
    ) {
        return expenseCategoryRepository
                .findByUserAndCategoryNameIgnoreCase(user, categoryName)
                .orElseGet(() -> expenseCategoryRepository.save(
                        ExpenseCategoryEntity.builder()
                                .user(user)
                                .categoryName(categoryName)
                                .iconCode(iconCode)
                                .colorHex(colorHex)
                                .build()
                ));
    }

    private void ensureDefaultCategories(UserEntity user) {
        getOrCreateCategory(user, "Comida", "utensils", "#F97316");
        getOrCreateCategory(user, "Transporte", "bus", "#3B82F6");
        getOrCreateCategory(user, "Salud", "heart-pulse", "#EF4444");
        getOrCreateCategory(user, "Compras", "shopping-bag", "#A855F7");
        getOrCreateCategory(user, "Servicios", "receipt", "#14B8A6");
        getOrCreateCategory(user, "Transferencias", "send", "#64748B");
        getOrCreateCategory(user, "Pagos programados", "calendar-clock", "#22C55E");
        getOrCreateCategory(user, "Marketplace", "shield-check", "#EAB308");
        getOrCreateCategory(user, "Otros", "circle", "#6B7280");
    }

    private ExpenseCategoryResponse toCategoryResponse(ExpenseCategoryEntity category) {
        return ExpenseCategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .iconCode(category.getIconCode())
                .colorHex(category.getColorHex())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private WalletEntity getPersonalWallet(UserEntity user) {
        return walletRepository
                .findByUserAndWalletType(user, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("No tienes billetera personal"));
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private YearMonth parseYearMonth(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("El formato yearMonth debe ser YYYY-MM");
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private BigDecimal calculatePercentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return value
                .multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    private String resolveRiskLevel(long totalTransactions, long disputedTransactions) {
        if (totalTransactions == 0) {
            return "LOW";
        }

        BigDecimal disputeRate = BigDecimal.valueOf(disputedTransactions)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP);

        if (disputeRate.compareTo(BigDecimal.valueOf(10)) >= 0) {
            return "HIGH";
        }

        if (disputeRate.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private String resolveSecurityMessage(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "Tienes varias operaciones disputadas este mes. Revisa tus pagos y vendedores frecuentes.";
            case "MEDIUM" -> "Tu actividad tiene algunas disputas. Mantén evidencias de pagos marketplace.";
            default -> "Tu actividad del mes se ve estable.";
        };
    }

    private String iconForCategory(String categoryName) {
        return switch (categoryName.toLowerCase()) {
            case "comida" -> "utensils";
            case "transporte" -> "bus";
            case "salud" -> "heart-pulse";
            case "compras" -> "shopping-bag";
            case "servicios" -> "receipt";
            case "transferencias" -> "send";
            case "pagos programados" -> "calendar-clock";
            case "marketplace" -> "shield-check";
            default -> "circle";
        };
    }

    private String colorForCategory(String categoryName) {
        return switch (categoryName.toLowerCase()) {
            case "comida" -> "#F97316";
            case "transporte" -> "#3B82F6";
            case "salud" -> "#EF4444";
            case "compras" -> "#A855F7";
            case "servicios" -> "#14B8A6";
            case "transferencias" -> "#64748B";
            case "pagos programados" -> "#22C55E";
            case "marketplace" -> "#EAB308";
            default -> "#6B7280";
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);

        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }

        return normalized;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static final class CategoryAccumulator {
        private BigDecimal total = BigDecimal.ZERO;
        private int count = 0;

        void add(BigDecimal amount) {
            total = total.add(amount);
            count++;
        }

        BigDecimal total() {
            return total;
        }

        int count() {
            return count;
        }
    }
}