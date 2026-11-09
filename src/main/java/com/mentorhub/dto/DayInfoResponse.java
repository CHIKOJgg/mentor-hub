package com.mentorhub.dto;

public record DayInfoResponse(
    String day,
    String date,
    String status,
    String missedDetails,
    java.time.LocalDate isoDate
) {
    public DayInfoResponse(String day, String date, String status, String missedDetails) {
        this(day, date, status, missedDetails, null);
    }
}
