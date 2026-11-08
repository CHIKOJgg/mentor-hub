package com.mentorhub.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectReportRequest(
    @NotBlank(message = "Reason is required")
    String reason
) {}
