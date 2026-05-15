package com.example.razorpaywebhook.domain.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_checks")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FraudCheck {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    @JsonProperty("isFraud")
    @Column(name = "is_fraud", nullable = false)
    private boolean isFraud;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rules", nullable = false, columnDefinition = "jsonb")
    private String triggeredRules;

    @Column(name = "ml_fraud_score")
    private Double mlFraudScore;

    @Column(name = "ml_is_anomaly")
    private Boolean mlIsAnomaly;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}