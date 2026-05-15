package com.example.razorpaywebhook.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderResponse {
    private String id;
    private Long amount;
    private String currency;
    private String receipt;
    private String status;
}