package com.example.razorpaywebhook.exception;

public class SimulatedGatewayException extends RuntimeException {
    public SimulatedGatewayException(String message) {
        super(message);
    }
}