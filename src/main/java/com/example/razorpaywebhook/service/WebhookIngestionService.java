package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.enums.FailureType;
import com.example.razorpaywebhook.enums.WebhookStatus;
import com.example.razorpaywebhook.event.WebhookReceivedEvent;
import com.example.razorpaywebhook.exception.SignatureInvalidException;
import com.example.razorpaywebhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class WebhookIngestionService {

    private final WebhookEventRepository webhookEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Counter idempotencyHitsCounter;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    public WebhookIngestionService(WebhookEventRepository webhookEventRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.webhookEventRepository = webhookEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.idempotencyHitsCounter = Counter.builder("webhooks.idempotency.hits")
                .description("Duplicate webhook event_id rejected as no-op")
                .register(meterRegistry);
    }

    @Transactional
    public WebhookEvent ingest(byte[] rawBody, String signature, String rawPayload) {
        if (!verifySignature(rawBody, signature)) {
            log.warn("Invalid webhook signature received");
            throw new SignatureInvalidException();
        }

        UUID correlationId = UUID.randomUUID();
        MDC.put("correlationId", correlationId.toString());

        try {
            JsonNode root = parsePayload(rawPayload);
            String eventId      = root.path("id").asText();
            String eventType    = root.path("event").asText();
            String paymentId    = extractPaymentId(root);
            Instant eventCreatedAt = extractEventCreatedAt(root);

            return webhookEventRepository.findByEventId(eventId)
                    .map(existing -> {
                        log.info("Duplicate webhook event: {}", eventId);
                        // metric: idempotency hit
                        idempotencyHitsCounter.increment();
                        return existing;
                    })
                    .orElseGet(() -> saveAndPublish(
                            eventId, eventType, paymentId, eventCreatedAt,
                            rawPayload, signature, correlationId));
        } catch (SignatureInvalidException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to ingest webhook", ex);
            WebhookEvent failed = WebhookEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("UNKNOWN")
                    .paymentId("UNKNOWN")
                    .payload(rawPayload)
                    .signature(signature)
                    .status(WebhookStatus.FAILED)
                    .failureType(FailureType.PARSE_ERROR)
                    .failureReason(ex.getMessage())
                    .correlationId(correlationId)
                    .build();
            return webhookEventRepository.save(failed);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private WebhookEvent saveAndPublish(String eventId, String eventType, String paymentId,
                                        Instant eventCreatedAt, String rawPayload,
                                        String signature, UUID correlationId) {
        // metric: webhooks.received tagged by event_type
        meterRegistry.counter("webhooks.received", "event_type", eventType).increment();

        WebhookEvent event = WebhookEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .paymentId(paymentId)
                .payload(rawPayload)
                .signature(signature)
                .status(WebhookStatus.RECEIVED)
                .eventCreatedAt(eventCreatedAt)
                .retryCount(0)
                .correlationId(correlationId)
                .build();

        WebhookEvent saved = webhookEventRepository.save(event);
        log.info("Webhook ingested: eventId={} eventType={}", eventId, eventType);

        UUID savedId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        MDC.put("correlationId", correlationId.toString());
                        try {
                            eventPublisher.publishEvent(new WebhookReceivedEvent(savedId));
                        } finally {
                            MDC.remove("correlationId");
                        }
                    }
                });

        return saved;
    }

    private boolean verifySignature(byte[] rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody);
            String computedHex = HexFormat.of().formatHex(computed);
            return computedHex.equals(signature);
        } catch (Exception ex) {
            log.error("Signature verification error", ex);
            return false;
        }
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse webhook payload", ex);
        }
    }

    private String extractPaymentId(JsonNode root) {
        return root.path("payload").path("payment").path("entity").path("id").asText("UNKNOWN");
    }

    private Instant extractEventCreatedAt(JsonNode root) {
        long epochSec = root.path("created_at").asLong(0);
        return epochSec > 0 ? Instant.ofEpochSecond(epochSec) : Instant.now();
    }
}