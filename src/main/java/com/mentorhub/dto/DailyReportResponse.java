package com.mentorhub.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportResponse(
    String id,
    String studentId,
    String studentName,
    String avatarText,
    String tgUsername,
    String tariff,
    String submittedAt,
    LocalDate reportDate,
    Boolean isOnTime,
    String status,
    String mentorReply,
    List<ContentDraftDto> contentDrafts,
    List<ReelDto> reels,
    BigDecimal salesAmount,
    Integer mailingsCount,
    Integer newClientsCount,
    String mentorQuestion,
    String voiceTranscript,
    ReflectionSummaryDto reflectionSummary,
    String aiSummary
) {}
