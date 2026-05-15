package com.example.razorpaywebhook.exception;

public class LedgerCurrencyMismatchException extends RuntimeException {
    public LedgerCurrencyMismatchException(String message) {
        super(message);
    }
}