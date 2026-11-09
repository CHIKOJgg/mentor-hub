package com.mentorhub.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDayStatusRequest(
        @NotBlank String date,
        @NotBlank String status,
        String details
) {}
