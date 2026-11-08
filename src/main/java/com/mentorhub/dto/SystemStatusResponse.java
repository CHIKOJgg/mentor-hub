package com.mentorhub.dto;

public record SystemStatusResponse(
    Boolean botActive,
    String botUsername,
    String businessConnectionStatus,
    Boolean canReplyOnBehalf,
    Long activeStudentsCount,
    Long pendingReportsToday,
    String springBootVersion,
    Boolean virtualThreadsEnabled,
    String springAiStatus,
    ZoomTrackerSummaryDto zoomTracker
) {}
