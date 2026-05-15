package com.example.razorpaywebhook.enums;

public enum FailureType {
    SIGNATURE_INVALID, PARSE_ERROR, DB_ERROR,
    STATE_VIOLATION, DATA_INCONSISTENCY, UNKNOWN
}