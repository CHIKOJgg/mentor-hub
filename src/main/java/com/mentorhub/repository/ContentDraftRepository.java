package com.mentorhub.repository;

import com.mentorhub.entity.ContentDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentDraftRepository extends JpaRepository<ContentDraft, Long> {

    List<ContentDraft> findByReportId(Long reportId);

    List<ContentDraft> findByReportIdAndDraftType(Long reportId, String draftType);
}
