package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.DisputeEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<DisputeEntity, UUID> {

    Optional<DisputeEntity> findByTransaction(TransactionEntity transaction);

    List<DisputeEntity> findByCreatedByUserOrderByCreatedAtDesc(UserEntity user);

    List<DisputeEntity> findByRespondentUserOrderByCreatedAtDesc(UserEntity user);
}