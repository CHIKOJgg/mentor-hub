package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_text", nullable = false, length = 10)
    private String avatarText;

    @Column(name = "tg_username", length = 100)
    private String tgUsername;

    @Column(name = "tg_chat_id", unique = true)
    private Long tgChatId;

    @Column(name = "niche", nullable = false)
    private String niche;

    @Column(name = "initial_income", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal initialIncome = BigDecimal.ZERO;

    @Column(name = "current_income", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal currentIncome = BigDecimal.ZERO;

    @Column(name = "initial_avg_ticket", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal initialAvgTicket = BigDecimal.ZERO;

    @Column(name = "current_avg_ticket", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal currentAvgTicket = BigDecimal.ZERO;

    @Column(name = "tariff", nullable = false, length = 50)
    @Builder.Default
    private String tariff = "STANDARD";

    @Column(name = "start_date", nullable = false)
    @Builder.Default
    private LocalDate startDate = LocalDate.now();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_paused", nullable = false)
    @Builder.Default
    private Boolean isPaused = false;

    @Column(name = "pause_reason")
    private String pauseReason;

    @Column(name = "next_zoom_at")
    private Instant nextZoomAt;

    @Column(name = "completed_zooms_count")
    @Builder.Default
    private Integer completedZoomsCount = 0;

    @Column(name = "contract_zoom_quota", nullable = false)
    @Builder.Default
    private Integer contractZoomQuota = 8;

    @Column(name = "total_reels")
    @Builder.Default
    private Integer totalReels = 0;

    @Column(name = "total_carousels")
    @Builder.Default
    private Integer totalCarousels = 0;

    @Column(name = "total_storytells")
    @Builder.Default
    private Integer totalStorytells = 0;

    @Column(name = "total_mailings")
    @Builder.Default
    private Integer totalMailings = 0;

    @Column(name = "total_new_clients")
    @Builder.Default
    private Integer totalNewClients = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DailyReport> dailyReports = new ArrayList<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ZoomMeeting> zoomMeetings = new ArrayList<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
