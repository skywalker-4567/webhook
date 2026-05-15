package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.domain.entity.LedgerRetryQueue;
import com.example.razorpaywebhook.enums.RetryStatus;
import com.example.razorpaywebhook.event.PaymentCapturedEvent;
import com.example.razorpaywebhook.event.PaymentRefundedEvent;
import com.example.razorpaywebhook.repository.LedgerRetryQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerRetryService {

    private static final int MAX_RETRY_COUNT = 5;

    private final LedgerRetryQueueRepository ledgerRetryQueueRepository;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void retryPending() {
        List<LedgerRetryQueue> pending = ledgerRetryQueueRepository
                .findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                        RetryStatus.PENDING, Instant.now());

        if (pending.isEmpty()) {
            return;
        }

        log.info("LedgerRetryService: found {} pending entries to retry", pending.size());

        for (LedgerRetryQueue entry : pending) {
            processEntry(entry);
        }
    }

    private void processEntry(LedgerRetryQueue entry) {
        entry.setStatus(RetryStatus.PROCESSING);
        ledgerRetryQueueRepository.save(entry);

        try {
            replayEvent(entry);
            ledgerRetryQueueRepository.delete(entry);
            log.info("Ledger retry succeeded and removed: id={} eventType={}",
                    entry.getId(), entry.getEventType());

        } catch (Exception ex) {
            int newRetryCount = entry.getRetryCount() + 1;
            entry.setRetryCount(newRetryCount);

            if (newRetryCount >= MAX_RETRY_COUNT) {
                entry.setStatus(RetryStatus.EXHAUSTED);
                ledgerRetryQueueRepository.save(entry);
                log.error("CRITICAL: Ledger retry EXHAUSTED after {} attempts: id={} eventType={}",
                        newRetryCount, entry.getId(), entry.getEventType());
            } else {
                long backoffSeconds = (long) Math.pow(2, newRetryCount) * 30;
                entry.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                entry.setStatus(RetryStatus.PENDING);
                entry.setLastFailureReason(ex.getMessage());
                ledgerRetryQueueRepository.save(entry);
                log.warn("Ledger retry failed (attempt {}), next retry in {}s: id={} eventType={}",
                        newRetryCount, backoffSeconds, entry.getId(), entry.getEventType());
            }
        }
    }

    private void replayEvent(LedgerRetryQueue entry) throws Exception {
        String payload = entry.getEventPayload();
        switch (entry.getEventType()) {
            case "PAYMENT_CAPTURED" -> {
                PaymentCapturedEvent event =
                        objectMapper.readValue(payload, PaymentCapturedEvent.class);
                ledgerService.onPaymentCaptured(event);
            }
            case "PAYMENT_REFUNDED" -> {
                PaymentRefundedEvent event =
                        objectMapper.readValue(payload, PaymentRefundedEvent.class);
                ledgerService.onPaymentRefunded(event);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown ledger retry event type: " + entry.getEventType());
        }
    }
}