package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionReceiptRepository extends JpaRepository<TransactionReceiptEntity, UUID> {

    Optional<TransactionReceiptEntity> findByTransaction(TransactionEntity transaction);

    Optional<TransactionReceiptEntity> findByTransaction_Id(UUID transactionId);

    Optional<TransactionReceiptEntity> findByReceiptNumber(String receiptNumber);

    boolean existsByReceiptNumber(String receiptNumber);
}
