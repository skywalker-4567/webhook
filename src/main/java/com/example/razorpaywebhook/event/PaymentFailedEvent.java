package com.example.razorpaywebhook.event;

import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        String paymentId,
        UUID internalOrderId,
        String reason,
        UUID correlationId
) {}