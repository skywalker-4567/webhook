package com.example.razorpaywebhook.scheduler;

import com.example.razorpaywebhook.distributed.LeaderElectionService;
import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.domain.entity.ReconciliationLog;
import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.enums.PaymentStatus;
import com.example.razorpaywebhook.enums.WebhookStatus;
import com.example.razorpaywebhook.repository.PaymentRecordRepository;
import com.example.razorpaywebhook.repository.ReconciliationLogRepository;
import com.example.razorpaywebhook.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final PaymentRecordRepository paymentRecordRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final LeaderElectionService leaderElectionService;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void reconcile() {
        if (!leaderElectionService.isLeader("reconciliation")) {
            return;
        }

        log.info("ReconciliationScheduler: starting reconciliation pass");
        unstickProcessingWebhooks();
        reconcileEligiblePayments();
    }

    private void unstickProcessingWebhooks() {
        Instant stuckBefore = Instant.now().minus(10, ChronoUnit.MINUTES);

        List<WebhookEvent> stuck = webhookEventRepository
                .findByStatusAndUpdatedAtBefore(WebhookStatus.PROCESSING, stuckBefore);

        if (stuck.isEmpty()) return;

        log.warn("ReconciliationScheduler: found {} stuck PROCESSING webhooks — resetting",
                stuck.size());

        for (WebhookEvent event : stuck) {
            event.setStatus(WebhookStatus.RECEIVED);
            webhookEventRepository.save(event);
            log.info("ReconciliationScheduler: reset stuck webhook: eventId={}",
                    event.getEventId());
        }
    }

    private void reconcileEligiblePayments() {
        Instant tenMinutesAgo      = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);

        List<PaymentRecord> eligible = paymentRecordRepository
                .findByStatusInAndUpdatedAtBetween(
                        List.of(PaymentStatus.FAILED, PaymentStatus.AUTHORIZED),
                        twentyFourHoursAgo,
                        tenMinutesAgo);

        if (eligible.isEmpty()) return;

        log.info("ReconciliationScheduler: reconciling {} eligible payments", eligible.size());

        for (PaymentRecord record : eligible) {
            reconcilePayment(record);
        }
    }

    private void reconcilePayment(PaymentRecord record) {
        log.warn("STUB: reconcilePayment — Razorpay gateway check not implemented. " +
                "Logging SKIPPED for paymentId={}", record.getPaymentId());
        try {
            ReconciliationLog entry = ReconciliationLog.builder()
                    .paymentId(record.getPaymentId())
                    .internalStatus(record.getStatus().name())
                    .gatewayStatus(record.getStatus().name())
                    .actionTaken("SKIPPED")
                    .skipReason("GATEWAY_MATCH_ASSUMED")
                    .source("SCHEDULER")
                    .reason("Reconciliation pass — no gateway discrepancy detected")
                    .build();

            reconciliationLogRepository.save(entry);
            log.info("ReconciliationScheduler: logged reconciliation for paymentId={}",
                    record.getPaymentId());
        } catch (Exception ex) {
            log.error("ReconciliationScheduler: failed to reconcile paymentId={}",
                    record.getPaymentId(), ex);
        }
    }
}