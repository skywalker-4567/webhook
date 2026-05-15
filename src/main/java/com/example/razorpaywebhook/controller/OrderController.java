package com.example.razorpaywebhook.controller;

import com.example.razorpaywebhook.domain.entity.Order;
import com.example.razorpaywebhook.dto.CreateOrderRequest;
import com.example.razorpaywebhook.dto.CreateOrderResponse;
import com.example.razorpaywebhook.dto.GetOrderResponse;
import com.example.razorpaywebhook.dto.PaymentSummary;
import com.example.razorpaywebhook.ratelimit.RateLimiterService;
import com.example.razorpaywebhook.repository.PaymentRecordRepository;
import com.example.razorpaywebhook.service.OrderCreationResult;
import com.example.razorpaywebhook.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RateLimiterService rateLimiterService;

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_REQUEST",
                            "message", "X-Idempotency-Key header is required"));
        }

        String clientIp = httpRequest.getRemoteAddr();
        if (!rateLimiterService.isAllowed("orders:" + clientIp, 20, 60_000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "RATE_LIMIT_EXCEEDED"));
        }

        OrderCreationResult result = orderService.createOrder(request, idempotencyKey);
        Order order = result.getOrder();

        CreateOrderResponse response = CreateOrderResponse.builder()
                .orderId(order.getId().toString())
                .razorpayOrderId(order.getRazorpayOrderId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus().name())
                .build();

        return result.isNew()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<GetOrderResponse> getOrder(@PathVariable UUID orderId) {
        Order order = orderService.getOrder(orderId);

        PaymentSummary paymentSummary = paymentRecordRepository
                .findByInternalOrderId(orderId)
                .map(p -> PaymentSummary.builder()
                        .paymentId(p.getPaymentId())
                        .method(p.getMethod())
                        .capturedAt(p.getCapturedAt())
                        .build())
                .orElse(null);

        GetOrderResponse response = GetOrderResponse.builder()
                .orderId(order.getId().toString())
                .razorpayOrderId(order.getRazorpayOrderId())
                .customerId(order.getCustomerId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus().name())
                .payment(paymentSummary)
                .createdAt(order.getCreatedAt())
                .build();

        return ResponseEntity.ok(response);
    }
}