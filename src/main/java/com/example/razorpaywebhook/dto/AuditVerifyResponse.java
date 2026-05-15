package com.example.razorpaywebhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditVerifyResponse {

    public enum VerifyResult {
        VALID, BROKEN
    }

    private VerifyResult result;
    private Integer brokenAtSequence;
    private String message;
}