package com.example.razorpaywebhook.fraud;

import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.enums.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FraudRuleEngine {

    private static final long HIGH_AMOUNT_THRESHOLD = 100_000L;
    private static final int  REPEATED_FAILURE_MIN_RETRIES = 2;
    private static final long RAPID_STATE_CHANGE_SECONDS = 5L;

    public FraudResult evaluate(PaymentRecord record, String eventType) {
        List<String> triggeredRules = new ArrayList<>();

        checkHighAmount(record, triggeredRules);
        checkRepeatedFailure(record, triggeredRules);
        checkRapidStateChange(record, triggeredRules);

        boolean isFraud = !triggeredRules.isEmpty();
        if (isFraud) {
            log.info("Fraud rules triggered for paymentId={} rules={}",
                    record.getPaymentId(), triggeredRules);
        }

        return new FraudResult(isFraud, triggeredRules);
    }

    private void checkHighAmount(PaymentRecord record, List<String> triggered) {
        if (record.getAmount() > HIGH_AMOUNT_THRESHOLD) {
            triggered.add("HIGH_AMOUNT");
        }
    }

    private void checkRepeatedFailure(PaymentRecord record, List<String> triggered) {
        if (record.getStatus() == PaymentStatus.FAILED
                && record.getRetryCount() >= REPEATED_FAILURE_MIN_RETRIES) {
            triggered.add("REPEATED_FAILURE");
        }
    }

    private void checkRapidStateChange(PaymentRecord record, List<String> triggered) {
        if (record.getRetryCount() <= 0) return;
        if (record.getCreatedAt() == null || record.getUpdatedAt() == null) return;

        long diffSeconds = Duration.between(record.getCreatedAt(), record.getUpdatedAt())
                .toSeconds();
        if (diffSeconds < RAPID_STATE_CHANGE_SECONDS) {
            triggered.add("RAPID_STATE_CHANGE");
        }
    }
}