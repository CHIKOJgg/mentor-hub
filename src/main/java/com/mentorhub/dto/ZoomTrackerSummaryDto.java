package com.mentorhub.dto;

import java.time.Instant;

public record ZoomTrackerSummaryDto(
    Instant nextCallAt,
    Long completedCallsTotal
) {}
