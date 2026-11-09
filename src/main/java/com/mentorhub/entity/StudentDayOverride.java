package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "student_day_overrides", uniqueConstraints =
        @UniqueConstraint(name = "uq_student_day_override", columnNames = {"student_id", "day_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDayOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onWrite() {
        updatedAt = Instant.now();
    }
}
