package com.mentorhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_message_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "tg_chat_id")
    private Long tgChatId;

    @Column(name = "tg_user_id")
    private Long tgUserId;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType; // 'BUSINESS_1ON1', 'MINI_GROUP', 'DIRECT_BOT'

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processing_status", nullable = false, length = 50)
    private String processingStatus; // 'PROCESSED', 'UNMATCHED_USER', 'ERROR'

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
