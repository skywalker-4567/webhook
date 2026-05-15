package com.example.razorpaywebhook.fraud;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MLScoreRequest(
        double amount,
        @JsonProperty("retry_count") int retryCount,
        @JsonProperty("time_diff_seconds") double timeDiffSeconds,
        String status
) {}