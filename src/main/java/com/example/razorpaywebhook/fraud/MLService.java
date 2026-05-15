package com.example.razorpaywebhook.fraud;

import com.example.razorpaywebhook.domain.entity.FraudCheck;
import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.repository.FraudCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MLService {

    private static final long AMOUNT_THRESHOLD = 50_000L;

    private final MLClient mlClient;
    private final FraudCheckRepository fraudCheckRepository;

    @Async("mlExecutor")
    @Transactional
    public void scoreAsync(PaymentRecord record) {
        try {
            // Guard: skip low-risk records
            if (record.getAmount() <= AMOUNT_THRESHOLD && record.getRetryCount() == 0) {
                return;
            }

            double timeDiffSeconds = 0.0;
            if (record.getCreatedAt() != null && record.getUpdatedAt() != null) {
                timeDiffSeconds = Duration.between(record.getCreatedAt(), record.getUpdatedAt())
                        .toSeconds();
            }

            MLScoreRequest request = new MLScoreRequest(
                    (double) record.getAmount(),
                    record.getRetryCount(),
                    timeDiffSeconds,
                    record.getStatus().name()
            );

            Optional<MLScoreResponse> result = mlClient.score(request);

            if (result.isEmpty()) {
                return;
            }

            MLScoreResponse response = result.get();
            log.info("MLService: scored paymentId={} fraudScore={} isAnomaly={}",
                    record.getPaymentId(), response.fraudScore(), response.isAnomaly());

            if (!response.isAnomaly()) {
                return;
            }

            // Update existing FraudCheck or create a new one
            List<FraudCheck> existing = fraudCheckRepository
                    .findByPaymentIdOrderByCreatedAtDesc(record.getPaymentId());

            if (!existing.isEmpty()) {
                FraudCheck latest = existing.get(0);
                latest.setMlFraudScore(response.fraudScore());
                latest.setMlIsAnomaly(response.isAnomaly());
                fraudCheckRepository.save(latest);
                log.info("MLService: updated existing FraudCheck id={} for paymentId={}",
                        latest.getId(), record.getPaymentId());
            } else {
                FraudCheck newCheck = FraudCheck.builder()
                        .paymentId(record.getPaymentId())
                        .eventType("ML_ANOMALY")
                        .paymentStatus(record.getStatus().name())
                        .isFraud(true)
                        .triggeredRules("[\"ML_ANOMALY\"]")
                        .mlFraudScore(response.fraudScore())
                        .mlIsAnomaly(response.isAnomaly())
                        .build();
                fraudCheckRepository.save(newCheck);
                log.info("MLService: created new FraudCheck for ML anomaly paymentId={}",
                        record.getPaymentId());
            }

        } catch (Exception ex) {
            log.error("MLService.scoreAsync failed for paymentId={} — suppressing",
                    record.getPaymentId(), ex);
        }
    }
}