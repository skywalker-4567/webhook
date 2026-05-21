package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.AuditLog;
import com.example.razorpaywebhook.dto.AuditLogDTO;
import com.example.razorpaywebhook.dto.AuditVerifyResponse;
import com.example.razorpaywebhook.dto.PageResponse;
import com.example.razorpaywebhook.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit", description = "SHA-256 hash-chained audit log — verify tamper-evidence")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/verify")
    @Operation(
            summary = "Verify audit hash chain",
            description = "Walks the entire hash chain for an entity and reports the exact sequence " +
                    "number where the chain breaks, if at all. VALID means tamper-free."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification result: VALID or BROKEN"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public ResponseEntity<AuditVerifyResponse> verify(
            @Parameter(description = "Entity type e.g. PAYMENT", required = true)
            @RequestParam String entityType,
            @Parameter(description = "Entity ID e.g. pay_abc123", required = true)
            @RequestParam String entityId) {
        return ResponseEntity.ok(auditService.verifyChain(entityType, entityId));
    }

    @GetMapping("/logs")
    @Operation(
            summary = "Get audit logs",
            description = "Returns paginated audit log entries for an entity, sorted by sequence number ascending"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated audit entries"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public ResponseEntity<PageResponse<AuditLogDTO>> getLogs(
            @Parameter(description = "Entity ID", required = true)
            @RequestParam String entityId,
            @Parameter(description = "Entity type e.g. PAYMENT", required = true)
            @RequestParam String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by("sequenceNum").ascending());
        Page<AuditLog> resultPage = auditService.getLogs(entityId, entityType, pageable);

        List<AuditLogDTO> dtos = resultPage.getContent().stream()
                .map(this::toDTO).toList();

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