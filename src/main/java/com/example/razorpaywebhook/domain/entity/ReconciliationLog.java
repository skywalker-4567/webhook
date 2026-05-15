package com.example.razorpaywebhook.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_log")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReconciliationLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "internal_status", nullable = false)
    private String internalStatus;

    @Column(name = "gateway_status", nullable = false)
    private String gatewayStatus;

    @Column(name = "action_taken", nullable = false)
    private String actionTaken;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(name = "source")
    private String source;

    @Column(name = "reason")
    private String reason;

    @CreationTimestamp
    @Column(name = "reconciled_at", nullable = false, updatable = false)
    private Instant reconciledAt;
}