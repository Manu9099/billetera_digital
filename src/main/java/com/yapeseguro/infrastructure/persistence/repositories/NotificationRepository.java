package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.NotificationEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    boolean existsByUserAndNotificationTypeAndRelatedEntityId(
            UserEntity user,
            NotificationEntity.NotificationType notificationType,
            UUID relatedEntityId
    );
}