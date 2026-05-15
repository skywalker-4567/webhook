package com.example.razorpaywebhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckDTO {
    private UUID id;
    private String paymentId;
    private String eventType;
    private String paymentStatus;
    @JsonProperty("isFraud")
    private boolean isFraud;
    private List<String> triggeredRules;
    private Double mlFraudScore;
    private Boolean mlIsAnomaly;
    private Instant createdAt;
}