package com.mentorhub.dto;

import java.math.BigDecimal;

public record AnalyticsSummaryResponse(
    BigDecimal totalEarned,
    String revenueGrowthFactor,
    Integer totalReels,
    Integer totalCarousels,
    Integer totalStorytells,
    Integer totalMailings,
    Integer totalNewClients,
    BigDecimal currentAvgTicket,
    BigDecimal initialAvgTicket,
    String avgTicketGrowthFactor,
    Double averageDisciplineRate,
    Long activeBurnoutAlertsCount,
    Long aiReflectionProcessedCount
) {}
