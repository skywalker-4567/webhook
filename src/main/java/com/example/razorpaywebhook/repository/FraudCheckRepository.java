package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.FraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudCheckRepository extends JpaRepository<FraudCheck, UUID> {

    List<FraudCheck> findByPaymentIdOrderByCreatedAtDesc(String paymentId);
}