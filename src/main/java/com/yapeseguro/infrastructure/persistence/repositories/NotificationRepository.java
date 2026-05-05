package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.NotificationEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    boolean existsByUserAndNotificationTypeAndRelatedEntityId(
            UserEntity user,
            NotificationEntity.NotificationType notificationType,
            UUID relatedEntityId
    );

    @Query("""
            select n
            from NotificationEntity n
            where n.user = :user
              and (:onlyUnread = false or n.read = false)
              and (n.expiresAt is null or n.expiresAt > :now)
            order by n.createdAt desc
            """)
    Page<NotificationEntity> findVisibleForUser(
            @Param("user") UserEntity user,
            @Param("onlyUnread") boolean onlyUnread,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Query("""
            select n
            from NotificationEntity n
            where n.id = :notificationId
              and n.user = :user
            """)
    Optional<NotificationEntity> findByIdAndUser(
            @Param("notificationId") UUID notificationId,
            @Param("user") UserEntity user
    );

    @Query("""
            select count(n)
            from NotificationEntity n
            where n.user = :user
              and n.read = false
              and (n.expiresAt is null or n.expiresAt > :now)
            """)
    long countUnreadVisibleForUser(
            @Param("user") UserEntity user,
            @Param("now") OffsetDateTime now
    );

    @Modifying
    @Query("""
            update NotificationEntity n
            set n.read = true,
                n.readAt = :readAt
            where n.user = :user
              and n.read = false
              and (n.expiresAt is null or n.expiresAt > :now)
            """)
    int markAllVisibleAsRead(
            @Param("user") UserEntity user,
            @Param("readAt") OffsetDateTime readAt,
            @Param("now") OffsetDateTime now
    );

    @Modifying
    @Query("""
            delete from NotificationEntity n
            where n.id = :notificationId
              and n.user = :user
            """)
    int deleteOwnedNotification(
            @Param("notificationId") UUID notificationId,
            @Param("user") UserEntity user
    );
}