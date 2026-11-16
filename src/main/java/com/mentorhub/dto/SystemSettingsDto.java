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
        boolean zoomAutoDetectEnabled,
        boolean reportAckEnabled
) {
    public SystemSettingsDto(
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
    ) {
        this(
                morningPushEnabled,
                morningPushTime,
                eveningReminderEnabled,
                eveningReminderTime,
                dailyDeadlineTime,
                strictDeadlineEnabled,
                saturdayPollEnabled,
                saturdayPollTime,
                sundayRestEnabled,
                aiWhisperEnabled,
                aiSummaryEnabled,
                antiFraudDedupEnabled,
                zoomAutoDetectEnabled,
                false
        );
    }
}
