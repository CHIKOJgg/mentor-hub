package com.mentorhub.dto;

import java.time.Instant;

public record ZoomSessionInput(
        Integer callNumber,
        String title,
        String zoomUrl,
        String recordingUrl,
        Instant scheduledAt,
        Instant linkSentAt,
        String evidenceUrl,
        Boolean isCompleted,
        String meetingNotes
) {}
