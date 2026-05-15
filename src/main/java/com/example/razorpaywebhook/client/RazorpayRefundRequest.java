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
public class RazorpayRefundRequest {
    private Long amount;
    private Map<String, Object> notes;
}