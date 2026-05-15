package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.distributed.DistributedLockService;
import com.example.razorpaywebhook.domain.entity.AuditLog;
import com.example.razorpaywebhook.dto.AuditVerifyResponse;
import com.example.razorpaywebhook.dto.AuditVerifyResponse.VerifyResult;
import com.example.razorpaywebhook.enums.AuditActor;
import com.example.razorpaywebhook.event.PaymentCapturedEvent;
import com.example.razorpaywebhook.event.PaymentFailedEvent;
import com.example.razorpaywebhook.event.PaymentRefundedEvent;
import com.example.razorpaywebhook.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String   LOCK_KEY    = "audit:chain:lock";
    private static final String   GENESIS     = "GENESIS";
    private static final Duration LOCK_TTL    = Duration.ofSeconds(5);
    private static final String   INSTANCE_ID = UUID.randomUUID().toString();

    private final AuditLogRepository     auditLogRepository;
    private final DistributedLockService distributedLockService;
    private final EntityManager          entityManager;

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId().toString());
        try {
            record("PAYMENT", event.paymentId(), "CAPTURED", AuditActor.WEBHOOK,
                    null, "{\"status\":\"CAPTURED\"}", event.correlationId());
        } finally { MDC.remove("correlationId"); }
    }

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId().toString());
        try {
            record("PAYMENT", event.paymentId(), "FAILED", AuditActor.WEBHOOK,
                    null, "{\"status\":\"FAILED\"}", event.correlationId());
        } finally { MDC.remove("correlationId"); }
    }

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId().toString());
        try {
            record("PAYMENT", event.paymentId(), "REFUNDED", AuditActor.WEBHOOK,
                    null, "{\"status\":\"REFUNDED\"}", event.correlationId());
        } finally { MDC.remove("correlationId"); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, String entityId, String action,
                       AuditActor actor, String oldValue, String newValue,
                       UUID correlationId) {
        boolean lockAcquired = acquireLock();
        try {
            List<AuditLog> existing = auditLogRepository
                    .findByEntityIdAndEntityTypeOrderBySequenceNumAsc(entityId, entityType);
            String previousHash = existing.isEmpty()
                    ? GENESIS
                    : existing.get(existing.size() - 1).getCurrentHash();

            AuditLog entry = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .actor(actor)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .previousHash(previousHash)
                    .currentHash("PENDING")
                    .correlationId(correlationId)
                    .build();

            AuditLog saved;
            try {
                saved = auditLogRepository.saveAndFlush(entry);
                // Force Hibernate to refresh from DB to get BIGSERIAL sequenceNum
                entityManager.refresh(saved);
            } catch (Exception ex) {
                log.error("Audit INSERT failed for entityId={} action={}", entityId, action, ex);
                return;
            }

            try {
                Long seqNum = saved.getSequenceNum();
                String realHash = computeHash(seqNum, entityType, entityId, action, previousHash);
                auditLogRepository.updateCurrentHash(saved.getId(), realHash);
                log.info("Audit recorded: entityType={} entityId={} action={} seq={}",
                        entityType, entityId, action, seqNum);
            } catch (Exception ex) {
                log.error("Audit hash update failed for id={}", saved.getId(), ex);
            }
        } catch (Exception ex) {
            log.error("Audit record failure: entityType={} entityId={} action={}", entityType, entityId, action, ex);
        } finally {
            if (lockAcquired) releaseLock();
        }
    }

    @Transactional(readOnly = true)
    public AuditVerifyResponse verifyChain(String entityType, String entityId) {
        List<AuditLog> logs = auditLogRepository
                .findByEntityIdAndEntityTypeOrderBySequenceNumAsc(entityId, entityType);

        if (logs.isEmpty()) {
            return AuditVerifyResponse.builder()
                    .result(VerifyResult.VALID).brokenAtSequence(null)
                    .message("No audit entries found").build();
        }

        String expectedPreviousHash = GENESIS;
        for (AuditLog entry : logs) {
            if (!expectedPreviousHash.equals(entry.getPreviousHash())) {
                return AuditVerifyResponse.builder()
                        .result(VerifyResult.BROKEN)
                        .brokenAtSequence(entry.getSequenceNum().intValue())
                        .message("Hash chain broken at sequence " + entry.getSequenceNum()).build();
            }
            String expectedHash = computeHash(
                    entry.getSequenceNum(), entry.getEntityType(), entry.getEntityId(),
                    entry.getAction(), entry.getPreviousHash());
            log.info("AUDIT_VERIFY seq={} expected={} stored={}",
                    entry.getSequenceNum(), expectedHash, entry.getCurrentHash());
            if (!expectedHash.equals(entry.getCurrentHash())) {
                return AuditVerifyResponse.builder()
                        .result(VerifyResult.BROKEN)
                        .brokenAtSequence(entry.getSequenceNum().intValue())
                        .message("Hash mismatch at sequence " + entry.getSequenceNum()).build();
            }
            expectedPreviousHash = entry.getCurrentHash();
        }

        return AuditVerifyResponse.builder()
                .result(VerifyResult.VALID).brokenAtSequence(null)
                .message("Chain verified: " + logs.size() + " entries valid").build();
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(String entityId, String entityType, Pageable pageable) {
        return auditLogRepository.findByEntityIdAndEntityType(entityId, entityType, pageable);
    }

    private String computeHash(Long sequenceNum, String entityType, String entityId,
                               String action, String previousHash) {
        String input = sequenceNum + entityType + entityId + action + previousHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 not available", ex);
        }
    }

    private boolean acquireLock() {
        try {
            return distributedLockService.tryLock(LOCK_KEY, INSTANCE_ID, LOCK_TTL.toMillis());
        } catch (Exception ex) {
            log.warn("Audit lock unavailable - proceeding without lock", ex);
            return false;
        }
    }

    private void releaseLock() {
        try {
            distributedLockService.releaseLock(LOCK_KEY, INSTANCE_ID);
        } catch (Exception ex) {
            log.warn("Failed to release audit lock", ex);
        }
    }
}