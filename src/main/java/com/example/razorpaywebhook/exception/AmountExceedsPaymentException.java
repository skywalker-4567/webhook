package com.example.razorpaywebhook.exception;

public class AmountExceedsPaymentException extends RuntimeException {
    public AmountExceedsPaymentException(String message) {
        super(message);
    }
}