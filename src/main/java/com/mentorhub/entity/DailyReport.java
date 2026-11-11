package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "daily_reports", uniqueConstraints = {
    @UniqueConstraint(name = "uq_student_report_date", columnNames = {"student_id", "report_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "is_on_time", nullable = false)
    @Builder.Default
    private Boolean isOnTime = true;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING"; // 'PENDING', 'APPROVED', 'REJECTED'

    @Column(name = "mailings_count")
    @Builder.Default
    private Integer mailingsCount = 0;

    @Column(name = "sales_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal salesTotal = BigDecimal.ZERO;

    @Column(name = "new_clients_count")
    @Builder.Default
    private Integer newClientsCount = 0;

    @Column(name = "mentor_question", columnDefinition = "TEXT")
    private String mentorQuestion;

    @Column(name = "mentor_reply", columnDefinition = "TEXT")
    private String mentorReply;

    @Column(name = "reply_sent_at")
    private Instant replySentAt;

    @Column(name = "voice_transcript", columnDefinition = "TEXT")
    private String voiceTranscript;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "reflection_emotions", columnDefinition = "TEXT")
    private String reflectionEmotions;

    @Column(name = "reflection_actions", columnDefinition = "TEXT")
    private String reflectionActions;

    @Column(name = "reflection_question", columnDefinition = "TEXT")
    private String reflectionQuestion;

    @Column(name = "emotional_status", length = 50)
    @Builder.Default
    private String emotionalStatus = "NORMAL"; // 'NORMAL', 'BURNOUT_ALERT', 'STRESSED'

    @Column(name = "last_tg_message_id")
    private Integer lastTgMessageId;

    @Column(name = "raw_message_text", columnDefinition = "TEXT")
    private String rawMessageText;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContentDraft> contentDrafts = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReelSubmission> reelSubmissions = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesEntry> salesEntries = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
