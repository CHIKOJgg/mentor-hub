package com.mentorhub.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleZoomRequest(
    @NotNull(message = "Student ID is required")
    Long studentId,
    String zoomUrl,
    @NotNull(message = "Scheduled time is required")
    Instant scheduledAt,
    String meetingNotes,
    String title,
    Integer callNumber,
    String recordingUrl
) {
    public ScheduleZoomRequest(Long studentId, String zoomUrl, Instant scheduledAt, String meetingNotes) {
        this(studentId, zoomUrl, scheduledAt, meetingNotes, null, null, null);
    }
}
