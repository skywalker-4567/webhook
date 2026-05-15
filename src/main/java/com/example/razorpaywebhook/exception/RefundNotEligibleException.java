package com.example.razorpaywebhook.exception;

public class RefundNotEligibleException extends RuntimeException {
    public RefundNotEligibleException(String message) {
        super(message);
    }
}