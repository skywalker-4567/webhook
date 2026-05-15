package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.LedgerRetryQueue;
import com.example.razorpaywebhook.enums.RetryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerRetryQueueRepository extends JpaRepository<LedgerRetryQueue, UUID> {

    List<LedgerRetryQueue> findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            RetryStatus status, Instant cutoff);
}