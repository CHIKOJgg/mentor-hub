package com.mentorhub.dto;

import java.time.Instant;
import java.util.List;

public record ZoomTrackerResponse(
    Instant nextCallAt,
    Long completedCallsTotal,
    List<ZoomMeetingDto> upcomingMeetings
) {}
