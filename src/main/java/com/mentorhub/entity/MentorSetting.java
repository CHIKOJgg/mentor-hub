package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "mentor_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_tg_id", nullable = false, unique = true)
    private Long mentorTgId;

    @Column(name = "mentor_name", nullable = false)
    @Builder.Default
    private String mentorName = "Ксения";

    @Column(name = "business_connection_id")
    private String businessConnectionId;

    @Column(name = "is_business_connected")
    @Builder.Default
    private Boolean isBusinessConnected = false;

    @Column(name = "reminder_cron", length = 50)
    @Builder.Default
    private String reminderCron = "0 0 20 * * MON-SAT";

    @Column(name = "deadline_time")
    @Builder.Default
    private LocalTime deadlineTime = LocalTime.of(21, 0, 0);

    @Column(name = "auto_reminders_enabled")
    @Builder.Default
    private Boolean autoRemindersEnabled = true;

    @Column(name = "morning_push_enabled", nullable = false)
    @Builder.Default
    private Boolean morningPushEnabled = true;

    @Column(name = "morning_push_time", nullable = false)
    @Builder.Default
    private LocalTime morningPushTime = LocalTime.of(9, 0);

    @Column(name = "evening_reminder_enabled", nullable = false)
    @Builder.Default
    private Boolean eveningReminderEnabled = true;

    @Column(name = "evening_reminder_time", nullable = false)
    @Builder.Default
    private LocalTime eveningReminderTime = LocalTime.of(20, 0);

    @Column(name = "strict_deadline_enabled", nullable = false)
    @Builder.Default
    private Boolean strictDeadlineEnabled = true;

    @Column(name = "saturday_poll_enabled", nullable = false)
    @Builder.Default
    private Boolean saturdayPollEnabled = true;

    @Column(name = "saturday_poll_time", nullable = false)
    @Builder.Default
    private LocalTime saturdayPollTime = LocalTime.of(18, 0);

    @Column(name = "sunday_rest_enabled", nullable = false)
    @Builder.Default
    private Boolean sundayRestEnabled = true;

    @Column(name = "ai_whisper_enabled", nullable = false)
    @Builder.Default
    private Boolean aiWhisperEnabled = true;

    @Column(name = "ai_summary_enabled", nullable = false)
    @Builder.Default
    private Boolean aiSummaryEnabled = true;

    @Column(name = "anti_fraud_dedup_enabled", nullable = false)
    @Builder.Default
    private Boolean antiFraudDedupEnabled = true;

    @Column(name = "zoom_auto_detect_enabled", nullable = false)
    @Builder.Default
    private Boolean zoomAutoDetectEnabled = true;

    @Column(name = "saturday_poll_cron", length = 50)
    @Builder.Default
    private String saturdayPollCron = "0 0 18 * * SAT";

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
