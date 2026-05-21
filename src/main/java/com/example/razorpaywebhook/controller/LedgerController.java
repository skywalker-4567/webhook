package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.dto.AccountBalanceResponse;
import com.example.razorpaywebhook.dto.LedgerResponse;
import com.example.razorpaywebhook.enums.LedgerAccountType;
import com.example.razorpaywebhook.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ledger", description = "Double-entry financial ledger queries and account balances")
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping
    @Operation(
            summary = "Query ledger entries",
            description = "Returns ledger entries filtered by paymentId, orderId, transactionId, " +
                    "or accountType + date range. At least one filter is required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger entries with debit/credit totals"),
            @ApiResponse(responseCode = "400", description = "No filter provided or invalid parameters"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public ResponseEntity<LedgerResponse> getLedger(
            @Parameter(description = "Filter by Razorpay payment ID")
            @RequestParam(required = false) String paymentId,
            @Parameter(description = "Filter by internal order UUID")
            @RequestParam(required = false) UUID orderId,
            @Parameter(description = "Filter by ledger transaction UUID (links DEBIT + CREDIT pair)")
            @RequestParam(required = false) UUID transactionId,
            @Parameter(description = "Account type: CUSTOMER, MERCHANT, GATEWAY")
            @RequestParam(required = false) String accountType,
            @Parameter(description = "ISO 8601 start timestamp (required with accountType)")
            @RequestParam(required = false) String from,
            @Parameter(description = "ISO 8601 end timestamp (required with accountType)")
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.min(size, 200);

        if (paymentId != null)    return ResponseEntity.ok(ledgerService.getLedgerByPaymentId(paymentId, page, clampedSize));
        if (orderId != null)      return ResponseEntity.ok(ledgerService.getLedgerByOrderId(orderId, page, clampedSize));
        if (transactionId != null) return ResponseEntity.ok(ledgerService.getLedgerByTransactionId(transactionId, page, clampedSize));
        if (accountType != null && from != null && to != null) {
            return ResponseEntity.ok(ledgerService.getLedgerByAccountType(
                    LedgerAccountType.valueOf(accountType.toUpperCase()),
                    Instant.parse(from), Instant.parse(to), page, clampedSize));
        }

        throw new IllegalArgumentException(
                "At least one filter is required: paymentId, orderId, transactionId, " +
                        "or accountType with from and to dates");
    }

    @GetMapping("/accounts/{accountType}/balance")
    @Operation(
            summary = "Get account balance",
            description = "Returns the point-in-time balance for CUSTOMER, MERCHANT, or GATEWAY account"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account balance"),
            @ApiResponse(responseCode = "400", description = "Invalid account type")
    })
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @Parameter(description = "Account type: CUSTOMER, MERCHANT, GATEWAY")
            @PathVariable String accountType,
            @Parameter(description = "ISO 8601 point-in-time (defaults to now)")
            @RequestParam(required = false) String asOf) {

        Instant asOfInstant = asOf != null ? Instant.parse(asOf) : Instant.now();
        return ResponseEntity.ok(ledgerService.getAccountBalance(
                LedgerAccountType.valueOf(accountType.toUpperCase()), asOfInstant));
    }
}