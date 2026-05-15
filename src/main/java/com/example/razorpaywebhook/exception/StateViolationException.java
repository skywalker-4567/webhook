package com.example.razorpaywebhook.exception;

public class StateViolationException extends RuntimeException {
    public StateViolationException(String message) {
        super(message);
    }
}