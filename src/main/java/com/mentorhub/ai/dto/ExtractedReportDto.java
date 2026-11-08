package com.mentorhub.ai.dto;

import com.mentorhub.dto.ReflectionSummaryDto;

import java.math.BigDecimal;
import java.util.List;

public record ExtractedReportDto(
        List<String> reelUrls,
        BigDecimal salesAmount,
        Integer mailingsCount,
        Integer newClientsCount,
        String mentorQuestion,
        String emotionalStatus,      // 'NORMAL', 'BURNOUT_ALERT', 'STRESSED'
        String aiSummary,
        ReflectionSummaryDto reflectionSummary,
        String filteredFloodSnippet
) {}
