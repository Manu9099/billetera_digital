package com.yapeseguro.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPaymentJob {

    private final ScheduledPaymentService scheduledPaymentService;
    private final TransactionService transactionService;
    private final DisputeService disputeService;

    /**
     * 08:00 Lima time.
     * Procesa autopagos vencidos.
     */
    @Scheduled(cron = "0 0 13 * * *")
    public void processScheduledPayments() {
        int processed = scheduledPaymentService.processDueAutoPayments();

        log.info(
                "Scheduled payments processed. processed={}, at={}",
                processed,
                OffsetDateTime.now()
        );
    }

    /**
     * 20:00 Lima time.
     * Crea notificaciones in-app para pagos próximos.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void sendUpcomingPaymentNotifications() {
        int notifications = scheduledPaymentService.createUpcomingPaymentNotifications();

        log.info(
                "Upcoming payment notifications created. count={}, at={}",
                notifications,
                OffsetDateTime.now()
        );
    }

    /**
     * Cada hora:
     * - libera holds marketplace vencidos sin disputa
     * - reembolsa automáticamente disputas vencidas
     */
    @Scheduled(cron = "0 0 * * * *")
    public void processMarketplaceAutomation() {
        int releasedHolds = transactionService.releaseExpiredMarketplaceHoldsAutomatically();
        int resolvedDisputes = disputeService.resolveExpiredMarketplaceDisputesAutomatically();

        log.info(
                "Marketplace automation finished. releasedHolds={}, resolvedDisputes={}, at={}",
                releasedHolds,
                resolvedDisputes,
                OffsetDateTime.now()
        );
    }
}