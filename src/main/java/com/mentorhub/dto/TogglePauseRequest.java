package com.mentorhub.dto;

import jakarta.validation.constraints.NotNull;

public record TogglePauseRequest(
    @NotNull(message = "isPaused is required")
    Boolean isPaused,
    String pauseReason
) {}
