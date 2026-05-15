package com.example.razorpaywebhook.domain.entity;

import com.example.razorpaywebhook.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SettlementReport {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "total_payments", nullable = false)
    private long totalPayments;

    @Column(name = "total_refunds", nullable = false)
    private long totalRefunds;

    @Column(name = "net_amount", nullable = false)
    private long netAmount;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;
}