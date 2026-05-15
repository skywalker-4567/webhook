package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    Optional<PaymentRecord> findByPaymentId(String paymentId);

    Optional<PaymentRecord> findByInternalOrderId(UUID internalOrderId);

    Page<PaymentRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PaymentRecord> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    List<PaymentRecord> findByStatusInAndUpdatedAtBetween(List<PaymentStatus> statuses,
                                                          Instant from,
                                                          Instant to);
}