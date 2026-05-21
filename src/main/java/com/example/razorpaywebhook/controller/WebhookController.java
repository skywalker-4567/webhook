package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.WebhookEvent;
import com.example.razorpaywebhook.dto.PageResponse;
import com.example.razorpaywebhook.dto.WebhookEventDTO;
import com.example.razorpaywebhook.dto.WebhookStatsResponse;
import com.example.razorpaywebhook.enums.WebhookStatus;
import com.example.razorpaywebhook.ratelimit.RateLimiterService;
import com.example.razorpaywebhook.repository.WebhookEventRepository;
import com.example.razorpaywebhook.service.WebhookIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Razorpay webhook ingestion and event management")
public class WebhookController {

    private final WebhookIngestionService webhookIngestionService;
    private final WebhookEventRepository  webhookEventRepository;
    private final RateLimiterService      rateLimiterService;

    @PostMapping("/razorpay")
    @Operation(
            summary = "Ingest Razorpay webhook",
            description = "Receives and verifies a Razorpay webhook event via HMAC-SHA256 signature. " +
                    "No JWT required — signature validation is the auth mechanism.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid signature or malformed body"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, String>> receiveWebhook(
            HttpServletRequest request,
            @Parameter(description = "HMAC-SHA256 signature from Razorpay", required = true)
            @RequestHeader(value = "X-Razorpay-Signature", defaultValue = "") String signature) {

        String clientIp = request.getRemoteAddr();
        if (!rateLimiterService.isAllowed("webhook:" + clientIp, 100, 60_000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "RATE_LIMIT_EXCEEDED"));
        }

        byte[] rawBytes;
        String payload;
        try {
            rawBytes = request.getInputStream().readAllBytes();
            payload  = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Failed to read webhook request body", ex);
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST"));
        }

        webhookIngestionService.ingest(rawBytes, signature, payload);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @GetMapping("/events")
    @Operation(summary = "List webhook events", description = "Paginated list of webhook events with optional filters")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of webhook events"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public ResponseEntity<PageResponse<WebhookEventDTO>> getEvents(
            @RequestParam(required = false) String paymentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by("receivedAt").descending());

        Page<WebhookEvent> resultPage;
        if (paymentId != null && status != null) {
            resultPage = webhookEventRepository.findByPaymentIdAndStatus(
                    paymentId, WebhookStatus.valueOf(status.toUpperCase()), pageable);
        } else if (paymentId != null) {
            resultPage = webhookEventRepository.findByPaymentId(paymentId, pageable);
        } else if (status != null) {
            resultPage = webhookEventRepository.findByStatus(
                    WebhookStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            resultPage = webhookEventRepository.findAllBy(pageable);
        }

        List<WebhookEventDTO> dtos = resultPage.getContent().stream()
                .map(this::toWebhookEventDTO).toList();

        return ResponseEntity.ok(PageResponse.<WebhookEventDTO>builder()
                .items(dtos)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .currentPage(resultPage.getNumber())
                .build());
    }

    @GetMapping("/stats")
    @Operation(summary = "Webhook statistics", description = "Count of webhook events grouped by status")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Stats object")
    public ResponseEntity<WebhookStatsResponse> getStats() {
        long processed  = webhookEventRepository.countByStatus(WebhookStatus.PROCESSED);
        long failed     = webhookEventRepository.countByStatus(WebhookStatus.FAILED);
        long processing = webhookEventRepository.countByStatus(WebhookStatus.PROCESSING);
        long received   = webhookEventRepository.countByStatus(WebhookStatus.RECEIVED);
        long total      = processed + failed + processing + received;

        return ResponseEntity.ok(WebhookStatsResponse.builder()
                .total(total).processed(processed).failed(failed)
                .processing(processing).received(received).build());
    }

    private WebhookEventDTO toWebhookEventDTO(WebhookEvent e) {
        return WebhookEventDTO.builder()
                .eventId(e.getEventId())
                .eventType(e.getEventType())
                .paymentId(e.getPaymentId())
                .status(e.getStatus().name())
                .failureType(e.getFailureType() != null ? e.getFailureType().name() : null)
                .failureReason(e.getFailureReason())
                .receivedAt(e.getReceivedAt())
                .processedAt(e.getProcessedAt())
                .retryCount(e.getRetryCount())
                .correlationId(e.getCorrelationId())
                .build();
    }
}