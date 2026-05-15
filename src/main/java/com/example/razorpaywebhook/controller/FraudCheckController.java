package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.FraudCheck;
import com.example.razorpaywebhook.dto.FraudCheckDTO;
import com.example.razorpaywebhook.fraud.FraudService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/fraud-checks")
@RequiredArgsConstructor
public class FraudCheckController {

    private final FraudService fraudService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<List<FraudCheckDTO>> getFraudChecks(
            @RequestParam String paymentId) {

        List<FraudCheck> checks = fraudService.getFraudChecks(paymentId);

        List<FraudCheckDTO> dtos = checks.stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    private FraudCheckDTO toDTO(FraudCheck f) {
        List<String> rules = parseRules(f.getTriggeredRules());
        return FraudCheckDTO.builder()
                .id(f.getId())
                .paymentId(f.getPaymentId())
                .eventType(f.getEventType())
                .paymentStatus(f.getPaymentStatus())
                .isFraud(f.isFraud())
                .triggeredRules(rules)
                .mlFraudScore(f.getMlFraudScore())
                .mlIsAnomaly(f.getMlIsAnomaly())
                .createdAt(f.getCreatedAt())
                .build();
    }

    private List<String> parseRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse triggeredRules JSON: {}", json);
            return Collections.emptyList();
        }
    }
}