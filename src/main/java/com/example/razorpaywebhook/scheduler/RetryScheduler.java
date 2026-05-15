package com.example.razorpaywebhook.scheduler;

import com.example.razorpaywebhook.distributed.LeaderElectionService;
import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.enums.WebhookStatus;
import com.example.razorpaywebhook.event.WebhookReceivedEvent;
import com.example.razorpaywebhook.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final WebhookEventRepository webhookEventRepository;
    private final LeaderElectionService leaderElectionService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void retryFailedWebhooks() {
        if (!leaderElectionService.isLeader("webhook-retry")) {
            return;
        }

        List<WebhookEvent> eligible = webhookEventRepository
                .findByStatusAndNextRetryAtBefore(WebhookStatus.FAILED, Instant.now());

        if (eligible.isEmpty()) return;

        log.info("RetryScheduler: found {} failed webhooks eligible for retry", eligible.size());

        for (WebhookEvent event : eligible) {
            if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("RetryScheduler: webhook exhausted retries, leaving FAILED: eventId={}",
                        event.getEventId());
                continue;
            }

            event.setStatus(WebhookStatus.RECEIVED);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setNextRetryAt(null);
            webhookEventRepository.save(event);

            log.info("RetryScheduler: resetting to RECEIVED for retry: eventId={} attempt={}",
                    event.getEventId(), event.getRetryCount());

            final UUID webhookId = event.getId();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventPublisher.publishEvent(new WebhookReceivedEvent(webhookId));
                        }
                    });
        }
    }
}