package com.mentorhub.repository;

import com.mentorhub.entity.ReelSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReelSubmissionRepository extends JpaRepository<ReelSubmission, Long> {

    Optional<ReelSubmission> findFirstByReelHashOrderByCheckedAtAsc(String reelHash);

    Optional<ReelSubmission> findByReelHash(String reelHash);

    Optional<ReelSubmission> findByShortcode(String shortcode);

    List<ReelSubmission> findByReportId(Long reportId);
}
