package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "content_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private DailyReport report;

    @Column(name = "draft_type", nullable = false, length = 50)
    private String draftType; // 'REEL', 'CAROUSEL', 'STORYTELL'

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "file_tg_id")
    private String fileTgId;

    @Column(name = "is_duplicate")
    @Builder.Default
    private Boolean isDuplicate = false;

    @Column(name = "duplicate_date", length = 50)
    private String duplicateDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
