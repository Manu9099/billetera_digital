package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.response.NotificationBulkActionResponse;
import com.yapeseguro.api.dto.response.NotificationResponse;
import com.yapeseguro.api.dto.response.NotificationUnreadCountResponse;
import com.yapeseguro.api.dto.response.PageResponse;
import com.yapeseguro.application.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /notifications/me
     * GET /notifications/me?onlyUnread=true&page=0&size=20
     */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "false") boolean onlyUnread,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(
                notificationService.getMyNotifications(
                        user.getUsername(),
                        onlyUnread,
                        page,
                        size
                )
        );
    }

    /**
     * GET /notifications/unread-count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                notificationService.getUnreadCount(user.getUsername())
        );
    }

    /**
     * PATCH /notifications/{id}/read
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                notificationService.markAsRead(
                        id,
                        user.getUsername()
                )
        );
    }

    /**
     * PATCH /notifications/read-all
     */
    @PatchMapping("/read-all")
    public ResponseEntity<NotificationBulkActionResponse> markAllAsRead(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                notificationService.markAllAsRead(user.getUsername())
        );
    }

    /**
     * DELETE /notifications/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user
    ) {
        notificationService.deleteNotification(
                id,
                user.getUsername()
        );

        return ResponseEntity.noContent().build();
    }
}