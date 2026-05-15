package com.example.razorpaywebhook.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentCapturedEvent(
        UUID eventId,
        String paymentId,
        UUID internalOrderId,
        String razorpayOrderId,
        Long amount,
        String currency,
        Instant capturedAt,
        UUID correlationId
) {}