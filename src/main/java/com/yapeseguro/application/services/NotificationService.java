package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.response.NotificationBulkActionResponse;
import com.yapeseguro.api.dto.response.NotificationResponse;
import com.yapeseguro.api.dto.response.NotificationUnreadCountResponse;
import com.yapeseguro.api.dto.response.PageResponse;
import com.yapeseguro.infrastructure.persistence.entities.NotificationEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.repositories.NotificationRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(
            String username,
            boolean onlyUnread,
            Integer page,
            Integer size
    ) {
        UserEntity user = getUserByUsername(username);

        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        Page<NotificationEntity> notifications = notificationRepository.findVisibleForUser(
                user,
                onlyUnread,
                OffsetDateTime.now(),
                PageRequest.of(safePage, safeSize)
        );

        return PageResponse.<NotificationResponse>builder()
                .content(
                        notifications.getContent()
                                .stream()
                                .map(this::toResponse)
                                .toList()
                )
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .first(notifications.isFirst())
                .last(notifications.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(String username) {
        UserEntity user = getUserByUsername(username);

        long unreadCount = notificationRepository.countUnreadVisibleForUser(
                user,
                OffsetDateTime.now()
        );

        return NotificationUnreadCountResponse.builder()
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional
    public NotificationResponse markAsRead(
            UUID notificationId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        NotificationEntity notification = notificationRepository.findByIdAndUser(
                        notificationId,
                        user
                )
                .orElseThrow(() -> new IllegalArgumentException("Notificación no encontrada"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(OffsetDateTime.now());
        }

        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationBulkActionResponse markAllAsRead(String username) {
        UserEntity user = getUserByUsername(username);

        OffsetDateTime now = OffsetDateTime.now();

        int affected = notificationRepository.markAllVisibleAsRead(
                user,
                now,
                now
        );

        return NotificationBulkActionResponse.builder()
                .affected(affected)
                .message("Notificaciones marcadas como leídas")
                .build();
    }

    @Transactional
    public void deleteNotification(
            UUID notificationId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        int deleted = notificationRepository.deleteOwnedNotification(
                notificationId,
                user
        );

        if (deleted == 0) {
            throw new IllegalArgumentException("Notificación no encontrada");
        }
    }

    @Transactional
    public NotificationResponse createInAppNotification(
            UserEntity user,
            String title,
            String message,
            NotificationEntity.NotificationType notificationType,
            UUID relatedEntityId,
            OffsetDateTime expiresAt
    ) {
        NotificationEntity notification = NotificationEntity.builder()
                .user(user)
                .title(normalize(title))
                .message(normalizeRequired(message, "El mensaje de la notificación es obligatorio"))
                .notificationType(notificationType)
                .relatedEntityId(relatedEntityId)
                .read(false)
                .sentVia(NotificationEntity.SentVia.IN_APP)
                .expiresAt(expiresAt)
                .build();

        return toResponse(notificationRepository.save(notification));
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        OffsetDateTime now = OffsetDateTime.now();

        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .notificationType(notification.getNotificationType().name())
                .relatedEntityId(notification.getRelatedEntityId())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .sentVia(notification.getSentVia() != null ? notification.getSentVia().name() : null)
                .createdAt(notification.getCreatedAt())
                .expiresAt(notification.getExpiresAt())
                .expired(
                        notification.getExpiresAt() != null
                                && !notification.getExpiresAt().isAfter(now)
                )
                .build();
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }

        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
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
}