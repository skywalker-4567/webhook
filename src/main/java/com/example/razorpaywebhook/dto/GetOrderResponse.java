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
public class GetOrderResponse {
    private String orderId;
    private String razorpayOrderId;
    private String customerId;
    private Long amount;
    private String currency;
    private String status;
    private PaymentSummary payment;
    private Instant createdAt;
}