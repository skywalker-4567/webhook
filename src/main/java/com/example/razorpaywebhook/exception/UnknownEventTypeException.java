package com.example.razorpaywebhook.exception;

public class UnknownEventTypeException extends RuntimeException {
    public UnknownEventTypeException(String eventType) {
        super("Unknown event type: " + eventType);
    }
}