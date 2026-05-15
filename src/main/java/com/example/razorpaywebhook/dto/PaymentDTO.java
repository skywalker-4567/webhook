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
public class PaymentDTO {
    private String paymentId;
    private String orderId;
    private Long amount;
    private String currency;
    private String status;
    private String method;
    private Instant capturedAt;
    private int retryCount;
    private UUID correlationId;
}