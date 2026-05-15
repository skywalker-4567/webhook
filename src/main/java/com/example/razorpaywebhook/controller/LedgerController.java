package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.dto.AccountBalanceResponse;
import com.example.razorpaywebhook.dto.LedgerResponse;
import com.example.razorpaywebhook.enums.LedgerAccountType;
import com.example.razorpaywebhook.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping
    public ResponseEntity<LedgerResponse> getLedger(
            @RequestParam(required = false) String paymentId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID transactionId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.min(size, 200);

        if (paymentId != null) {
            return ResponseEntity.ok(
                    ledgerService.getLedgerByPaymentId(paymentId, page, clampedSize));
        }
        if (orderId != null) {
            return ResponseEntity.ok(
                    ledgerService.getLedgerByOrderId(orderId, page, clampedSize));
        }
        if (transactionId != null) {
            return ResponseEntity.ok(
                    ledgerService.getLedgerByTransactionId(transactionId, page, clampedSize));
        }
        if (accountType != null && from != null && to != null) {
            return ResponseEntity.ok(
                    ledgerService.getLedgerByAccountType(
                            LedgerAccountType.valueOf(accountType.toUpperCase()),
                            Instant.parse(from),
                            Instant.parse(to),
                            page, clampedSize));
        }

        throw new IllegalArgumentException(
                "At least one filter is required: paymentId, orderId, transactionId, " +
                        "or accountType with from and to dates");
    }

    @GetMapping("/accounts/{accountType}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable String accountType,
            @RequestParam(required = false) String asOf) {

        Instant asOfInstant = asOf != null ? Instant.parse(asOf) : Instant.now();

        return ResponseEntity.ok(
                ledgerService.getAccountBalance(
                        LedgerAccountType.valueOf(accountType.toUpperCase()), asOfInstant));
    }
}