package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.ReconciliationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, UUID> {

    Page<ReconciliationLog> findAllByOrderByReconciledAtDesc(Pageable pageable);
}