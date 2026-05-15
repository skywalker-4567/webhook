package com.example.razorpaywebhook.fraud;

import com.example.razorpaywebhook.domain.entity.FraudCheck;
import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.repository.FraudCheckRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudService {

    private final FraudRuleEngine fraudRuleEngine;
    private final FraudCheckRepository fraudCheckRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void checkAndPersist(PaymentRecord record, String eventType) {
        try {
            FraudResult result = fraudRuleEngine.evaluate(record, eventType);

            if (!result.isFraud()) {
                return;
            }

            String triggeredRulesJson = serializeRules(result.triggeredRules());

            FraudCheck fraudCheck = FraudCheck.builder()
                    .paymentId(record.getPaymentId())
                    .eventType(eventType)
                    .paymentStatus(record.getStatus().name())
                    .isFraud(true)
                    .triggeredRules(triggeredRulesJson)
                    .build();

            fraudCheckRepository.save(fraudCheck);
            log.info("FraudCheck persisted for paymentId={} rules={}",
                    record.getPaymentId(), result.triggeredRules());

        } catch (Exception ex) {
            log.error("FraudService.checkAndPersist failed for paymentId={} — suppressing",
                    record.getPaymentId(), ex);
        }
    }

    @Transactional(readOnly = true)
    public List<FraudCheck> getFraudChecks(String paymentId) {
        return fraudCheckRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }

    private String serializeRules(List<String> rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (Exception ex) {
            log.warn("FraudService: failed to serialize triggered rules", ex);
            return "[]";
        }
    }
}