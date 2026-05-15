package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.enums.WebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByEventId(String eventId);

    List<WebhookEvent> findByPaymentIdOrderByReceivedAtAsc(String paymentId);

    List<WebhookEvent> findByStatusAndNextRetryAtBefore(WebhookStatus status, Instant cutoff);

    List<WebhookEvent> findByStatusAndUpdatedAtBefore(WebhookStatus status, Instant cutoff);

    long countByStatus(WebhookStatus status);

    Page<WebhookEvent> findByPaymentIdAndStatusAndEventType(
            String paymentId, WebhookStatus status, String eventType, Pageable pageable);

    Page<WebhookEvent> findAllBy(Pageable pageable);

    Page<WebhookEvent> findByPaymentId(String paymentId, Pageable pageable);

    Page<WebhookEvent> findByStatus(WebhookStatus status, Pageable pageable);

    Page<WebhookEvent> findByPaymentIdAndStatus(
            String paymentId, WebhookStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE WebhookEvent w SET w.status = :processing " +
            "WHERE w.id = :id AND w.status = :received")
    int acquireProcessingLock(
            @Param("id") UUID id,
            @Param("processing") WebhookStatus processing,
            @Param("received") WebhookStatus received);
}