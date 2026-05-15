package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityIdAndEntityTypeOrderBySequenceNumAsc(
            String entityId, String entityType);

    Page<AuditLog> findByEntityIdAndEntityType(
            String entityId, String entityType, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE audit_log SET current_hash = :hash WHERE id = :id",
            nativeQuery = true)
    void updateCurrentHash(@Param("id") UUID id, @Param("hash") String hash);
}