package com.mentorhub.dto;

import java.time.Instant;

public record TelegramMessageDeliveryResponse(
        boolean delivered,
        String message,
        Instant attemptedAt
) {}
