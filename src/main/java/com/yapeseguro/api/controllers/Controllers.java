package com.yapeseguro.api.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;





// ============================================================
// DisputeController — /disputes  (Feature #2)
// ============================================================
@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
class DisputeController {

    /** POST /disputes — abrir reclamo */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> openDispute(
            @Valid @RequestBody OpenDisputeRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /disputes — mis disputas */
    @GetMapping
    public ResponseEntity<?> getMyDisputes(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** GET /disputes/{id} — detalle del reclamo */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDispute(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    /** POST /disputes/{id}/evidence — subir evidencia */
    @PostMapping("/{id}/evidence")
    public ResponseEntity<?> addEvidence(
            @PathVariable UUID id,
            @Valid @RequestBody AddEvidenceRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    @Data public static class OpenDisputeRequest {
        @NotNull  private UUID transactionId;
        @NotBlank private String reason;
        @NotBlank @Size(min = 20, max = 2000) private String description;
        @NotNull  @DecimalMin("0.01") private BigDecimal disputedAmount;
                  private String recipientPhone;
                  private String qrPhotoUrl;
                  private String chatTranscript;
    }

    @Data public static class AddEvidenceRequest {
        @NotBlank private String type;          // TEXT, IMAGE, AUDIO, VIDEO
        @NotBlank private String contentUrl;
                  private String description;
    }
}

// ============================================================
// BusinessController — /business  (Features #3, #4, #5)
// ============================================================
@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
class BusinessController {

    /** POST /business/profile — crear perfil de negocio */
    @PostMapping("/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createBusinessProfile(
            @Valid @RequestBody CreateBusinessProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /business/profile — mi perfil de negocio */
    @GetMapping("/profile")
    public ResponseEntity<?> getMyBusinessProfile(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** GET /business/inventory — Feature #5: inventario */
    @GetMapping("/inventory")
    public ResponseEntity<?> getInventory(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** POST /business/inventory — Feature #5: agregar producto */
    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> addInventoryItem(
            @Valid @RequestBody AddInventoryItemRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /business/analytics — Feature #4 y #10: analytics del negocio */
    @GetMapping("/analytics")
    public ResponseEntity<?> getBusinessAnalytics(
            @RequestParam(defaultValue = "2024-01") String yearMonth,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    @Data public static class CreateBusinessProfileRequest {
        @NotBlank @Size(max = 255) private String businessName;
        @NotBlank @Pattern(regexp = "^\\d{11}$", message = "RUC must be 11 digits") private String ruc;
        @NotBlank private String businessCategory;
        private String description;
        private String address;
        private Double latitude;
        private Double longitude;
        private String district;
        @Pattern(regexp = "^9\\d{8}$") private String businessPhoneNumber;
        @Email private String businessEmail;
    }

    @Data public static class AddInventoryItemRequest {
        @NotBlank @Size(max = 255) private String productName;
        private String description;
        private String productCategory;
        private String sku;
        @NotNull @DecimalMin("0.01") private BigDecimal price;
        @NotNull @Min(0)             private Integer currentStock;
        @Min(0)                      private Integer lowStockThreshold = 5;
        private boolean qrEnabled = false;
    }
}

// ============================================================
// GroupController — /groups  (Feature #8)
// ============================================================
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
class GroupController {

    /** POST /groups */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /groups — mis grupos */
    @GetMapping
    public ResponseEntity<?> getMyGroups(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** POST /groups/{id}/pay — pagar al grupo */
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payGroup(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    @Data public static class CreateGroupRequest {
        @NotBlank @Size(max = 255)     private String groupName;
        private String description;
        @NotBlank                      private String groupType;
        @NotNull @DecimalMin("0.01")   private BigDecimal totalAmount;
        private BigDecimal perPersonAmount;
        private OffsetDateTime targetDate;
    }
}

// ============================================================
// ScheduledPaymentController — /scheduled-payments  (Feature #7)
// ============================================================
@RestController
@RequestMapping("/scheduled-payments")
@RequiredArgsConstructor
class ScheduledPaymentController {

    /** POST /scheduled-payments */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateScheduledPaymentRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /scheduled-payments */
    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** PATCH /scheduled-payments/{id}/pause */
    @PatchMapping("/{id}/pause")
    public ResponseEntity<?> pause(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    /** DELETE /scheduled-payments/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(@PathVariable UUID id) {
        return ResponseEntity.noContent().build();
    }

    @Data public static class CreateScheduledPaymentRequest {
        @NotBlank @Size(max = 255)   private String recipientName;
        @Pattern(regexp = "^9\\d{8}$") private String recipientPhone;
        private UUID recipientUserId;
        @NotNull @DecimalMin("0.01") private BigDecimal amount;
        @NotBlank @Size(max = 100)   private String concept;
        @NotBlank                    private String frequency;     // DAILY, WEEKLY, BIWEEKLY, MONTHLY
        @Min(1) @Max(28)             private Integer dayOfMonth;
        @Min(1) @Max(7)              private Integer dayOfWeek;
        @NotNull                     private OffsetDateTime nextPaymentDate;
        private OffsetDateTime endDate;
        private boolean autoPayEnabled = false;
        @Min(1) @Max(30)             private int notifyDaysInAdvance = 1;
    }
}

// ============================================================
// QrController — /qr  (Feature #9)
// ============================================================
@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
class QrController {

    /** POST /qr — crear QR con monto fijo */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createFixedQR(
            @Valid @RequestBody CreateQrRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /qr/{id} — obtener detalles del QR para mostrar antes de pagar */
    @GetMapping("/{id}")
    public ResponseEntity<?> getQrDetails(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }

    /** POST /qr/{id}/pay — pagar QR escaneado */
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payViaQR(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    @Data public static class CreateQrRequest {
        @NotBlank @Size(max = 255)   private String description;
        @DecimalMin("0.01")          private BigDecimal fixedAmount;
        @NotBlank                    private String qrType;   // PAYMENT, FIXED_AMOUNT, INVENTORY
    }
}

// ============================================================
// AnalyticsController — /analytics  (Feature #10)
// ============================================================
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
class AnalyticsController {

    /** GET /analytics/expenses — ranking de gastos del mes */
    @GetMapping("/expenses")
    public ResponseEntity<?> getExpenses(
            @RequestParam(defaultValue = "") String yearMonth,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }

    /** GET /analytics/expenses/summary */
    @GetMapping("/expenses/summary")
    public ResponseEntity<?> getSummary(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok().build();
    }
}
