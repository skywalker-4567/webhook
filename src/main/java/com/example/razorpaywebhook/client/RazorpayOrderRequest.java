package com.example.razorpaywebhook.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderRequest {
    private Long amount;
    private String currency;
    private String receipt;
    private Map<String, Object> notes;
}