package com.example.razorpaywebhook.fraud;

import java.util.List;

public record FraudResult(
        boolean isFraud,
        List<String> triggeredRules
) {}