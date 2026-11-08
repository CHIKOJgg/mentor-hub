package com.mentorhub.dto;

import java.time.Instant;

public record TogglePauseResponse(
    String studentId,
    String fullName,
    Boolean isOnPause,
    String pauseReason,
    Boolean streakFrozen,
    Instant updatedAt
) {}
