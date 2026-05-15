package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.enums.OrderStatus;
import com.example.razorpaywebhook.enums.PaymentStatus;
import com.example.razorpaywebhook.event.PaymentCapturedEvent;
import com.example.razorpaywebhook.event.PaymentFailedEvent;
import com.example.razorpaywebhook.event.PaymentRefundedEvent;
import com.example.razorpaywebhook.exception.DataInconsistencyException;
import com.example.razorpaywebhook.exception.StateViolationException;
import com.example.razorpaywebhook.fraud.FraudService;
import com.example.razorpaywebhook.fraud.MLService;
import com.example.razorpaywebhook.repository.OrderRepository;
import com.example.razorpaywebhook.repository.PaymentRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final FraudService fraudService;
    private final MLService mlService;

    @Transactional
    public void handleCaptured(WebhookEvent event) {
        JsonNode payload       = parsePayload(event.getPayload());
        JsonNode entity        = extractPaymentEntity(payload);

        String  paymentId      = entity.path("id").asText();
        long    amount         = entity.path("amount").asLong();
        String  currency       = entity.path("currency").asText();
        String  method         = entity.path("method").asText(null);
        String  email          = entity.path("email").asText(null);
        String  contact        = entity.path("contact").asText(null);
        String  razorpayOrderId = entity.path("order_id").asText(null);
        Instant capturedAt     = Instant.now();

        PaymentRecord record = findOrCreatePaymentRecord(
                paymentId, amount, currency, method, email, contact,
                event.getCorrelationId());

        validateConsistency(record, amount, currency);
        validateStateTransition(record.getStatus(), PaymentStatus.CAPTURED);

        record.setStatus(PaymentStatus.CAPTURED);
        record.setCapturedAt(capturedAt);
        record.setMethod(method);

        linkToOrder(record, razorpayOrderId);

        PaymentRecord saved = paymentRecordRepository.save(record);

        // Phase 12: sync fraud check — never throws
        fraudService.checkAndPersist(saved, event.getEventType());

        // Phase 13: async ML scoring — guard enforced inside MLService
        mlService.scoreAsync(saved);

        log.info("Payment captured: paymentId={}", paymentId);

        UUID eventUUID = event.getId();
        publishAfterCommit(new PaymentCapturedEvent(
                eventUUID,
                saved.getPaymentId(),
                saved.getInternalOrderId(),
                razorpayOrderId,
                saved.getAmount(),
                saved.getCurrency(),
                capturedAt,
                saved.getCorrelationId()
        ));
    }

    @Transactional
    public void handleFailed(WebhookEvent event) {
        JsonNode payload  = parsePayload(event.getPayload());
        JsonNode entity   = extractPaymentEntity(payload);

        String  paymentId = entity.path("id").asText();
        long    amount    = entity.path("amount").asLong();
        String  currency  = entity.path("currency").asText();
        String  errorDesc = entity.path("error_description").asText("Payment failed");

        PaymentRecord record = findOrCreatePaymentRecord(
                paymentId, amount, currency, null, null, null,
                event.getCorrelationId());

        validateStateTransition(record.getStatus(), PaymentStatus.FAILED);

        if (record.getId() != null) {
            record.setRetryCount(record.getRetryCount() + 1);
        }
        record.setStatus(PaymentStatus.FAILED);

        PaymentRecord saved = paymentRecordRepository.save(record);

        // Phase 13: async ML scoring on failed payments
        mlService.scoreAsync(saved);

        log.info("Payment failed: paymentId={}", paymentId);

        UUID eventUUID = event.getId();
        publishAfterCommit(new PaymentFailedEvent(
                eventUUID,
                saved.getPaymentId(),
                saved.getInternalOrderId(),
                errorDesc,
                saved.getCorrelationId()
        ));
    }

    @Transactional
    public void handleRefunded(WebhookEvent event) {
        JsonNode payload       = parsePayload(event.getPayload());
        JsonNode refundEntity  = payload.path("refund").path("entity");
        JsonNode paymentEntity = extractPaymentEntity(payload);

        String  paymentId    = paymentEntity.path("id").asText();
        String  refundId     = refundEntity.path("id").asText();
        long    refundAmount = refundEntity.path("amount").asLong();
        String  currency     = refundEntity.path("currency").asText();
        Instant refundedAt   = Instant.now();

        PaymentRecord record = paymentRecordRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new DataInconsistencyException(
                        "Payment not found for refund: " + paymentId));

        validateStateTransition(record.getStatus(), PaymentStatus.REFUNDED);
        record.setStatus(PaymentStatus.REFUNDED);

        PaymentRecord saved = paymentRecordRepository.save(record);
        log.info("Payment refunded: paymentId={} refundId={}", paymentId, refundId);

        UUID eventUUID = event.getId();
        publishAfterCommit(new PaymentRefundedEvent(
                eventUUID,
                saved.getPaymentId(),
                refundId,
                saved.getInternalOrderId(),
                refundAmount,
                currency,
                refundedAt,
                saved.getCorrelationId()
        ));
    }

    private PaymentRecord findOrCreatePaymentRecord(String paymentId, long amount,
                                                    String currency, String method,
                                                    String email, String contact,
                                                    UUID correlationId) {
        return paymentRecordRepository.findByPaymentId(paymentId)
                .orElseGet(() -> PaymentRecord.builder()
                        .paymentId(paymentId)
                        .amount(amount)
                        .currency(currency)
                        .method(method)
                        .email(email)
                        .contact(contact)
                        .status(PaymentStatus.AUTHORIZED)
                        .retryCount(0)
                        .correlationId(correlationId)
                        .build());
    }

    private void validateConsistency(PaymentRecord record, long amount, String currency) {
        if (record.getId() != null) {
            if (record.getAmount() != amount) {
                throw new DataInconsistencyException(
                        "Amount mismatch for payment: " + record.getPaymentId());
            }
            if (!record.getCurrency().equals(currency)) {
                throw new DataInconsistencyException(
                        "Currency mismatch for payment: " + record.getPaymentId());
            }
        }
    }

    private void validateStateTransition(PaymentStatus current, PaymentStatus to) {
        if (current == null) return;
        if (current == PaymentStatus.REFUNDED) {
            throw new StateViolationException("Cannot transition from REFUNDED to " + to);
        }
        if (current == PaymentStatus.FAILED && to != PaymentStatus.FAILED) {
            throw new StateViolationException("Cannot transition from FAILED to " + to);
        }
        boolean valid = switch (current) {
            case AUTHORIZED -> to == PaymentStatus.CAPTURED || to == PaymentStatus.FAILED;
            case CAPTURED   -> to == PaymentStatus.REFUNDED;
            case FAILED     -> false;
            case REFUNDED   -> false;
        };
        if (!valid) {
            throw new StateViolationException(
                    "Invalid state transition: " + current + " → " + to);
        }
    }

    private void linkToOrder(PaymentRecord record, String razorpayOrderId) {
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) return;
        orderRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(order -> {
            record.setOrderId(razorpayOrderId);
            record.setInternalOrderId(order.getId());
            int rows = orderRepository.updateStatusConditionally(
                    order.getId(), OrderStatus.PAID, OrderStatus.CREATED);
            if (rows > 0) {
                log.info("Order {} marked PAID", order.getId());
            }
        });
    }

    private void publishAfterCommit(Object event) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(event);
                    }
                });
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new DataInconsistencyException(
                    "Failed to parse webhook payload: " + ex.getMessage());
        }
    }

    private JsonNode extractPaymentEntity(JsonNode root) {
        return root.path("payload").path("payment").path("entity");
    }
}