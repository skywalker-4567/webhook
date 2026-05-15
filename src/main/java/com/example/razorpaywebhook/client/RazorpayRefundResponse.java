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
public class RazorpayRefundResponse {
    private String id;
    @JsonProperty("payment_id")
    private String paymentId;
    private Long amount;
    private String currency;
    private String status;
}