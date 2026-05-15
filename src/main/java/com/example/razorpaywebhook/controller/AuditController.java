package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.AuditLog;
import com.example.razorpaywebhook.dto.AuditLogDTO;
import com.example.razorpaywebhook.dto.AuditVerifyResponse;
import com.example.razorpaywebhook.dto.PageResponse;
import com.example.razorpaywebhook.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/verify")
    public ResponseEntity<AuditVerifyResponse> verify(
            @RequestParam String entityType,
            @RequestParam String entityId) {

        return ResponseEntity.ok(auditService.verifyChain(entityType, entityId));
    }

    @GetMapping("/logs")
    public ResponseEntity<PageResponse<AuditLogDTO>> getLogs(
            @RequestParam String entityId,
            @RequestParam String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by("sequenceNum").ascending());

        Page<AuditLog> resultPage = auditService.getLogs(entityId, entityType, pageable);

        List<AuditLogDTO> dtos = resultPage.getContent().stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(PageResponse.<AuditLogDTO>builder()
                .items(dtos)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .currentPage(resultPage.getNumber())
                .build());
    }

    private AuditLogDTO toDTO(AuditLog a) {
        return AuditLogDTO.builder()
                .id(a.getId())
                .sequenceNum(a.getSequenceNum())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .action(a.getAction())
                .actor(a.getActor().name())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .previousHash(a.getPreviousHash())
                .currentHash(a.getCurrentHash())
                .createdAt(a.getCreatedAt())
                .correlationId(a.getCorrelationId())
                .build();
    }
}