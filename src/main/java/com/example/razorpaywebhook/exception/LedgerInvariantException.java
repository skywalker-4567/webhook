package com.example.razorpaywebhook.exception;

public class LedgerInvariantException extends RuntimeException {
    public LedgerInvariantException(String message) {
        super(message);
    }
}