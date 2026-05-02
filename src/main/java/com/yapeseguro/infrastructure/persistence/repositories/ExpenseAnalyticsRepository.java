package com.yapeseguro.infrastructure.persistence.repositories;

import com.yapeseguro.infrastructure.persistence.entities.ExpenseAnalyticsEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseAnalyticsRepository extends JpaRepository<ExpenseAnalyticsEntity, UUID> {

    List<ExpenseAnalyticsEntity> findByWalletAndYearMonthOrderByTotalSpentDesc(
            WalletEntity wallet,
            String yearMonth
    );

    void deleteByWalletAndYearMonth(
            WalletEntity wallet,
            String yearMonth
    );
}