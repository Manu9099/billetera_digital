package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.AddDisputeTimelineNoteRequest;
import com.yapeseguro.api.dto.response.DisputeTimelineEventResponse;
import com.yapeseguro.application.services.DisputeTimelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeTimelineController {

    private final DisputeTimelineService timelineService;

    /**
     * GET /disputes/{disputeId}/timeline
     */
    @GetMapping("/{disputeId}/timeline")
    public ResponseEntity<List<DisputeTimelineEventResponse>> getTimeline(
            @PathVariable UUID disputeId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                timelineService.getTimeline(
                        disputeId,
                        user.getUsername()
                )
        );
    }

    /**
     * POST /disputes/{disputeId}/timeline/note
     */
    @PostMapping("/{disputeId}/timeline/note")
    public ResponseEntity<DisputeTimelineEventResponse> addNote(
            @PathVariable UUID disputeId,
            @Valid @RequestBody AddDisputeTimelineNoteRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        timelineService.addNote(
                                disputeId,
                                request,
                                user.getUsername()
                        )
                );
    }
}