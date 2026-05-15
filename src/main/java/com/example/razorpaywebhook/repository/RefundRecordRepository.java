package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, UUID> {

    Optional<RefundRecord> findByIdempotencyKey(String key);

    List<RefundRecord> findByPaymentId(String paymentId);
}