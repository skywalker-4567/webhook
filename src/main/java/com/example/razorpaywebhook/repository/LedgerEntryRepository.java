package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.LedgerEntry;
import com.example.razorpaywebhook.enums.EntryType;
import com.example.razorpaywebhook.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByTransactionRefAndEntryType(String transactionRef, EntryType entryType);

    List<LedgerEntry> findAllByPaymentIdOrderByCreatedAtAsc(String paymentId);

    List<LedgerEntry> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<LedgerEntry> findAllByTransactionId(UUID transactionId);

    Page<LedgerEntry> findByPaymentId(String paymentId, Pageable pageable);

    Page<LedgerEntry> findByOrderId(UUID orderId, Pageable pageable);

    Page<LedgerEntry> findByTransactionId(UUID transactionId, Pageable pageable);

    List<LedgerEntry> findAllByAccountIdAndCreatedAtBefore(UUID accountId, Instant before);

    Page<LedgerEntry> findByAccountIdAndCreatedAtBetween(UUID accountId,
                                                         Instant from,
                                                         Instant to,
                                                         Pageable pageable);

    List<LedgerEntry> findByCreatedAtBetweenAndTransactionTypeIn(Instant from,
                                                                 Instant to,
                                                                 List<TransactionType> types);
}