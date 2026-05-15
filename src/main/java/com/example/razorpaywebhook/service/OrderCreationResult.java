package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.domain.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCreationResult {
    private final Order order;
    private final boolean isNew;
}