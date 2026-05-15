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
public class ReconciliationLogDTO {
    private UUID id;
    private String paymentId;
    private String internalStatus;
    private String gatewayStatus;
    private String actionTaken;
    private String skipReason;
    private String reason;
    private Instant reconciledAt;
}