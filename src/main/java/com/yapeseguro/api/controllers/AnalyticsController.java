package com.yapeseguro.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    /**
     * GET /analytics/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Void> getSummary(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "2024-01") String yearMonth
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /analytics/spending
     */
    @GetMapping("/spending")
    public ResponseEntity<Void> getSpendingAnalytics(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "2024-01") String yearMonth
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * GET /analytics/security
     */
    @GetMapping("/security")
    public ResponseEntity<Void> getSecurityAnalytics(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok().build();
    }
}