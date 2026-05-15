package com.example.razorpaywebhook.fraud;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MLScoreResponse(
        @JsonProperty("fraud_score") double fraudScore,
        @JsonProperty("is_anomaly") boolean isAnomaly
) {}