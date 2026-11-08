package com.mentorhub.dto;

import java.time.Instant;

public record ZoomMeetingDto(
    Long id,
    Long studentId,
    String studentName,
    String zoomUrl,
    Instant scheduledAt,
    Instant completedAt,
    Boolean isCompleted,
    String meetingNotes
) {}
