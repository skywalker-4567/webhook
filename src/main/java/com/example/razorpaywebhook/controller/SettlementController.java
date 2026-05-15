package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.dto.SettlementSummaryResponse;
import com.example.razorpaywebhook.exception.InvalidDateRangeException;
import com.example.razorpaywebhook.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/report")
    public ResponseEntity<byte[]> getReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        if (from == null || to == null) {
            throw new InvalidDateRangeException("from and to parameters are required");
        }

        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant   = parseInstant(to, "to");

        byte[] csvBytes = settlementService.generateCsvBytes(fromInstant, toInstant);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"settlement_report.csv\"")
                .body(csvBytes);
    }

    @GetMapping("/summary")
    public ResponseEntity<SettlementSummaryResponse> getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        if (from == null || to == null) {
            throw new InvalidDateRangeException("from and to parameters are required");
        }

        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant   = parseInstant(to, "to");

        return ResponseEntity.ok(settlementService.getSummary(fromInstant, toInstant));
    }

    private Instant parseInstant(String value, String paramName) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new InvalidDateRangeException(
                    "Invalid ISO date for parameter '" + paramName + "': " + value);
        }
    }
}