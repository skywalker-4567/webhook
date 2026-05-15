package com.example.razorpaywebhook.exception;

public class SignatureInvalidException extends RuntimeException {
    public SignatureInvalidException() {
        super("Invalid webhook signature");
    }
}