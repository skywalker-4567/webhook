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
public class AuditLogDTO {
    private UUID id;
    private Long sequenceNum;
    private String entityType;
    private String entityId;
    private String action;
    private String actor;
    private String oldValue;
    private String newValue;
    private String previousHash;
    private String currentHash;
    private Instant createdAt;
    private UUID correlationId;
}