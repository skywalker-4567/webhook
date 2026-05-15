package com.example.razorpaywebhook.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, String>> error(HttpStatus status,
                                                      String code,
                                                      String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handle(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(SignatureInvalidException.class)
    public ResponseEntity<Map<String, String>> handle(SignatureInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, "SIGNATURE_INVALID", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(OrderNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(PaymentNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RefundNotEligibleException.class)
    public ResponseEntity<Map<String, String>> handle(RefundNotEligibleException ex) {
        return error(HttpStatus.BAD_REQUEST, "REFUND_NOT_ELIGIBLE", ex.getMessage());
    }

    @ExceptionHandler(AmountExceedsPaymentException.class)
    public ResponseEntity<Map<String, String>> handle(AmountExceedsPaymentException ex) {
        return error(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_PAYMENT", ex.getMessage());
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<Map<String, String>> handle(GatewayTimeoutException ex) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", ex.getMessage());
    }

    @ExceptionHandler(GatewayUnavailableException.class)
    public ResponseEntity<Map<String, String>> handle(GatewayUnavailableException ex) {
        return error(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR", ex.getMessage());
    }

    @ExceptionHandler(StateViolationException.class)
    public ResponseEntity<Map<String, String>> handle(StateViolationException ex) {
        return error(HttpStatus.CONFLICT, "STATE_VIOLATION", ex.getMessage());
    }

    @ExceptionHandler(DataInconsistencyException.class)
    public ResponseEntity<Map<String, String>> handle(DataInconsistencyException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "DATA_INCONSISTENCY", ex.getMessage());
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<Map<String, String>> handle(InvalidDateRangeException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", ex.getMessage());
    }

    @ExceptionHandler(LedgerCurrencyMismatchException.class)
    public ResponseEntity<Map<String, String>> handle(LedgerCurrencyMismatchException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "LEDGER_CURRENCY_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(LedgerInvariantException.class)
    public ResponseEntity<Map<String, String>> handle(LedgerInvariantException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "LEDGER_INVARIANT_VIOLATION", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handle(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handle(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }
}