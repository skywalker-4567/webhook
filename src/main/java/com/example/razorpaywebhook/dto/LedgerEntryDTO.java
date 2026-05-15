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
public class LedgerEntryDTO {
    private UUID id;
    private String transactionRef;
    private String transactionType;
    private String entryType;
    private String accountType;
    private Long amount;
    private String currency;
    private String description;
    private UUID transactionId;
    private Instant createdAt;
    private UUID correlationId;
}