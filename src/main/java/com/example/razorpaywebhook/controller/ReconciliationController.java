package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.ReconciliationLog;
import com.example.razorpaywebhook.dto.PageResponse;
import com.example.razorpaywebhook.dto.ReconciliationLogDTO;
import com.example.razorpaywebhook.repository.ReconciliationLogRepository;
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
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationLogRepository reconciliationLogRepository;

    @GetMapping
    public ResponseEntity<PageResponse<ReconciliationLogDTO>> getReconciliationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by("reconciledAt").descending());

        Page<ReconciliationLog> resultPage =
                reconciliationLogRepository.findAllByOrderByReconciledAtDesc(pageable);

        List<ReconciliationLogDTO> dtos = resultPage.getContent().stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(PageResponse.<ReconciliationLogDTO>builder()
                .items(dtos)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .currentPage(resultPage.getNumber())
                .build());
    }

    private ReconciliationLogDTO toDTO(ReconciliationLog r) {
        return ReconciliationLogDTO.builder()
                .id(r.getId())
                .paymentId(r.getPaymentId())
                .internalStatus(r.getInternalStatus())
                .gatewayStatus(r.getGatewayStatus())
                .actionTaken(r.getActionTaken())
                .skipReason(r.getSkipReason())
                .reason(r.getReason())
                .reconciledAt(r.getReconciledAt())
                .build();
    }
}