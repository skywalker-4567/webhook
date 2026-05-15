package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.client.RazorpayRefundClient;
import com.example.razorpaywebhook.client.RazorpayRefundResponse;
import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.domain.entity.RefundRecord;
import com.example.razorpaywebhook.enums.PaymentStatus;
import com.example.razorpaywebhook.enums.RefundStatus;
import com.example.razorpaywebhook.exception.AmountExceedsPaymentException;
import com.example.razorpaywebhook.exception.GatewayUnavailableException;
import com.example.razorpaywebhook.exception.PaymentNotFoundException;
import com.example.razorpaywebhook.exception.RefundNotEligibleException;
import com.example.razorpaywebhook.repository.PaymentRecordRepository;
import com.example.razorpaywebhook.repository.RefundRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefundService {

    private final RefundRecordRepository refundRecordRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RazorpayRefundClient razorpayRefundClient;

    public RefundRecord initiateRefund(String paymentId, Long amount,
                                       String reason, String idempotencyKey) {
        // 1. Idempotency check
        return refundRecordRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Idempotent refund repeat for key: {}", idempotencyKey);
                    return existing;
                })
                .orElseGet(() -> createNewRefund(paymentId, amount, reason, idempotencyKey));
    }

    private RefundRecord createNewRefund(String paymentId, Long amount,
                                         String reason, String idempotencyKey) {
        // 2. Find payment
        PaymentRecord payment = paymentRecordRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // 3. Validate eligibility
        if (payment.getStatus() != PaymentStatus.CAPTURED
                && payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new RefundNotEligibleException(
                    "Payment " + paymentId + " is not eligible for refund. Status: "
                            + payment.getStatus());
        }

        // 4. Validate amount
        if (amount > payment.getAmount()) {
            throw new AmountExceedsPaymentException(
                    "Refund amount " + amount + " exceeds payment amount " + payment.getAmount());
        }

        // 5a. Persist before call
        RefundRecord refundRecord = RefundRecord.builder()
                .paymentId(paymentId)
                .idempotencyKey(idempotencyKey)
                .razorpayRefundId(null)
                .amount(amount)
                .reason(reason)
                .status(RefundStatus.INITIATED)
                .build();

        RefundRecord saved;
        try {
            saved = refundRecordRepository.saveAndFlush(refundRecord);
            log.info("RefundRecord persisted before gateway call: id={}", saved.getId());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race condition on refund idempotency key: {}", idempotencyKey);
            return refundRecordRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Race condition recovery failed for key: " + idempotencyKey));
        }

        // 5b. Call Razorpay
        try {
            RazorpayRefundResponse response =
                    razorpayRefundClient.initiateRefund(paymentId, amount, reason);

            saved.setRazorpayRefundId(response.getId());
            saved.setStatus(RefundStatus.INITIATED);
            refundRecordRepository.save(saved);
            log.info("Refund initiated: paymentId={} razorpayRefundId={}",
                    paymentId, response.getId());
            return saved;

        } catch (GatewayUnavailableException ex) {
            saved.setStatus(RefundStatus.FAILED);
            refundRecordRepository.save(saved);
            log.error("Refund gateway call failed for paymentId={}", paymentId, ex);
            throw ex;
        }
    }
}