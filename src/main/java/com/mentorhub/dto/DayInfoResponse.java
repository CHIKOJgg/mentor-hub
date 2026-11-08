package com.mentorhub.dto;

public record DayInfoResponse(
    String day,
    String date,
    String status,
    String missedDetails
) {}
