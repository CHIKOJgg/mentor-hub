package com.mentorhub.dto;

import java.time.Instant;

public record SyncTelegramResponse(
    Boolean success,
    String message,
    Instant syncedAt
) {}
