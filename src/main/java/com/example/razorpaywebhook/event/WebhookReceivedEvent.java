package com.example.razorpaywebhook.event;

import java.util.UUID;

public record WebhookReceivedEvent(UUID webhookEventId) {}