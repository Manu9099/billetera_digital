package com.yapeseguro.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Feature #7: Pagos Programados
 * Job nocturno que ejecuta pagos y notifica con anticipación
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPaymentJob {

    // TODO: inyectar ScheduledPaymentRepository y TransactionService

    /**
     * Se ejecuta todos los días a las 08:00 Lima time (UTC-5)
     * Procesa pagos que vencen hoy y notifica los del mañana
     */
    @Scheduled(cron = "0 0 13 * * *") // 08:00 Lima = 13:00 UTC
    public void processScheduledPayments() {
        log.info("Processing scheduled payments for {}", OffsetDateTime.now());

        // 1. Buscar pagos activos cuyo next_payment_date sea hoy
        // scheduledPaymentRepository.findDueToday()
        //     .forEach(payment -> {
        //         if (payment.isAutoPayEnabled()) {
        //             transactionService.createP2PPayment(...);
        //         }
        //         notificationService.sendPaymentDueNotification(payment);
        //     });
    }

    /**
     * Notificaciones previas — avisa 1 día antes si notify_days_in_advance = 1
     * Se ejecuta a las 20:00 Lima para notificar los pagos del día siguiente
     */
    @Scheduled(cron = "0 0 1 * * *") // 20:00 Lima = 01:00 UTC del día siguiente
    public void sendUpcomingPaymentNotifications() {
        log.info("Sending upcoming payment notifications");

        // scheduledPaymentRepository.findDueTomorrow()
        //     .forEach(payment -> notificationService.sendUpcomingNotification(payment));
    }

    /**
     * Liberar holds de marketplace que expiró su plazo — cada hora
     */
    @Scheduled(cron = "0 0 * * * *")
    public void releaseExpiredHolds() {
        log.info("Releasing expired marketplace holds");

        // transactionRepository.findExpiredHolds(OffsetDateTime.now())
        //     .forEach(tx -> transactionService.releaseHold(tx));
    }
}
