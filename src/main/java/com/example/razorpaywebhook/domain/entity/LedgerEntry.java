package com.example.razorpaywebhook.domain.entity;

import com.example.razorpaywebhook.enums.EntryType;
import com.example.razorpaywebhook.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class LedgerEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry(String transactionRef, TransactionType transactionType,
                       EntryType entryType, UUID accountId, long amount,
                       String currency, String description, String paymentId,
                       UUID orderId, UUID transactionId, UUID correlationId) {
        this.transactionRef = transactionRef;
        this.transactionType = transactionType;
        this.entryType = entryType;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.correlationId = correlationId;
    }
}