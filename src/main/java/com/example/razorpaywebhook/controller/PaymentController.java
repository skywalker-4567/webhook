package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.PaymentRecord;
import com.example.razorpaywebhook.domain.entity.RefundRecord;
import com.example.razorpaywebhook.dto.PageResponse;
import com.example.razorpaywebhook.dto.PaymentDTO;
import com.example.razorpaywebhook.dto.RefundRequest;
import com.example.razorpaywebhook.dto.RefundResponse;
import com.example.razorpaywebhook.enums.PaymentStatus;
import com.example.razorpaywebhook.exception.PaymentNotFoundException;
import com.example.razorpaywebhook.repository.PaymentRecordRepository;
import com.example.razorpaywebhook.service.RefundService;
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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payments", description = "Payment record queries and refund initiation")
public class PaymentController {

    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundService           refundService;

    @GetMapping
    @Operation(summary = "List payments", description = "Paginated list of payment records, optionally filtered by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of payments"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public ResponseEntity<PageResponse<PaymentDTO>> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filter by status: AUTHORIZED, CAPTURED, FAILED, REFUNDED")
            @RequestParam(required = false) String status) {

        int clampedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, clampedSize,
                Sort.by("createdAt").descending());

        Page<PaymentRecord> resultPage = status != null
                ? paymentRecordRepository.findByStatusOrderByCreatedAtDesc(
                PaymentStatus.valueOf(status.toUpperCase()), pageable)
                : paymentRecordRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<PaymentDTO> dtos = resultPage.getContent().stream()
                .map(this::toPaymentDTO).toList();

        return ResponseEntity.ok(PageResponse.<PaymentDTO>builder()
                .items(dtos)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .currentPage(resultPage.getNumber())
                .build());
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment record"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<PaymentDTO> getPayment(
            @Parameter(description = "Razorpay payment ID e.g. pay_abc123")
            @PathVariable String paymentId) {
        PaymentRecord record = paymentRecordRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return ResponseEntity.ok(toPaymentDTO(record));
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(
            summary = "Initiate refund",
            description = "Initiates a refund for a captured payment. Idempotent via X-Idempotency-Key header."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund initiated"),
            @ApiResponse(responseCode = "400", description = "Not eligible or amount exceeds payment"),
            @ApiResponse(responseCode = "502", description = "Razorpay gateway error")
    })
    public ResponseEntity<?> refund(
            @PathVariable String paymentId,
            @RequestBody RefundRequest request,
            @Parameter(description = "Unique key for idempotent refund", required = true)
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_REQUEST",
                            "message", "X-Idempotency-Key header is required"));
        }

        RefundRecord refund = refundService.initiateRefund(
                paymentId, request.getAmount(), request.getReason(), idempotencyKey);

        return ResponseEntity.ok(RefundResponse.builder()
                .refundId(refund.getId().toString())
                .paymentId(refund.getPaymentId())
                .amount(refund.getAmount())
                .status(refund.getStatus().name())
                .build());
    }

    private PaymentDTO toPaymentDTO(PaymentRecord p) {
        return PaymentDTO.builder()
                .paymentId(p.getPaymentId())
                .orderId(p.getOrderId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus().name())
                .method(p.getMethod())
                .capturedAt(p.getCapturedAt())
                .retryCount(p.getRetryCount())
                .correlationId(p.getCorrelationId())
                .build();
    }
}