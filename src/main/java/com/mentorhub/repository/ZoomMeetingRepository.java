package com.mentorhub.repository;

import com.mentorhub.entity.ZoomMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomMeetingRepository extends JpaRepository<ZoomMeeting, Long> {

    List<ZoomMeeting> findByStudentIdOrderByScheduledAtDesc(Long studentId);

    Optional<ZoomMeeting> findFirstByStudentIdAndIsCompletedFalseOrderByScheduledAtAsc(Long studentId);

    Optional<ZoomMeeting> findFirstByIsCompletedFalseOrderByScheduledAtAsc();

    long countByIsCompletedTrue();

    long countByStudentIdAndIsCompletedTrue(Long studentId);
}
