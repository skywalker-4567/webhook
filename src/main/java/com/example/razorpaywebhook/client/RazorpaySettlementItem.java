package com.example.razorpaywebhook.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpaySettlementItem {
    private String id;
    @JsonProperty("entity_id")
    private String entityId;
    private String type;
    private Long amount;
    private String currency;
    private String status;
    @JsonProperty("created_at")
    private Long createdAt;
}