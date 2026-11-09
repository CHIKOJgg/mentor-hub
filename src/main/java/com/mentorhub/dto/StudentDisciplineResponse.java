package com.mentorhub.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentDisciplineResponse(
    String id,
    String fullName,
    String avatarText,
    String tgUsername,
    Long tgChatId,
    String niche,
    String tariff,
    java.time.LocalDate startDate,
    Boolean isActive,
    BigDecimal initialIncome,
    BigDecimal currentIncome,
    BigDecimal initialAvgTicket,
    BigDecimal currentAvgTicket,
    Integer totalReels,
    Integer totalCarousels,
    Integer totalStorytells,
    Integer totalMailings,
    Integer totalNewClients,
    Integer disciplineRate,
    Boolean isOnPause,
    String pauseReason,
    String nextZoomDate,
    Integer completedZoomsCount,
    List<DayInfoResponse> weekDays,
    List<ZoomMeetingDto> zoomSessions
) {}
