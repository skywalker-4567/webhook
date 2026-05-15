package com.example.razorpaywebhook.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
        UUID eventId,
        String paymentId,
        String refundId,
        UUID internalOrderId,
        Long amount,
        String currency,
        Instant refundedAt,
        UUID correlationId
) {}