package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateExpenseCategoryRequest;
import com.yapeseguro.api.dto.response.ExpenseCategoryResponse;
import com.yapeseguro.api.dto.response.MonthlyAnalyticsSummaryResponse;
import com.yapeseguro.api.dto.response.SecurityAnalyticsResponse;
import com.yapeseguro.api.dto.response.SpendingCategoryRankingResponse;
import com.yapeseguro.application.services.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /analytics/summary?yearMonth=2026-05
     * Devuelve resumen mensual usando snapshots ya generados.
     */
    @GetMapping("/summary")
    public ResponseEntity<MonthlyAnalyticsSummaryResponse> getSummary(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String yearMonth
    ) {
        return ResponseEntity.ok(
                analyticsService.getMonthlySummary(
                        resolveYearMonth(yearMonth),
                        user.getUsername()
                )
        );
    }

    /**
     * GET /analytics/spending?yearMonth=2026-05
     * Devuelve ranking mensual por categoría.
     */
    @GetMapping("/spending")
    public ResponseEntity<List<SpendingCategoryRankingResponse>> getSpendingAnalytics(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String yearMonth
    ) {
        return ResponseEntity.ok(
                analyticsService.getSpendingRanking(
                        resolveYearMonth(yearMonth),
                        user.getUsername()
                )
        );
    }

    /**
     * POST /analytics/refresh?yearMonth=2026-05
     * Recalcula snapshots mensuales desde transactions.
     */
    @PostMapping("/refresh")
    public ResponseEntity<MonthlyAnalyticsSummaryResponse> refreshAnalytics(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String yearMonth
    ) {
        return ResponseEntity.ok(
                analyticsService.refreshMonthlyAnalytics(
                        resolveYearMonth(yearMonth),
                        user.getUsername()
                )
        );
    }

    /**
     * GET /analytics/categories
     * Lista categorías del usuario.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<ExpenseCategoryResponse>> getCategories(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                analyticsService.getMyCategories(user.getUsername())
        );
    }

    /**
     * POST /analytics/categories
     * Crea categoría personalizada.
     */
    @PostMapping("/categories")
    public ResponseEntity<ExpenseCategoryResponse> createCategory(
            @Valid @RequestBody CreateExpenseCategoryRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(analyticsService.createCategory(
                        request,
                        user.getUsername()
                ));
    }

    /**
     * GET /analytics/security
     * Métrica simple de riesgo por disputas del mes actual.
     */
    @GetMapping("/security")
    public ResponseEntity<SecurityAnalyticsResponse> getSecurityAnalytics(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                analyticsService.getSecurityAnalytics(user.getUsername())
        );
    }

    private String resolveYearMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            return YearMonth.now().toString();
        }

        return yearMonth;
    }
}