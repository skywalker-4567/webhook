package com.example.razorpaywebhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventDTO {
    private String eventId;
    private String eventType;
    private String paymentId;
    private String status;
    private String failureType;
    private String failureReason;
    private Instant receivedAt;
    private Instant processedAt;
    private int retryCount;
    private UUID correlationId;
}