package com.mentorhub.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateStudentRequest(
    @NotBlank(message = "Full name is required")
    String fullName,
    String avatarText,
    String tgUsername,
    Long tgChatId,
    @NotBlank(message = "Niche is required")
    String niche,
    String tariff,
    BigDecimal initialIncome,
    BigDecimal initialAvgTicket,
    LocalDate startDate,
    String currency,
    String timezone
) {
    public CreateStudentRequest(
        String fullName,
        String avatarText,
        String tgUsername,
        Long tgChatId,
        String niche,
        String tariff,
        BigDecimal initialIncome,
        BigDecimal initialAvgTicket,
        LocalDate startDate
    ) {
        this(fullName, avatarText, tgUsername, tgChatId, niche, tariff, initialIncome, initialAvgTicket, startDate, "BYN", "Europe/Minsk");
    }
}
