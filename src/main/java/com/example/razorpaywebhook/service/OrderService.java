package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.client.RazorpayOrderClient;
import com.example.razorpaywebhook.client.RazorpayOrderResponse;
import com.example.razorpaywebhook.domain.entity.Order;
import com.example.razorpaywebhook.dto.CreateOrderRequest;
import com.example.razorpaywebhook.enums.OrderStatus;
import com.example.razorpaywebhook.exception.OrderNotFoundException;
import com.example.razorpaywebhook.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final RazorpayOrderClient razorpayOrderClient;
    private final ObjectMapper objectMapper;

    public OrderCreationResult createOrder(CreateOrderRequest req, String idempotencyKey) {
        log.info("Creating order for idempotency key: {}", idempotencyKey);

        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Idempotent repeat for key: {}", idempotencyKey);
                    return new OrderCreationResult(existing, false);
                })
                .orElseGet(() -> createNewOrder(req, idempotencyKey));
    }

    private OrderCreationResult createNewOrder(CreateOrderRequest req, String idempotencyKey) {
        RazorpayOrderResponse razorpayResponse = razorpayOrderClient.createOrder(req);

        Order order = Order.builder()
                .idempotencyKey(idempotencyKey)
                .razorpayOrderId(razorpayResponse.getId())
                .customerId(req.getCustomerId())
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .status(OrderStatus.CREATED)
                .description(req.getDescription())
                .metadata(serializeMetadata(req.getMetadata()))
                .build();

        try {
            Order saved = orderRepository.saveAndFlush(order);
            log.info("Order created: {} razorpayOrderId: {}", saved.getId(), saved.getRazorpayOrderId());
            return new OrderCreationResult(saved, true);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race condition on idempotency key: {} — fetching existing", idempotencyKey);
            Order existing = orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Race condition recovery failed for key: " + idempotencyKey));
            return new OrderCreationResult(existing, false);
        }
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            log.warn("Failed to serialize order metadata", ex);
            return null;
        }
    }
}