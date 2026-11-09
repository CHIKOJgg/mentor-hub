package com.mentorhub.dto;

import java.time.LocalTime;

public record SystemSettingsDto(
        boolean morningPushEnabled,
        LocalTime morningPushTime,
        boolean eveningReminderEnabled,
        LocalTime eveningReminderTime,
        LocalTime dailyDeadlineTime,
        boolean strictDeadlineEnabled,
        boolean saturdayPollEnabled,
        LocalTime saturdayPollTime,
        boolean sundayRestEnabled,
        boolean aiWhisperEnabled,
        boolean aiSummaryEnabled,
        boolean antiFraudDedupEnabled,
        boolean zoomAutoDetectEnabled
) {}
