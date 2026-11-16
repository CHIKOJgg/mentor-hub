package com.mentorhub.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateStudentRequest(
    String fullName,
    String avatarText,
    String tgUsername,
    Long tgChatId,
    String niche,
    String tariff,
    BigDecimal initialIncome,
    BigDecimal currentIncome,
    BigDecimal initialAvgTicket,
    BigDecimal currentAvgTicket,
    LocalDate startDate,
    Boolean isActive,
    Boolean isPaused,
    String pauseReason,
    String taskPlanRaw,
    Integer currentTaskDay,
    String currency,
    String timezone
) {
    public UpdateStudentRequest(
        String fullName,
        String avatarText,
        String tgUsername,
        Long tgChatId,
        String niche,
        String tariff,
        BigDecimal initialIncome,
        BigDecimal currentIncome,
        BigDecimal initialAvgTicket,
        BigDecimal currentAvgTicket,
        LocalDate startDate,
        Boolean isActive,
        Boolean isPaused,
        String pauseReason
    ) {
        this(fullName, avatarText, tgUsername, tgChatId, niche, tariff,
             initialIncome, currentIncome, initialAvgTicket, currentAvgTicket,
             startDate, isActive, isPaused, pauseReason, null, null, null, null);
    }

    public UpdateStudentRequest(
        String fullName,
        String avatarText,
        String tgUsername,
        Long tgChatId,
        String niche,
        String tariff,
        BigDecimal initialIncome,
        BigDecimal currentIncome,
        BigDecimal initialAvgTicket,
        BigDecimal currentAvgTicket,
        LocalDate startDate,
        Boolean isActive,
        Boolean isPaused,
        String pauseReason,
        String taskPlanRaw,
        Integer currentTaskDay
    ) {
        this(fullName, avatarText, tgUsername, tgChatId, niche, tariff,
             initialIncome, currentIncome, initialAvgTicket, currentAvgTicket,
             startDate, isActive, isPaused, pauseReason, taskPlanRaw, currentTaskDay, null, null);
    }
}
