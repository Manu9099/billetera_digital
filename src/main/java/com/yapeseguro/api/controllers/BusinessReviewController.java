package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.CreateBusinessReviewRequest;
import com.yapeseguro.api.dto.request.UpdateBusinessReviewRequest;
import com.yapeseguro.api.dto.response.BusinessReviewResponse;
import com.yapeseguro.api.dto.response.BusinessReviewSummaryResponse;
import com.yapeseguro.api.dto.response.PageResponse;
import com.yapeseguro.application.services.BusinessReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BusinessReviewController {

    private final BusinessReviewService businessReviewService;

    /**
     * POST /businesses/{businessProfileId}/reviews
     */
    @PostMapping("/businesses/{businessProfileId}/reviews")
    public ResponseEntity<BusinessReviewResponse> createReview(
            @PathVariable UUID businessProfileId,
            @Valid @RequestBody CreateBusinessReviewRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        businessReviewService.createReview(
                                businessProfileId,
                                request,
                                user.getUsername()
                        )
                );
    }

    /**
     * GET /businesses/{businessProfileId}/reviews?page=0&size=20
     */
    @GetMapping("/businesses/{businessProfileId}/reviews")
    public ResponseEntity<PageResponse<BusinessReviewResponse>> getBusinessReviews(
            @PathVariable UUID businessProfileId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(
                businessReviewService.getBusinessReviews(
                        businessProfileId,
                        page,
                        size
                )
        );
    }

    /**
     * GET /businesses/{businessProfileId}/reviews/summary
     */
    @GetMapping("/businesses/{businessProfileId}/reviews/summary")
    public ResponseEntity<BusinessReviewSummaryResponse> getBusinessReviewSummary(
            @PathVariable UUID businessProfileId
    ) {
        return ResponseEntity.ok(
                businessReviewService.getBusinessReviewSummary(businessProfileId)
        );
    }

    /**
     * GET /reviews/transactions/{transactionId}
     */
    @GetMapping("/reviews/transactions/{transactionId}")
    public ResponseEntity<BusinessReviewResponse> getMyReviewByTransaction(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                businessReviewService.getMyReviewByTransaction(
                        transactionId,
                        user.getUsername()
                )
        );
    }

    /**
     * PUT /reviews/{reviewId}
     */
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<BusinessReviewResponse> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateBusinessReviewRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                businessReviewService.updateReview(
                        reviewId,
                        request,
                        user.getUsername()
                )
        );
    }

    /**
     * DELETE /reviews/{reviewId}
     */
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal UserDetails user
    ) {
        businessReviewService.deleteReview(
                reviewId,
                user.getUsername()
        );

        return ResponseEntity.noContent().build();
    }
}
