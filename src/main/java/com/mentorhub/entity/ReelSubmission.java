package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reel_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private DailyReport report;

    @Column(name = "reel_url", nullable = false, columnDefinition = "TEXT")
    private String reelUrl;

    @Column(name = "canonical_url", nullable = false, columnDefinition = "TEXT")
    private String canonicalUrl;

    @Column(name = "shortcode", nullable = false, length = 100)
    private String shortcode;

    @Column(name = "reel_hash", nullable = false, length = 64)
    private String reelHash;

    @Column(name = "is_valid", nullable = false)
    @Builder.Default
    private Boolean isValid = true;

    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private Boolean isDuplicate = false;

    @Column(name = "duplicate_date", length = 50)
    private String duplicateDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private ReelSubmission duplicateOf;

    @Column(name = "http_status_code")
    @Builder.Default
    private Integer httpStatusCode = 200;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @PrePersist
    public void prePersist() {
        if (checkedAt == null) {
            checkedAt = Instant.now();
        }
    }
}
