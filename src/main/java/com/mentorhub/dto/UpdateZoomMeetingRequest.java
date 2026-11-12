package com.mentorhub.dto;

import java.time.Instant;

public record UpdateZoomMeetingRequest(
        String title,
        Integer callNumber,
        String zoomUrl,
        String recordingUrl,
        Instant scheduledAt,
        Instant linkSentAt,
        String evidenceUrl,
        Boolean isCompleted,
        String meetingNotes
) {}
