package com.mentorhub.dto;

import java.time.Instant;

public record ReportActionResponse(
    Boolean success,
    String reportId,
    String status,
    Boolean tgMessageDelivered,
    Boolean feedbackSent,
    Instant approvedAt,
    Instant rejectedAt
) {}
