package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.enums.FailureType;
import com.example.razorpaywebhook.enums.WebhookStatus;
import com.example.razorpaywebhook.event.WebhookReceivedEvent;
import com.example.razorpaywebhook.exception.UnknownEventTypeException;
import com.example.razorpaywebhook.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final ApplicationContext applicationContext;

    @Async
    @EventListener
    public void onWebhookReceived(WebhookReceivedEvent receivedEvent) {
        applicationContext.getBean(WebhookProcessingService.class)
                .processWebhook(receivedEvent.webhookEventId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processWebhook(UUID webhookEventId) {
        int updated = webhookEventRepository.acquireProcessingLock(
                webhookEventId, WebhookStatus.PROCESSING, WebhookStatus.RECEIVED);
        if (updated == 0) {
            log.info("Webhook {} already processing or not RECEIVED - skipping", webhookEventId);
            return;
        }

        WebhookEvent event = webhookEventRepository.findById(webhookEventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook not found after lock: " + webhookEventId));

        MDC.put("correlationId", event.getCorrelationId().toString());
        try {
            dispatch(event);
            event.setStatus(WebhookStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
            log.info("Webhook processed: eventId={}", event.getEventId());
        } catch (UnknownEventTypeException ex) {
            markFailed(event, FailureType.UNKNOWN, ex.getMessage());
        } catch (Exception ex) {
            log.error("Webhook processing failed: eventId={}", event.getEventId(), ex);
            markFailed(event, FailureType.DB_ERROR, ex.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void dispatch(WebhookEvent event) {
        switch (event.getEventType()) {
            case "payment.captured" -> paymentService.handleCaptured(event);
            case "payment.failed"   -> paymentService.handleFailed(event);
            case "payment.refunded" -> paymentService.handleRefunded(event);
            default -> throw new UnknownEventTypeException(event.getEventType());
        }
    }

    private void markFailed(WebhookEvent event, FailureType failureType, String reason) {
        event.setStatus(WebhookStatus.FAILED);
        event.setFailureType(failureType);
        event.setFailureReason(reason);
        webhookEventRepository.save(event);
        log.warn("Webhook marked FAILED: eventId={} reason={}", event.getEventId(), reason);
    }
}