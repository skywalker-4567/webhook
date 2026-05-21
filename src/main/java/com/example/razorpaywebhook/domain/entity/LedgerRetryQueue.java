package com.example.razorpaywebhook.domain.entity;

import com.example.razorpaywebhook.enums.RetryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_retry_queue")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LedgerRetryQueue {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private String eventPayload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_failure_reason")
    private String lastFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RetryStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
