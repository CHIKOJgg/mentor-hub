package com.mentorhub.repository;

import com.mentorhub.entity.AuditMessageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditMessageLogRepository extends JpaRepository<AuditMessageLog, Long> {

    List<AuditMessageLog> findByTgUserIdOrderByCreatedAtDesc(Long tgUserId);

    List<AuditMessageLog> findByProcessingStatus(String processingStatus);
}
