package com.example.razorpaywebhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementSummaryResponse {
    private Instant periodStart;
    private Instant periodEnd;
    private Long totalPayments;
    private Long totalRefunds;
    private Long netAmount;
    private String currency;
    private int entryCount;
}