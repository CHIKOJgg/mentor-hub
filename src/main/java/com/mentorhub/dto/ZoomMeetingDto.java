package com.mentorhub.dto;

import java.time.Instant;

public record ZoomMeetingDto(
    Long id,
    Long studentId,
    String studentName,
    String title,
    Integer callNumber,
    String zoomUrl,
    String recordingUrl,
    Instant scheduledAt,
    Instant completedAt,
    Boolean isCompleted,
    String meetingNotes
) {
    public ZoomMeetingDto(Long id, Long studentId, String studentName, String zoomUrl,
                          Instant scheduledAt, Instant completedAt, Boolean isCompleted, String meetingNotes) {
        this(id, studentId, studentName, "Созвон", null, zoomUrl, null, scheduledAt, completedAt, isCompleted, meetingNotes);
    }
}
