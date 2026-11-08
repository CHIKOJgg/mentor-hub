package com.mentorhub.dto;

import java.math.BigDecimal;

public record StoryCaseResponse(
    String studentId,
    String fullName,
    String niche,
    BigDecimal initialIncome,
    BigDecimal currentIncome,
    String growthMultiplier,
    String growthPercent,
    BigDecimal initialAvgTicket,
    BigDecimal currentAvgTicket,
    Integer totalReels,
    Integer totalCarousels,
    Integer totalStorytells,
    Integer totalMailings,
    Integer totalNewClients,
    Integer disciplineRate,
    String generatedStoryCopy
) {}
