package com.example.razorpaywebhook.exception;

public class DataInconsistencyException extends RuntimeException {
    public DataInconsistencyException(String message) {
        super(message);
    }
}